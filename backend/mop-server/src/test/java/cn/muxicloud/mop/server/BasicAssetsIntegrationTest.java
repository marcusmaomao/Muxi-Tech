package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BasicAssetsIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("cgr.dev/chainguard/minio:latest")
            .withEnv("MINIO_ROOT_USER", "mopminio")
            .withEnv("MINIO_ROOT_PASSWORD", "mop-minio-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("mop.jwt.secret", () -> "integration-test-jwt-secret-32bytes");
        registry.add("mop.demo.seed", () -> "true");
        registry.add("mop.demo.admin-username", () -> "admin");
        registry.add("mop.demo.admin-password", () -> "demo-admin");
        registry.add("mop.captcha.expose", () -> "true");
        registry.add("mop.datasource.secret", () -> "integration-datasource-secret");
        registry.add("mop.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("mop.storage.access-key", () -> "mopminio");
        registry.add("mop.storage.secret-key", () -> "mop-minio-secret");
        registry.add("mop.storage.bucket", () -> "mop-test");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void projectDatasourceAndFileStayInsideTheTenant() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS asset_probe (id INT PRIMARY KEY, title VARCHAR(40))");
        }
        String admin = token("admin", "demo-admin");
        ObjectNode project = objectMapper.createObjectNode();
        project.put("code", "grid");
        project.put("name", "电网");
        project.put("namespace", "https://muxi.example/ont/grid#");
        project.put("domain", "电力");
        project.put("scopeInclude", "设备");
        project.put("scopeExclude", "财务");
        project.put("description", "主网");
        MvcResult created = mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(project.toString()))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertThat(mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains("电力");

        project.put("namespace", "grid");
        mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(project.toString()))
                .andExpect(status().isBadRequest());

        ObjectNode update = project.deepCopy();
        update.put("namespace", "https://muxi.example/ont/grid#");
        update.put("code", "ignored");
        update.put("domain", "输变电");
        mvc.perform(put("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/projects/" + projectId + "/members").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"members\":[{\"userId\":1,\"role\":\"EDITOR\"}]}"))
                .andExpect(status().isOk());

        ObjectNode datasource = objectMapper.createObjectNode();
        datasource.put("name", "pg-probe");
        datasource.put("dbType", "POSTGRESQL");
        datasource.put("host", POSTGRES.getHost());
        datasource.put("port", POSTGRES.getMappedPort(5432));
        datasource.put("databaseName", POSTGRES.getDatabaseName());
        datasource.put("username", POSTGRES.getUsername());
        datasource.put("password", "pg-secret-value");
        datasource.put("projectId", projectId);
        MvcResult saved = mvc.perform(post("/api/v1/datasources").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(datasource.toString()))
                .andExpect(status().isOk()).andReturn();
        String savedBody = saved.getResponse().getContentAsString();
        assertThat(savedBody).doesNotContain("pg-secret-value");
        long datasourceId = objectMapper.readTree(savedBody).path("data").path("id").asLong();

        datasource.put("password", POSTGRES.getPassword());
        mvc.perform(put("/api/v1/datasources/" + datasourceId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(datasource.toString()))
                .andExpect(status().isOk());
        MvcResult probed = mvc.perform(post("/api/v1/datasources/" + datasourceId + "/test").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode probe = objectMapper.readTree(probed.getResponse().getContentAsString()).path("data");
        assertThat(probe.path("ok").asBoolean()).isTrue();
        assertThat(probe.path("elapsedMs").asLong()).isLessThan(5000);

        String tables = mvc.perform(get("/api/v1/datasources/" + datasourceId + "/tables").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(tables).contains("asset_probe");
        String columns = mvc.perform(get("/api/v1/datasources/" + datasourceId + "/tables/asset_probe/columns")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(columns).contains("title");

        ObjectNode dameng = objectMapper.createObjectNode();
        dameng.put("name", "dm-closed");
        dameng.put("dbType", "DAMENG");
        dameng.put("host", "127.0.0.1");
        dameng.put("port", 1);
        dameng.put("databaseName", "SYSDBA");
        dameng.put("username", "SYSDBA");
        dameng.put("password", "secret");
        MvcResult dm = mvc.perform(post("/api/v1/datasources").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(dameng.toString()))
                .andExpect(status().isOk()).andReturn();
        long dmId = objectMapper.readTree(dm.getResponse().getContentAsString()).path("data").path("id").asLong();
        String dmProbe = mvc.perform(post("/api/v1/datasources/" + dmId + "/test").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dmProbe).contains("\"ok\":false");
        assertThat(dmProbe).doesNotContain("classpath");

        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "变压器检修记录".getBytes(StandardCharsets.UTF_8));
        MvcResult uploaded = mvc.perform(multipart("/api/v1/files").file(file).param("category", "规程").param("projectId", Long.toString(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        long fileId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();
        String text = mvc.perform(get("/api/v1/files/" + fileId + "/text").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(text).contains("变压器检修记录");
        mvc.perform(get("/api/v1/files/" + fileId + "/preview").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"other\",\"name\":\"另一租户\",\"adminUsername\":\"other-admin\",\"adminPassword\":\"demo-admin\",\"adminDisplayName\":\"另一管理员\"}"))
                .andExpect(status().isOk());
        String other = token("other-admin", "demo-admin");
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/" + fileId + "/text").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/datasources").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("pg-probe"));
    }

    @Test
    void clerkWithoutPermissionCannotCreateProject() throws Exception {
        String admin = token("admin", "demo-admin");
        mvc.perform(post("/api/v1/system/users").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clerk\",\"password\":\"demo-admin\",\"displayName\":\"文员\"}"))
                .andExpect(status().isOk());
        String clerk = token("clerk", "demo-admin");
        mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + clerk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"nope\",\"name\":\"拒绝\",\"namespace\":\"https://muxi.example/ont/nope#\"}"))
                .andExpect(status().isForbidden());
    }

    private String token(String username, String password) throws Exception {
        MvcResult captcha = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(captcha.getResponse().getContentAsString()).path("data");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", username);
        payload.put("password", password);
        payload.put("captchaId", body.path("captchaId").asText());
        payload.put("captchaCode", body.path("debugCode").asText());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
