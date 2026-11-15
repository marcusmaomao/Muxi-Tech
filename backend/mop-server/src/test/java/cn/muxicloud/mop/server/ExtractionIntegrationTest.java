package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExtractionIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("cgr.dev/chainguard/minio:latest")
            .withEnv("MINIO_ROOT_USER", "mopminio")
            .withEnv("MINIO_ROOT_PASSWORD", "mop-minio-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> MOCK = new GenericContainer<>("wiremock/wiremock:3.9.1")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("chat-completions.json")), "/home/wiremock/mappings/chat-completions.json")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("extract-valid.json")), "/home/wiremock/mappings/extract-valid.json")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("extract-invalid.json")), "/home/wiremock/mappings/extract-invalid.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", NEO4J::getAdminPassword);
        registry.add("mop.jwt.secret", () -> "integration-test-jwt-secret-32bytes");
        registry.add("mop.demo.seed", () -> "true");
        registry.add("mop.demo.admin-username", () -> "admin");
        registry.add("mop.demo.admin-password", () -> "demo-admin");
        registry.add("mop.captcha.expose", () -> "true");
        registry.add("mop.datasource.secret", () -> "integration-datasource-secret");
        registry.add("mop.ai.key-secret", () -> "integration-ai-key-secret");
        registry.add("mop.ai.rate-limit", () -> "memory");
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
    void reviewPublishesOnlyValidExtractionsInsideTheTenant() throws Exception {
        String token = token("admin", "demo-admin");
        long branchId = branch(token, "extract-grid");
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些设备").put("apiName", "whichDevices")));
        String equipment = object(token, root, "变压器", "Transformer", cq);
        String station = object(token, root, "变电站", "Substation", cq);
        property(token, root, "变压器名", "transformerName", equipment);
        property(token, root, "站名", "stationName", station);
        link(token, root, equipment, station, cq);
        long modelId = model(token);
        long fileId = upload(token, "设备说明.txt", "一号变压器安装在城东站。");
        long jobId = extract(token, branchId, fileId, modelId);
        JsonNode items = items(token, jobId);
        assertThat(items.toString()).contains("一号变压器安装在城东站").contains("REVIEW");
        for (JsonNode item : items) {
            if ("REVIEW".equals(item.path("state").asText())) {
                mvc.perform(post("/api/v1/ai/extraction-items/" + item.path("id").asLong() + "/accept").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
        JsonNode published = objectMapper.readTree(mvc.perform(post("/api/v1/ai/extraction-jobs/" + jobId + "/publish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(published.path("inserted").asInt()).isEqualTo(2);
        assertThat(published.path("linked").asInt()).isEqualTo(1);
        JsonNode neighbors = objectMapper.readTree(mvc.perform(get("/api/v1/graph/neighbors").param("type", "Transformer").param("pk", "一号变压器").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(neighbors.toString()).contains("城东站").contains("一号变压器安装在城东站");

        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"extract-other\",\"name\":\"其他\",\"adminUsername\":\"extract-other\",\"adminPassword\":\"other-pass-1\",\"adminDisplayName\":\"其他\"}"))
                .andExpect(status().isOk());
        String other = token("extract-other", "other-pass-1");
        assertThat(objectMapper.readTree(mvc.perform(get("/api/v1/graph/objects").param("type", "Transformer").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data")).isEmpty();
    }

    @Test
    void illegalModelOutputGoesToQuarantine() throws Exception {
        String token = token("admin", "demo-admin");
        long branchId = branch(token, "extract-bad");
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些设备").put("apiName", "whichDevices")));
        object(token, root, "设备", "Equipment", cq);
        long modelId = model(token);
        long fileId = upload(token, "坏抽取.txt", "INVALID-EXTRACT 无法对应本体。");
        long jobId = extract(token, branchId, fileId, modelId);
        JsonNode items = items(token, jobId);
        assertThat(items.toString()).contains("QUARANTINE").contains("INVALID-EXTRACT");
        assertThat(items.toString()).doesNotContain("PUBLISHED");
        mvc.perform(post("/api/v1/ai/extraction-jobs/" + jobId + "/publish").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private long extract(String token, long branchId, long fileId, long modelId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", 0);
        body.put("branchId", branchId);
        body.put("fileId", fileId);
        body.put("modelId", modelId);
        return id(postJson(token, "/api/v1/ai/extraction-jobs", body));
    }

    private JsonNode items(String token, long jobId) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/v1/ai/extraction-jobs/" + jobId + "/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private long model(String token) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("displayName", "抽取回放");
        body.put("modelName", "extract-" + System.nanoTime());
        body.put("vendor", "LOCAL");
        body.put("baseUrl", "http://" + MOCK.getHost() + ":" + MOCK.getMappedPort(8080) + "/v1");
        body.put("apiKey", "sk-test");
        body.put("rpmLimit", 100);
        return id(postJson(token, "/api/v1/ai/models", body));
    }

    private long upload(String token, String name, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mvc.perform(multipart("/api/v1/files").file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long branch(String token, String code) throws Exception {
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode().put("code", code).put("name", code).put("namespace", "https://example.com/ont/" + code + "#")));
        MvcResult branches = mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(branches.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
    }

    private String object(String token, String root, String name, String api, String cqUid) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api);
        body.putArray("cqUids").add(cqUid);
        return uid(postJson(token, root + "/object-types", body));
    }

    private void property(String token, String root, String name, String api, String owner) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api).put("ownerUid", owner);
        body.putObject("valueType").put("kind", "BASIC").put("basic", "STRING");
        postJson(token, root + "/properties", body);
    }

    private void link(String token, String root, String equipment, String station, String cqUid) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", "安装于").put("apiName", "installedAt").put("sourceUid", equipment).put("targetUid", station).put("cardinality", "MANY_TO_ONE").put("invertible", false);
        body.putArray("cqUids").add(cqUid);
        postJson(token, root + "/link-types", body);
    }

    private MvcResult postJson(String token, String path, JsonNode body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk()).andReturn();
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String uid(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("uid").asText();
    }

    private static String mapping(String name) {
        Path direct = Path.of("deploy/compose/llm-mock/mappings").resolve(name);
        Path nested = Path.of("../../deploy/compose/llm-mock/mappings").resolve(name);
        return Files.exists(direct) ? direct.toString() : nested.toString();
    }

    private String token(String username, String password) throws Exception {
        MvcResult captcha = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(captcha.getResponse().getContentAsString()).path("data");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", username);
        payload.put("password", password);
        payload.put("captchaId", body.path("captchaId").asText());
        payload.put("captchaCode", body.path("debugCode").asText());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload.toString())).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
