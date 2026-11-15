package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ModelingIntegrationTest {
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
    void formWritesGoThroughCommitsAndRequireCq() throws Exception {
        String token = token();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "grid")
                .put("name", "电网")
                .put("namespace", "https://muxi.example/ont/grid#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;

        mvc.perform(post(root + "/object-types").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"变压器\",\"apiName\":\"Transformer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("MOP-2006"));

        String cqUid = idText(postJson(token, root + "/cqs", objectMapper.createObjectNode()
                .put("displayName", "有哪些设备")
                .put("apiName", "whichAssets")
                .put("priority", "P0")
                .put("acceptance", "能列出设备")));
        ObjectNode transformer = objectMapper.createObjectNode()
                .put("displayName", "变压器")
                .put("apiName", "Transformer")
                .put("description", "主设备");
        transformer.putArray("cqUids").add(cqUid);
        String typeUid = idText(postJson(token, root + "/object-types", transformer));

        ObjectNode voltage = objectMapper.createObjectNode()
                .put("displayName", "额定电压")
                .put("apiName", "ratedVoltage")
                .put("ownerUid", typeUid)
                .put("required", true)
                .put("minCount", 1)
                .put("maxCount", 1)
                .put("defaultValue", "10");
        voltage.putObject("valueType").put("kind", "BASIC").put("basic", "DECIMAL").put("unit", "kV");
        String propertyUid = idText(postJson(token, root + "/properties", voltage));

        ObjectNode station = transformer.deepCopy();
        station.put("displayName", "变电站");
        station.put("apiName", "Substation");
        String stationUid = idText(postJson(token, root + "/object-types", station));
        ObjectNode link = objectMapper.createObjectNode()
                .put("displayName", "安装于")
                .put("apiName", "installedAt")
                .put("sourceUid", typeUid)
                .put("targetUid", stationUid)
                .put("cardinality", "MANY_TO_ONE")
                .put("invertible", true);
        link.putObject("inverse").put("displayName", "安装设备").put("apiName", "hasEquipment");
        link.putArray("cqUids").add(cqUid);
        postJson(token, root + "/link-types", link);

        postJson(token, root + "/terms", objectMapper.createObjectNode().put("displayName", "变压器").put("apiName", "transformerTerm").put("englishName", "Transformer"));
        postJson(token, root + "/data-types", objectMapper.createObjectNode().put("displayName", "电压").put("apiName", "Voltage").put("baseType", "DECIMAL").put("unit", "kV"));
        String enumUid = idText(postJson(token, root + "/enums", objectMapper.createObjectNode().put("displayName", "运行状态").put("apiName", "RunState")));
        postJson(token, root + "/enum-values", objectMapper.createObjectNode().put("displayName", "运行").put("apiName", "Running").put("ownerUid", enumUid));
        ObjectNode axiom = objectMapper.createObjectNode().put("kind", "SUBCLASS").put("subjectUid", typeUid).put("objectUid", stationUid);
        postJson(token, root + "/axioms", axiom);
        ObjectNode rule = objectMapper.createObjectNode()
                .put("kind", "RANGE")
                .put("targetObjectUid", typeUid)
                .put("targetPropertyUid", propertyUid)
                .put("min", 0)
                .put("max", 1000)
                .put("severity", "Violation")
                .put("message", "额定电压超出范围");
        postJson(token, root + "/data-rules", rule);

        transformer.put("apiName", "Breaker");
        mvc.perform(put(root + "/object-types/" + typeUid).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(transformer.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("API 名创建后不能修改"));

        mvc.perform(delete(root + "/elements/" + enumUid).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("MOP-2005"));

        String elements = mvc.perform(get(root + "/elements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(elements).contains("ratedVoltage", "installedAt", "whichAssets", "RunState");
        assertThat(objectMapper.readTree(elements).path("data")).anyMatch(node ->
                "PROPERTY".equals(node.path("elementType").asText()) && node.path("payload").asText().contains("\"defaultValue\": \"10\""));
    }

    private MvcResult postJson(String token, String path, JsonNode body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String idText(MvcResult result) throws Exception {
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
