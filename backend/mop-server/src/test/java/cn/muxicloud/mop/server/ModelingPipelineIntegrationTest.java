package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ModelingPipelineIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379).waitingFor(Wait.forListeningPort());

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
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("modeling-invalid.json")), "/home/wiremock/mappings/modeling-invalid.json")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("modeling-round-1.json")), "/home/wiremock/mappings/modeling-round-1.json")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("modeling-round-2.json")), "/home/wiremock/mappings/modeling-round-2.json")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("modeling-round-3.json")), "/home/wiremock/mappings/modeling-round-3.json")
            .withCopyFileToContainer(MountableFile.forHostPath(mapping("modeling-round-4.json")), "/home/wiremock/mappings/modeling-round-4.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("mop.jwt.secret", () -> "integration-test-jwt-secret-32bytes");
        registry.add("mop.demo.seed", () -> "true");
        registry.add("mop.demo.admin-username", () -> "admin");
        registry.add("mop.demo.admin-password", () -> "demo-admin");
        registry.add("mop.captcha.expose", () -> "true");
        registry.add("mop.datasource.secret", () -> "integration-datasource-secret");
        registry.add("mop.ai.key-secret", () -> "integration-ai-key-secret");
        registry.add("mop.ai.rate-limit", () -> "redis");
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
    void happyPathCanPauseResumeAndAdoptOntoTheBranch() throws Exception {
        String token = token();
        long branchId = branch(token, "ai-grid");
        long fileId = upload(token, "设备说明.txt", "变压器是电网的主设备。额定电压需要记录。");
        long modelId = model(token);
        long jobId = start(token, branchId, fileId, modelId);
        JsonNode paused = resume(token, jobId, "R1");
        assertThat(paused.path("status").asText()).isEqualTo("PAUSED");
        assertThat(paused.path("stage").asText()).isEqualTo("R1");
        assertThat(candidates(token, jobId).toString()).contains("whichDevices");

        JsonNode done = resume(token, jobId, null);
        assertThat(done.path("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode rows = candidates(token, jobId);
        long cqId = candidateId(rows, "whichDevices");
        long termId = candidateId(rows, "transformerTerm");
        mvc.perform(put("/api/v1/ai/candidates/" + termId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"主设备术语\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ai/candidates/" + termId + "/reject").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ai/candidates/" + cqId + "/adopt").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        long objectId = candidateId(candidates(token, jobId), "Transformer");
        mvc.perform(post("/api/v1/ai/candidates/" + objectId + "/adopt").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        String elements = mvc.perform(get("/api/v1/branches/" + branchId + "/elements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(elements).contains("Transformer").contains("whichDevices");

        MvcResult async = mvc.perform(get("/api/v1/ai/jobs/" + jobId + "/events").header("Authorization", "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        String stream = mvc.perform(asyncDispatch(async)).andReturn().getResponse().getContentAsString();
        assertThat(stream).contains("PARSE").contains("DONE");
    }

    @Test
    void invalidModelOutputFailsTheJob() throws Exception {
        String token = token();
        long branchId = branch(token, "ai-bad");
        long fileId = upload(token, "坏文档.txt", "INVALID-OUTPUT 无法形成结构。");
        long modelId = model(token);
        long jobId = start(token, branchId, fileId, modelId);
        JsonNode failed = resume(token, jobId, null);
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("error").asText()).contains("JSON");
    }

    private JsonNode resume(String token, long jobId, String through) throws Exception {
        String url = "/api/v1/ai/jobs/" + jobId + "/resume" + (through == null ? "" : "?through=" + through);
        MvcResult result = mvc.perform(post(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode candidates(String token, long jobId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/ai/jobs/" + jobId + "/candidates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static long candidateId(JsonNode rows, String apiName) {
        for (JsonNode row : rows) {
            if (row.path("candidateKey").asText().endsWith(":" + apiName)) {
                return row.path("id").asLong();
            }
        }
        throw new AssertionError(apiName);
    }

    private long start(String token, long branchId, long fileId, long modelId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", 0);
        body.put("branchId", branchId);
        body.put("fileId", fileId);
        body.put("modelId", modelId);
        body.put("background", false);
        MvcResult result = mvc.perform(post("/api/v1/ai/modeling-jobs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long branch(String token, String code) throws Exception {
        ObjectNode project = objectMapper.createObjectNode();
        project.put("code", code);
        project.put("name", code);
        project.put("namespace", "https://example.com/ont/#");
        MvcResult created = mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(project.toString()))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        MvcResult branches = mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(branches.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
    }

    private long upload(String token, String name, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mvc.perform(multipart("/api/v1/files").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long model(String token) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("displayName", "回放");
        body.put("modelName", "mock-" + System.nanoTime());
        body.put("vendor", "LOCAL");
        body.put("baseUrl", "http://" + MOCK.getHost() + ":" + MOCK.getMappedPort(8080) + "/v1");
        body.put("apiKey", "sk-test");
        body.put("rpmLimit", 200);
        MvcResult result = mvc.perform(post("/api/v1/ai/models").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private static String mapping(String name) {
        Path direct = Path.of("deploy/compose/llm-mock/mappings").resolve(name);
        Path nested = Path.of("../../deploy/compose/llm-mock/mappings").resolve(name);
        return Files.exists(direct) ? direct.toString() : nested.toString();
    }

    private String token() throws Exception {
        MvcResult captcha = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(captcha.getResponse().getContentAsString()).path("data");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", "admin");
        payload.put("password", "demo-admin");
        payload.put("captchaId", body.path("captchaId").asText());
        payload.put("captchaCode", body.path("debugCode").asText());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
