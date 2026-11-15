package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.muxicloud.mop.runtime.graph.GraphWriter;
import cn.muxicloud.mop.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
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
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReasonIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

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
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GraphWriter graph;

    @Test
    void elkReportAndSubtypeQueryUseTheSameClassification() throws Exception {
        String token = token();
        long tenantId = objectMapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("user").path("tenantId").asLong();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "reason")
                .put("name", "推理")
                .put("namespace", "https://example.com/ont/reason#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些设备").put("apiName", "whichAssets")));
        String device = objectType(token, root, "设备", "Device", cq, null);
        objectType(token, root, "变压器", "Transformer", cq, device);
        objectType(token, root, "断路器", "Breaker", cq, device);

        JsonNode report = objectMapper.readTree(postJson(token, root + "/reason", objectMapper.createObjectNode().put("reasoner", "ELK"))
                .getResponse().getContentAsString()).path("data");
        assertThat(report.path("unsatisfiable")).isEmpty();
        assertThat(report.path("types").toString()).contains("Transformer").contains("Breaker");
        long runId = report.path("runId").asLong();
        JsonNode stored = objectMapper.readTree(mvc.perform(get("/api/v1/sem-runs/" + runId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(stored.path("reasoner").asText()).isEqualTo("ELK");
        assertThat(stored.path("types").toString()).contains("Device");

        TenantContext.set(tenantId);
        try {
            graph.mergeObjects(java.util.List.of(
                    new GraphWriter.ObjectRow("Transformer", "t1", "rid-t1", Map.of("name", "一号变压器")),
                    new GraphWriter.ObjectRow("Breaker", "b1", "rid-b1", Map.of("name", "一号断路器"))));
        } finally {
            TenantContext.clear();
        }
        JsonNode expanded = objectMapper.readTree(mvc.perform(get(root + "/objects").param("type", "Device").param("includeSubtypes", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(expanded.toString()).contains("t1").contains("b1");
        JsonNode exact = objectMapper.readTree(mvc.perform(get(root + "/objects").param("type", "Device").param("includeSubtypes", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(exact).isEmpty();
    }

    private String objectType(String token, String root, String name, String api, String cq, String parent) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api);
        body.putArray("cqUids").add(cq);
        if (parent != null) {
            body.putArray("parentUids").add(parent);
        }
        return uid(postJson(token, root + "/object-types", body));
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
