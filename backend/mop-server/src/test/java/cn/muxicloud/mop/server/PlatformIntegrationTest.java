package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PlatformIntegrationTest {
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
    void loginRefreshAndTenantIsolation() throws Exception {
        JsonNode login = login("admin", "demo-admin");
        String token = login.path("data").path("accessToken").asText();
        String refresh = login.path("data").path("refreshToken").asText();
        assertThat(token).isNotBlank();

        MvcResult me = mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode profile = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(profile.path("data").path("permissions").toString()).contains("system:user:list");

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String otherToken = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"other\",\"name\":\"另一租户\",\"adminUsername\":\"other-admin\",\"adminPassword\":\"demo-admin\",\"adminDisplayName\":\"另一管理员\"}"))
                .andExpect(status().isOk());

        JsonNode otherLogin = login("other-admin", "demo-admin");
        String other = otherLogin.path("data").path("accessToken").asText();
        JsonNode ownUsers = users(token);
        JsonNode otherUsers = users(other);
        assertThat(ownUsers.toString()).contains("admin");
        assertThat(ownUsers.toString()).doesNotContain("other-admin");
        assertThat(otherUsers.toString()).contains("other-admin");
        assertThat(otherUsers.toString()).doesNotContain("\"username\":\"admin\"");
    }

    @Test
    void commitProtectsReferencedElements() throws Exception {
        JsonNode login = login("admin", "demo-admin");
        String token = login.path("data").path("accessToken").asText();
        MvcResult created = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"grid\",\"name\":\"电网\",\"namespace\":\"https://muxi.example/ont/grid#\",\"description\":\"设备\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        MvcResult branches = mvc.perform(get("/api/v1/projects/" + projectId + "/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long branchId = objectMapper.readTree(branches.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        mvc.perform(post("/api/v1/branches/" + branchId + "/commits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"能力问题\",\"changes\":[{\"op\":\"ADD\",\"type\":\"CQ\",\"apiName\":\"whichAssets\",\"payload\":{\"displayName\":\"有哪些设备\",\"apiName\":\"whichAssets\"}}]}"))
                .andExpect(status().isOk());
        String cqUid = elementUid(token, branchId, "CQ");
        mvc.perform(post("/api/v1/branches/" + branchId + "/commits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"类型\",\"changes\":[{\"op\":\"ADD\",\"type\":\"OBJECT_TYPE\",\"apiName\":\"Transformer\",\"payload\":{\"displayName\":\"变压器\",\"apiName\":\"Transformer\",\"cqUids\":[\"" + cqUid + "\"]}}]}"))
                .andExpect(status().isOk());
        String typeUid = elementUid(token, branchId, "OBJECT_TYPE");
        mvc.perform(post("/api/v1/branches/" + branchId + "/commits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"属性\",\"changes\":[{\"op\":\"ADD\",\"type\":\"PROPERTY\",\"apiName\":\"ratedVoltage\",\"payload\":{\"displayName\":\"额定电压\",\"apiName\":\"ratedVoltage\",\"ownerUid\":\"" + typeUid + "\"}}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/branches/" + branchId + "/commits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删除类型\",\"changes\":[{\"op\":\"DELETE\",\"type\":\"OBJECT_TYPE\",\"uid\":\"" + typeUid + "\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("MOP-2005"));
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult captcha = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(captcha.getResponse().getContentAsString()).path("data");
        String payload = objectMapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .put("captchaId", body.path("captchaId").asText())
                .put("captchaCode", body.path("debugCode").asText())
                .toString();
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String elementUid(String token, long branchId, String type) throws Exception {
        MvcResult elements = mvc.perform(get("/api/v1/branches/" + branchId + "/elements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(elements.getResponse().getContentAsString()).path("data")) {
            if (type.equals(node.path("elementType").asText())) {
                return node.path("uid").asText();
            }
        }
        throw new AssertionError("缺少元素 " + type);
    }

    private JsonNode users(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/system/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
