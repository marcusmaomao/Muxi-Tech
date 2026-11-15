package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class QualityIntegrationTest {
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
    void unsatisfiableClassBlocksReleaseUntilTheDisjointAxiomIsRemoved() throws Exception {
        String token = token();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "quality").put("name", "质量").put("namespace", "https://example.com/ont/quality#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些").put("apiName", "which")));
        String classA = objectType(token, root, "甲类", "ClassA", cq, null);
        String classB = objectType(token, root, "乙类", "ClassB", cq, null);
        objectType(token, root, "冲突类", "Clash", cq, new String[] {classA, classB});
        ObjectNode axiom = objectMapper.createObjectNode().put("displayName", "甲乙互斥").put("apiName", "classADisjointClassB").put("kind", "DISJOINT").put("subjectUid", classA).put("objectUid", classB);
        String axiomUid = uid(postJson(token, root + "/axioms", axiom));

        JsonNode blockedRun = objectMapper.readTree(mvc.perform(post(root + "/quality-runs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(blockedRun.path("blocked").asBoolean()).isTrue();
        assertThat(blockedRun.path("issues").toString()).contains("不可满足");
        assertThat(blockedRun.path("score").asDouble()).isLessThan(100);
        long runId = blockedRun.path("runId").asLong();
        JsonNode trend = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/quality/trend").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(trend.toString()).contains(String.valueOf(runId));

        MvcResult rejected = mvc.perform(post(root + "/releases").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(rejected.getResponse().getContentAsString()).contains("MOP-2201");

        mvc.perform(delete(root + "/elements/" + axiomUid).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        JsonNode release = objectMapper.readTree(mvc.perform(post(root + "/releases").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        long releaseId = release.path("id").asLong();
        String compiled = mvc.perform(get("/api/v1/releases/" + releaseId + "/compiled-model").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(compiled).contains("ClassA").contains("ClassB").contains("Clash");
        assertThat(compiled).contains("https://example.com/ont/quality#");
    }

    private String objectType(String token, String root, String name, String api, String cq, String[] parents) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api).put("description", name);
        body.putArray("cqUids").add(cq);
        if (parents != null) {
            var array = body.putArray("parentUids");
            for (String parent : parents) {
                array.add(parent);
            }
        }
        return uid(postJson(token, root + "/object-types", body));
    }

    private MvcResult postJson(String token, String path, JsonNode body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
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
