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
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CqTestIntegrationTest {
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

    @Test
    void sparqlAndCypherCoverageFollowResultsNotManualStatus() throws Exception {
        String token = token();
        String namespace = "https://example.com/ont/cq#";
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "cqtest").put("name", "能力问题").put("namespace", namespace)));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cq = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "由哪些零件组成").put("apiName", "whichParts").put("priority", "P0")));
        String product = objectType(token, root, "成品", "Product", cq);
        String part = objectType(token, root, "零件", "Part", cq);
        ObjectNode link = objectMapper.createObjectNode().put("displayName", "组成").put("apiName", "composedOf").put("sourceUid", product).put("targetUid", part).put("cardinality", "ONE_TO_MANY");
        link.putArray("cqUids").add(cq);
        String linkUid = uid(postJson(token, root + "/link-types", link));

        ObjectNode fixture = objectMapper.createObjectNode().put("displayName", "手机夹具").put("apiName", "phoneFixture");
        fixture.put("turtle", "@prefix ex: <" + namespace + "> .\nex:phone ex:composedOf ex:screen .\n");
        var objects = fixture.putArray("objects");
        objects.addObject().put("type", "Product").put("pk", "phone");
        objects.addObject().put("type", "Part").put("pk", "screen");
        fixture.putArray("links").addObject().put("type", "composedOf").put("fromType", "Product").put("fromPk", "phone").put("toType", "Part").put("toPk", "screen");
        String fixtureUid = uid(postJson(token, root + "/fixtures", fixture));

        ObjectNode sparql = testBody("零件数量", "partCount", "SPARQL", """
                PREFIX ex: <%s>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT ?part WHERE { ?product ex:composedOf ?part . ex:composedOf a owl:ObjectProperty . }
                """.formatted(namespace), fixtureUid);
        postJson(token, root + "/cqs/" + cq + "/tests", sparql);
        ObjectNode cypher = testBody("图上的零件", "partGraph", "CYPHER", """
                MATCH (a:MopObject)-[r:MOP_LINK]->(b:MopObject)
                WHERE a._t = $tenant AND r._t = $tenant AND b._t = $tenant AND r._link = 'composedOf'
                RETURN b._pk AS pk
                """, fixtureUid);
        postJson(token, root + "/cqs/" + cq + "/tests", cypher);
        ObjectNode rejected = testBody("拒绝写入", "rejectWrite", "CYPHER", "MATCH (n:MopObject) WHERE n._t = $tenant DELETE n RETURN n._pk", fixtureUid);
        postJson(token, root + "/cqs/" + cq + "/tests", rejected);

        JsonNode first = run(token, root);
        assertThat(caseMessage(first, "partCount")).isEqualTo("通过");
        assertThat(caseMessage(first, "partGraph")).isEqualTo("通过");
        assertThat(caseMessage(first, "rejectWrite")).contains("DELETE");
        assertThat(first.path("coverage").path("cqPassed").asInt()).isZero();
        assertThat(first.path("gateOpen").asBoolean()).isFalse();

        mvc.perform(delete(root + "/elements/" + rejectedUid(token, root, cq)).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        JsonNode passed = run(token, root);
        assertThat(passed.path("coverage").path("cqPassed").asInt()).isEqualTo(1);
        assertThat(passed.path("coverage").path("cqTotal").asInt()).isEqualTo(1);
        assertThat(passed.path("gateOpen").asBoolean()).isTrue();
        long runId = passed.path("runId").asLong();
        JsonNode stored = objectMapper.readTree(mvc.perform(get("/api/v1/cq-runs/" + runId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(stored.path("coverage").path("cqPassed").asInt()).isEqualTo(1);

        ObjectNode marked = objectMapper.createObjectNode().put("displayName", "由哪些零件组成").put("apiName", "whichParts").put("priority", "P0").put("acceptStatus", "已覆盖");
        mvc.perform(put(root + "/cqs/" + cq).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(marked.toString()))
                .andExpect(status().isOk());
        String extra = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "另一个问题").put("apiName", "otherCq").put("priority", "P0").put("acceptStatus", "已覆盖")));
        assertThat(extra).isNotBlank();
        JsonNode half = run(token, root);
        assertThat(half.path("coverage").path("cqPassed").asInt()).isEqualTo(1);
        assertThat(half.path("coverage").path("cqTotal").asInt()).isEqualTo(2);
        assertThat(half.path("gateOpen").asBoolean()).isFalse();

        mvc.perform(delete(root + "/elements/" + linkUid).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        JsonNode after = run(token, root);
        assertThat(caseMessage(after, "partCount")).contains("不满足");
        assertThat(after.path("coverage").path("cqPassed").asInt()).isZero();
    }

    private ObjectNode testBody(String name, String api, String language, String query, String fixtureUid) {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api).put("language", language).put("layer", "ABOX").put("query", query).put("fixtureUid", fixtureUid);
        body.putObject("assert").put("kind", "COUNT").put("op", ">=").put("value", 1);
        return body;
    }

    private String objectType(String token, String root, String name, String api, String cq) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api);
        body.putArray("cqUids").add(cq);
        return uid(postJson(token, root + "/object-types", body));
    }

    private JsonNode run(String token, String root) throws Exception {
        return objectMapper.readTree(postJson(token, root + "/cq-runs", objectMapper.createObjectNode()).getResponse().getContentAsString()).path("data");
    }

    private static String caseMessage(JsonNode run, String api) {
        for (JsonNode item : run.path("cases")) {
            if (api.equals(item.path("apiName").asText())) {
                return item.path("message").asText();
            }
        }
        return "";
    }

    private String rejectedUid(String token, String root, String cq) throws Exception {
        JsonNode tests = objectMapper.readTree(mvc.perform(get(root + "/cqs/" + cq + "/tests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        for (JsonNode test : tests) {
            if ("rejectWrite".equals(test.path("apiName").asText())) {
                return test.path("uid").asText();
            }
        }
        throw new AssertionError("missing rejected test");
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
