package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LlmGatewayIntegrationTest {
    private static final String SECRET = "sk-test-secret";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> MOCK = new GenericContainer<>("wiremock/wiremock:3.9.1")
            .withCopyToContainer(Transferable.of(mapping()), "/home/wiremock/mappings/chat-completions.json")
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
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void marketHidesKeyAndCallsLlmMock() throws Exception {
        String token = token();
        String base = "http://" + MOCK.getHost() + ":" + MOCK.getMappedPort(8080) + "/v1";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("displayName", "本地回放");
        body.put("modelName", "mock-chat");
        body.put("vendor", "LOCAL");
        body.put("baseUrl", base);
        body.put("apiKey", SECRET);
        body.put("rpmLimit", 1);
        MvcResult created = mvc.perform(post("/api/v1/ai/models").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn();
        String createdBody = created.getResponse().getContentAsString();
        assertThat(createdBody).doesNotContain(SECRET);
        long modelId = objectMapper.readTree(createdBody).path("data").path("id").asLong();

        String listed = mvc.perform(get("/api/v1/ai/models").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listed).contains("mock-chat").doesNotContain(SECRET);

        String probe = mvc.perform(post("/api/v1/ai/models/" + modelId + "/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(probe).contains("llm-mock").doesNotContain(SECRET);

        ObjectNode chat = objectMapper.createObjectNode();
        chat.put("modelId", modelId);
        chat.putArray("messages").addObject().put("role", "user").put("content", "ping");
        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(chat.toString()))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("MOP-3003").doesNotContain(SECRET));

        String usage = mvc.perform(get("/api/v1/ai/usage").param("modelId", Long.toString(modelId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(usage).contains("\"success\":1").doesNotContain(SECRET);
    }

    private static byte[] mapping() {
        try {
            Path direct = Path.of("deploy/compose/llm-mock/mappings/chat-completions.json");
            Path nested = Path.of("../../deploy/compose/llm-mock/mappings/chat-completions.json");
            Path path = Files.exists(direct) ? direct : nested;
            return Files.readAllBytes(path);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
