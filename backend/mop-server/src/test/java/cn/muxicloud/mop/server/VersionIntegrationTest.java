package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class VersionIntegrationTest {
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
        registry.add("mop.model.view-cache", () -> "memory");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void branchIsMetadataOnlyAndThreeWayMergeKeepsTheFeatureDescription() throws Exception {
        String token = token();
        long projectId = project(token, "merge");
        long mainId = mainBranch(token, projectId);
        String cqUid = cq(token, mainId);
        ObjectNode transformer = object(cqUid, "主设备", "变压器");
        String typeUid = uid(postJson(token, branchRoot(mainId) + "/object-types", transformer));
        long featureId = createBranch(token, projectId, "feature-defect", mainId);
        Integer copied = jdbc.queryForObject("SELECT COUNT(*) FROM mop_element WHERE branch_id = ?", Integer.class, featureId);
        assertThat(copied).isZero();
        assertThat(elements(token, featureId)).contains("变压器");

        transformer.put("description", "功能分支");
        putJson(token, branchRoot(featureId) + "/object-types/" + typeUid, transformer);
        transformer.put("description", "主干");
        putJson(token, branchRoot(mainId) + "/object-types/" + typeUid, transformer);
        long mainBeforeMerge = objectMapper.readTree(mvc.perform(get(branchRoot(mainId) + "/commits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("rev").asLong();

        ObjectNode request = objectMapper.createObjectNode();
        request.put("sourceBranchId", featureId);
        request.put("targetBranchId", mainId);
        request.put("title", "合并缺陷管理");
        JsonNode opened = data(postJson(token, "/api/v1/projects/" + projectId + "/merge-requests", request));
        assertThat(opened.path("status").asText()).isEqualTo("CONFLICT");
        assertThat(opened.path("conflicts").asText()).contains("/description");
        String listed = mvc.perform(get("/api/v1/projects/" + projectId + "/merge-requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listed).contains("合并缺陷管理");

        ArrayNode resolutions = objectMapper.createArrayNode();
        resolutions.addObject().put("uid", typeUid).put("choice", "SOURCE");
        ObjectNode resolve = objectMapper.createObjectNode().set("resolutions", resolutions);
        JsonNode resolved = data(postJson(token, "/api/v1/merge-requests/" + opened.path("id").asLong() + "/resolve", resolve));
        assertThat(resolved.path("status").asText()).isEqualTo("OPEN");
        postJson(token, "/api/v1/merge-requests/" + opened.path("id").asLong() + "/reviews",
                objectMapper.createObjectNode().put("decision", "APPROVE").put("comment", "保留功能分支"));
        postJson(token, "/api/v1/merge-requests/" + opened.path("id").asLong() + "/merge", objectMapper.createObjectNode());

        assertThat(elements(token, mainId)).contains("功能分支");
        String diff = mvc.perform(get("/api/v1/projects/" + projectId + "/diff")
                        .param("from", "branch:main@" + mainBeforeMerge)
                        .param("to", "branch:main")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(diff).contains("/description").contains("功能分支");
    }

    @Test
    void differentFieldsMergeWithoutAConflict() throws Exception {
        String token = token();
        long projectId = project(token, "auto");
        long mainId = mainBranch(token, projectId);
        String cqUid = cq(token, mainId);
        ObjectNode transformer = object(cqUid, "主设备", "变压器");
        String typeUid = uid(postJson(token, branchRoot(mainId) + "/object-types", transformer));
        long featureId = createBranch(token, projectId, "feature-name", mainId);
        transformer.put("description", "只改说明");
        putJson(token, branchRoot(featureId) + "/object-types/" + typeUid, transformer);
        transformer.put("description", "主设备");
        transformer.put("displayName", "主变");
        putJson(token, branchRoot(mainId) + "/object-types/" + typeUid, transformer);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("sourceBranchId", featureId);
        request.put("targetBranchId", mainId);
        request.put("title", "字段合并");
        JsonNode opened = data(postJson(token, "/api/v1/projects/" + projectId + "/merge-requests", request));
        assertThat(opened.path("status").asText()).isEqualTo("OPEN");
        postJson(token, "/api/v1/merge-requests/" + opened.path("id").asLong() + "/merge", objectMapper.createObjectNode());
        String merged = elements(token, mainId);
        assertThat(merged).contains("只改说明").contains("主变");
    }

    @Test
    void protectedBranchRejectsDirectCommitButAcceptsMergeAndRevertRestoresThePreviousRevision() throws Exception {
        String token = token();
        long projectId = project(token, "guard");
        long mainId = mainBranch(token, projectId);
        String cqUid = cq(token, mainId);
        long createdRev = objectMapper.readTree(mvc.perform(get(branchRoot(mainId) + "/commits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("rev").asLong();
        long featureId = createBranch(token, projectId, "feature-extra", mainId);
        cq(token, featureId, "whichBreakers", "有哪些断路器");

        putJson(token, "/api/v1/branches/" + mainId + "/protect", objectMapper.createObjectNode().put("protected", true));
        mvc.perform(post(branchRoot(mainId) + "/cqs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("displayName", "被拒绝").put("apiName", "blocked").put("priority", "P1").put("acceptance", "不能直接写").toString()))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("受保护"));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("sourceBranchId", featureId);
        request.put("targetBranchId", mainId);
        request.put("title", "合并到受保护分支");
        JsonNode opened = data(postJson(token, "/api/v1/projects/" + projectId + "/merge-requests", request));
        assertThat(opened.path("status").asText()).isEqualTo("OPEN");
        postJson(token, "/api/v1/merge-requests/" + opened.path("id").asLong() + "/merge", objectMapper.createObjectNode());
        assertThat(elements(token, mainId)).contains("有哪些断路器");

        postJson(token, branchRoot(mainId) + "/revert/" + createdRev, objectMapper.createObjectNode());
        assertThat(elements(token, mainId)).doesNotContain("有哪些断路器").contains(cqUid);
    }

    @Test
    void chainDeeperThanFiveIsRejectedAndACycleIsRejected() throws Exception {
        String token = token();
        long projectId = project(token, "chain");
        long parent = mainBranch(token, projectId);
        cq(token, parent);
        long tip = parent;
        for (int i = 1; i <= 4; i++) {
            tip = createBranch(token, projectId, "layer-" + i, tip);
            assertThat(mvc.perform(get(branchRoot(tip) + "/elements").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus()).isEqualTo(200);
        }
        long tooDeep = createBranch(token, projectId, "layer-5", tip);
        mvc.perform(get(branchRoot(tooDeep) + "/elements").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("分支链"));

        long cycleProject = project(token, "cycle");
        long cycleMain = mainBranch(token, cycleProject);
        String cqUid = cq(token, cycleMain);
        ObjectNode asset = object(cqUid, "资产", "设备资产");
        asset.put("apiName", "Asset");
        String assetUid = uid(postJson(token, branchRoot(cycleMain) + "/object-types", asset));
        ObjectNode line = object(cqUid, "线路", "线路");
        line.put("apiName", "Line");
        line.putArray("parentUids").add(assetUid);
        String lineUid = uid(postJson(token, branchRoot(cycleMain) + "/object-types", line));
        asset.putArray("parentUids").add(lineUid);
        mvc.perform(put(branchRoot(cycleMain) + "/object-types/" + assetUid).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(asset.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("MOP-2003"));
    }

    private String elements(String token, long branchId) throws Exception {
        return mvc.perform(get(branchRoot(branchId) + "/elements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long createBranch(String token, long projectId, String name, long parentId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("name", name).put("parentBranchId", parentId);
        return data(postJson(token, "/api/v1/projects/" + projectId + "/branches", body)).path("id").asLong();
    }

    private String cq(String token, long branchId) throws Exception {
        return cq(token, branchId, "whichAssets", "有哪些设备");
    }

    private String cq(String token, long branchId, String apiName, String name) throws Exception {
        return uid(postJson(token, branchRoot(branchId) + "/cqs", objectMapper.createObjectNode()
                .put("displayName", name).put("apiName", apiName).put("priority", "P1").put("acceptance", "能列出")));
    }

    private ObjectNode object(String cqUid, String description, String name) {
        ObjectNode node = objectMapper.createObjectNode().put("displayName", name).put("apiName", "Transformer").put("description", description);
        node.putArray("cqUids").add(cqUid);
        return node;
    }

    private long project(String token, String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return data(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", prefix + suffix)
                .put("name", prefix)
                .put("namespace", "https://example.com/ont/" + suffix + "#"))).path("id").asLong();
    }

    private long mainBranch(String token, long projectId) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
    }

    private static String branchRoot(long branchId) {
        return "/api/v1/branches/" + branchId;
    }

    private MvcResult postJson(String token, String path, JsonNode body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void putJson(String token, String path, JsonNode body) throws Exception {
        mvc.perform(put(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String uid(MvcResult result) throws Exception {
        return data(result).path("uid").asText();
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
