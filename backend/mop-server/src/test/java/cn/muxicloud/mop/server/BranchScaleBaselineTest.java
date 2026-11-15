package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BranchScaleBaselineTest {
    private static final int CLASSES = 5000;
    private static final int SAMPLES = 20;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

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
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fiveThousandClassesBranchWithinOneSecondAndViewWithinBudget() throws Exception {
        String token = token();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "scale5k")
                .put("name", "五千类")
                .put("namespace", "https://example.com/ont/scale#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些类").put("apiName", "whichClasses")));
        for (int offset = 0; offset < CLASSES; offset += 250) {
            ArrayNode changes = objectMapper.createArrayNode();
            int end = Math.min(CLASSES, offset + 250);
            for (int index = offset; index < end; index += 1) {
                ObjectNode change = changes.addObject();
                change.put("op", "ADD");
                change.put("type", "OBJECT_TYPE");
                change.put("apiName", "C" + index);
                ObjectNode payload = change.putObject("payload");
                payload.put("displayName", "类" + index);
                payload.put("apiName", "C" + index);
                payload.put("description", "类" + index);
                payload.putArray("cqUids").add(cq);
            }
            postJson(token, root + "/commits", objectMapper.createObjectNode().put("message", "类 " + offset).set("changes", changes));
        }

        long started = System.nanoTime();
        postJson(token, "/api/v1/projects/" + projectId + "/branches", objectMapper.createObjectNode().put("name", "feature-scale"));
        long branchMs = (System.nanoTime() - started) / 1_000_000;
        System.out.println("BASELINE branchCreateMs=" + branchMs);
        assertThat(branchMs).isLessThanOrEqualTo(1000);

        List<Long> samples = new ArrayList<>();
        for (int sample = 0; sample < SAMPLES; sample += 1) {
            long queryStarted = System.nanoTime();
            mvc.perform(get(root + "/elements").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            samples.add((System.nanoTime() - queryStarted) / 1_000_000);
        }
        Collections.sort(samples);
        long p95 = samples.get((int) Math.ceil(SAMPLES * 0.95) - 1);
        System.out.println("BASELINE elementsP95Ms=" + p95 + " samples=" + samples);
        assertThat(p95).isLessThanOrEqualTo(300);
    }

    private MvcResult postJson(String token, String path, JsonNode body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn();
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String uid(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("uid").asText();
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
