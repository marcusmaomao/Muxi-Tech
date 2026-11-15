package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
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
class SyncIntegrationTest {
    @Container
    static final MySQLContainer<?> APP = new MySQLContainer<>("mysql:8.4");

    @Container
    static final MySQLContainer<?> SOURCE = new MySQLContainer<>("mysql:8.4");

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", APP::getJdbcUrl);
        registry.add("spring.datasource.username", APP::getUsername);
        registry.add("spring.datasource.password", APP::getPassword);
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

    @BeforeAll
    static void sourceTables() throws Exception {
        try (Connection connection = DriverManager.getConnection(SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE src_station (id VARCHAR(32) PRIMARY KEY, name VARCHAR(64) NOT NULL, updated_at VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE src_equipment (id VARCHAR(32) PRIMARY KEY, name VARCHAR(64) NOT NULL, station_id VARCHAR(32) NOT NULL, updated_at VARCHAR(32) NOT NULL, CONSTRAINT fk_eq_station FOREIGN KEY (station_id) REFERENCES src_station (id))");
            statement.execute("INSERT INTO src_station VALUES ('s1', '城东站', '2026-01-01 00:00:00')");
            statement.execute("INSERT INTO src_equipment VALUES ('e1', '一号变压器', 's1', '2026-01-01 00:00:00')");
            statement.execute("CREATE TABLE src_meter (id VARCHAR(32) PRIMARY KEY, name VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO src_meter VALUES ('a', '甲')");
            statement.execute("CREATE TABLE src_note (id VARCHAR(32) PRIMARY KEY, name VARCHAR(128) NOT NULL, updated_at VARCHAR(32) NOT NULL)");
            statement.execute("INSERT INTO src_note VALUES ('n1', 'MATCH (n) DETACH DELETE n', '2026-01-01 00:00:00')");
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullIncrementalAndForeignKeySyncStayInsideTheTenant() throws Exception {
        String token = token("admin", "demo-admin");
        long branchId = branch(token, "sync-grid");
        String root = "/api/v1/branches/" + branchId;
        String cqUid = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些站点").put("apiName", "whichStations")));
        String stationUid = object(token, root, "变电站", "Substation", cqUid);
        String equipmentUid = object(token, root, "设备", "Equipment", cqUid);
        String stationProperty = property(token, root, "站名", "stationName", stationUid);
        String equipmentProperty = property(token, root, "设备名", "equipmentName", equipmentUid);
        String linkUid = link(token, root, stationUid, equipmentUid, cqUid);
        long datasourceId = datasource(token, "src");

        String keys = mvc.perform(get("/api/v1/datasources/" + datasourceId + "/tables/src_equipment/foreign-keys").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(keys).contains("station_id").contains("src_station");

        long stationMapping = save(token, branchId, datasourceId, stationUid, "src_station", "id", "UPDATED_AT", "updated_at", stationProperty, null, null, null);
        long second = save(token, branchId, datasourceId, stationUid, "src_station", "id", "UPDATED_AT", "updated_at", stationProperty, null, null, null);
        assertThat(second).isNotEqualTo(stationMapping);
        String versions = mvc.perform(get("/api/v1/branches/" + branchId + "/bindings").param("datasourceId", String.valueOf(datasourceId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(versions).contains("\"versionNo\":1").contains("\"versionNo\":2");

        String preview = mvc.perform(post("/api/v1/bindings/" + stationMapping + "/preview").param("limit", "20").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("城东站");
        JsonNode stationRun = sync(token, stationMapping, "FULL");
        assertThat(stationRun.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(stationRun.path("insertedCount").asInt()).isEqualTo(1);

        long equipmentMapping = save(token, branchId, datasourceId, equipmentUid, "src_equipment", "id", "UPDATED_AT", "updated_at", equipmentProperty, linkUid, "station_id", stationUid);
        JsonNode equipmentRun = sync(token, equipmentMapping, "FULL");
        assertThat(equipmentRun.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(equipmentRun.path("linkCount").asInt()).isEqualTo(1);
        assertThat(objects(token, "Equipment").toString()).contains("e1").contains("一号变压器");

        try (Connection connection = DriverManager.getConnection(SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO src_equipment VALUES ('e2', '二号变压器', 's1', '2026-02-01 00:00:00')");
            statement.execute("INSERT INTO src_meter VALUES ('b', '乙')");
        }
        JsonNode incremental = sync(token, equipmentMapping, "INCREMENTAL");
        assertThat(incremental.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(incremental.path("insertedCount").asInt()).isEqualTo(1);
        assertThat(objects(token, "Equipment").toString()).contains("e2").contains("二号变压器");

        String meterUid = object(token, root, "电表", "Meter", cqUid);
        String meterProperty = property(token, root, "表名", "meterName", meterUid);
        long meterMapping = save(token, branchId, datasourceId, meterUid, "src_meter", "id", "PK", null, meterProperty, null, null, null);
        assertThat(sync(token, meterMapping, "FULL").path("insertedCount").asInt()).isEqualTo(2);
        try (Connection connection = DriverManager.getConnection(SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO src_meter VALUES ('c', '丙')");
        }
        assertThat(sync(token, meterMapping, "INCREMENTAL").path("insertedCount").asInt()).isEqualTo(1);
        assertThat(objects(token, "Meter").toString()).contains("\"c\"").contains("丙");

        mvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"other\",\"name\":\"其他\",\"adminUsername\":\"other\",\"adminPassword\":\"other-pass-1\",\"adminDisplayName\":\"其他管理员\"}"))
                .andExpect(status().isOk());
        String other = token("other", "other-pass-1");
        assertThat(objects(other, "Equipment")).isEmpty();
        String detail = mvc.perform(get("/api/v1/sync-runs/" + equipmentRun.path("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("开始全量同步");
    }

    @Test
    void injectedTextStaysDataAndIllegalIdentifiersAreRejected() throws Exception {
        String token = token("admin", "demo-admin");
        long branchId = branch(token, "sync-note");
        String root = "/api/v1/branches/" + branchId;
        String cqUid = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些备注").put("apiName", "whichNotes")));
        String noteUid = object(token, root, "备注", "Note", cqUid);
        String noteProperty = property(token, root, "内容", "noteText", noteUid);
        long datasourceId = datasource(token, "notes");
        long mappingId = save(token, branchId, datasourceId, noteUid, "src_note", "id", "UPDATED_AT", "updated_at", noteProperty, null, null, null);
        assertThat(sync(token, mappingId, "FULL").path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(objects(token, "Note").toString()).contains("MATCH (n) DETACH DELETE n").contains("\"n1\"");

        ObjectNode bad = mappingBody(datasourceId, noteUid, "src_note", "id", "UPDATED_AT", "updated_at", noteProperty, null, null, null);
        ((ObjectNode) bad.path("definition").path("fields").get(0)).put("column", "name`; DROP TABLE src_note");
        mvc.perform(put("/api/v1/branches/" + branchId + "/bindings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(bad.toString()))
                .andExpect(status().isBadRequest());
        assertThat(objects(token, "Note").toString()).contains("\"n1\"");
        mvc.perform(get("/api/v1/graph/objects").param("type", "Note`) DETACH DELETE n //").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertThat(objects(token, "Note").toString()).contains("\"n1\"");
    }

    private JsonNode sync(String token, long mappingId, String mode) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/bindings/" + mappingId + "/sync").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"" + mode + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode objects(String token, String type) throws Exception {
        String body = mvc.perform(get("/api/v1/graph/objects").param("type", type).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private long save(String token, long branchId, long datasourceId, String objectUid, String table, String pk, String kind, String column, String propertyUid, String linkUid, String linkColumn, String targetUid) throws Exception {
        MvcResult result = mvc.perform(put("/api/v1/branches/" + branchId + "/bindings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mappingBody(datasourceId, objectUid, table, pk, kind, column, propertyUid, linkUid, linkColumn, targetUid).toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private ObjectNode mappingBody(long datasourceId, String objectUid, String table, String pk, String kind, String column, String propertyUid, String linkUid, String linkColumn, String targetUid) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("datasourceId", datasourceId);
        ObjectNode definition = body.putObject("definition");
        definition.put("objectUid", objectUid);
        definition.put("table", table);
        definition.put("pkColumn", pk);
        definition.putObject("incremental").put("kind", kind).put("column", column);
        ArrayNode fields = definition.putArray("fields");
        fields.addObject().put("propertyUid", propertyUid).put("column", "name");
        ArrayNode links = definition.putArray("links");
        if (linkUid != null) {
            links.addObject().put("kind", "FOREIGN_KEY").put("linkUid", linkUid).put("column", linkColumn).put("targetObjectUid", targetUid);
        }
        return body;
    }

    private long datasource(String token, String name) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("dbType", "MYSQL");
        body.put("host", SOURCE.getHost());
        body.put("port", SOURCE.getMappedPort(3306));
        body.put("databaseName", SOURCE.getDatabaseName());
        body.put("username", SOURCE.getUsername());
        body.put("password", SOURCE.getPassword());
        return id(postJson(token, "/api/v1/datasources", body));
    }

    private long branch(String token, String code) throws Exception {
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", code).put("name", code).put("namespace", "https://example.com/ont/" + code + "#")));
        MvcResult branches = mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(branches.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
    }

    private String object(String token, String root, String name, String api, String cqUid) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api);
        body.putArray("cqUids").add(cqUid);
        return uid(postJson(token, root + "/object-types", body));
    }

    private String property(String token, String root, String name, String api, String ownerUid) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("displayName", name).put("apiName", api).put("ownerUid", ownerUid);
        body.putObject("valueType").put("kind", "BASIC").put("basic", "STRING");
        return uid(postJson(token, root + "/properties", body));
    }

    private String link(String token, String root, String stationUid, String equipmentUid, String cqUid) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("displayName", "安装于")
                .put("apiName", "installedAt")
                .put("sourceUid", equipmentUid)
                .put("targetUid", stationUid)
                .put("cardinality", "MANY_TO_ONE")
                .put("invertible", false);
        body.putArray("cqUids").add(cqUid);
        return uid(postJson(token, root + "/link-types", body));
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

    private String token(String username, String password) throws Exception {
        MvcResult captcha = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(captcha.getResponse().getContentAsString()).path("data");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", username);
        payload.put("password", password);
        payload.put("captchaId", body.path("captchaId").asText());
        payload.put("captchaCode", body.path("debugCode").asText());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
