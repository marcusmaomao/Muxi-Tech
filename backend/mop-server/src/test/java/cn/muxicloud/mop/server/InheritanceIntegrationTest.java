package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
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
class InheritanceIntegrationTest {
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
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void multipleParentsExposeInheritedPropertiesAndRejectANameClash() throws Exception {
        String token = token();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "inherit")
                .put("name", "继承")
                .put("namespace", "https://example.com/ont/inherit#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode()
                .put("displayName", "有哪些设备").put("apiName", "whichAssets").put("priority", "P1").put("acceptance", "能列出")));
        String asset = uid(postJson(token, root + "/object-types", objectType("设备资产", "Asset", cq)));
        String line = uid(postJson(token, root + "/object-types", objectType("线路", "Line", cq)));
        postJson(token, root + "/properties", property("名称甲", "titleName", asset, false));
        postJson(token, root + "/properties", property("名称乙", "titleName", line, false));

        ObjectNode combo = objectType("组合设备", "Combo", cq);
        combo.putArray("parentUids").add(asset).add(line);
        mvc.perform(post(root + "/object-types").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(combo.toString()))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("MOP-2004"));

        String comboUid = uid(postJson(token, root + "/object-types", objectType("组合设备", "Combo", cq)));
        postJson(token, root + "/properties", property("组合名", "titleName", comboUid, true));
        combo.put("displayName", "组合设备");
        mvc.perform(put(root + "/object-types/" + comboUid).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(combo.toString()))
                .andExpect(status().isOk());
        String effective = mvc.perform(get(root + "/elements/" + comboUid + "/effective").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(effective).contains("OWN").contains("组合名");
    }

    private ObjectNode objectType(String name, String api, String cq) {
        ObjectNode node = objectMapper.createObjectNode().put("displayName", name).put("apiName", api);
        node.putArray("cqUids").add(cq);
        return node;
    }

    private ObjectNode property(String name, String api, String owner, boolean override) {
        ObjectNode node = objectMapper.createObjectNode()
                .put("displayName", name)
                .put("apiName", api)
                .put("ownerUid", owner)
                .put("override", override);
        node.putObject("valueType").put("kind", "BASIC").put("basic", "STRING");
        return node;
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
