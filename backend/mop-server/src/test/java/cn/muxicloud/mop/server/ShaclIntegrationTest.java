package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
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
class ShaclIntegrationTest {
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
    void shapesExportAndReportAgreeOnARangeViolation() throws Exception {
        String token = token();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "shacl")
                .put("name", "形状")
                .put("namespace", "https://example.com/ont/shacl#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cqUid = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些设备").put("apiName", "whichAssets")));
        ObjectNode type = objectMapper.createObjectNode().put("displayName", "变压器").put("apiName", "Transformer");
        type.putArray("cqUids").add(cqUid);
        String typeUid = uid(postJson(token, root + "/object-types", type));
        ObjectNode property = objectMapper.createObjectNode()
                .put("displayName", "额定电压")
                .put("apiName", "ratedVoltage")
                .put("ownerUid", typeUid)
                .put("required", true)
                .put("minCount", 1);
        property.putObject("valueType").put("kind", "BASIC").put("basic", "DECIMAL");
        String propertyUid = uid(postJson(token, root + "/properties", property));
        ObjectNode rule = objectMapper.createObjectNode()
                .put("displayName", "电压范围")
                .put("apiName", "voltageRange")
                .put("kind", "RANGE")
                .put("targetObjectUid", typeUid)
                .put("targetPropertyUid", propertyUid)
                .put("min", 0)
                .put("max", 1000)
                .put("inclusive", true)
                .put("severity", "Violation")
                .put("message", "额定电压超出范围");
        postJson(token, root + "/data-rules", rule);

        String shapes = new String(mvc.perform(get(root + "/shapes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(shapes).contains("NodeShape");
        assertThat(shapes).contains("minCount");
        assertThat(shapes).contains("minInclusive");
        assertThat(shapes).contains("maxInclusive");
        assertThat(shapes).contains("ratedVoltage");
        String viaExport = new String(mvc.perform(get(root + "/export").param("shapes", "true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(viaExport).contains("ratedVoltage");

        String data = """
                @prefix ex: <https://example.com/ont/shacl#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:over a ex:Transformer ; ex:ratedVoltage "1500"^^xsd:decimal .
                """;
        JsonNode report = objectMapper.readTree(postJson(token, root + "/shacl/validate", objectMapper.createObjectNode().put("data", data))
                .getResponse().getContentAsString()).path("data");
        assertThat(report.path("conforms").asBoolean()).isFalse();
        assertThat(report.path("issues").toString()).contains("https://example.com/ont/shacl#over");
        assertThat(report.path("issues").toString()).contains("Violation");
        long runId = report.path("runId").asLong();
        JsonNode stored = objectMapper.readTree(mvc.perform(get("/api/v1/sem-runs/" + runId + "/report").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(stored.path("conforms").asBoolean()).isFalse();
        assertThat(stored.path("issues")).hasSize(report.path("issues").size());
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
