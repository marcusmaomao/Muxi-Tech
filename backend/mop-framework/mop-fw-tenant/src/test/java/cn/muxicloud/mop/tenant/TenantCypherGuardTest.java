package cn.muxicloud.mop.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TenantCypherGuardTest {
    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community");

    @Test
    void rejectsLabelsOutsideTheWhitelist() {
        assertThatThrownBy(() -> TenantCypherGuard.matchByTenant("T_设备`"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryOnlyReturnsTheCurrentTenant() {
        try (Driver driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", NEO4J.getAdminPassword()))) {
            try (Session session = driver.session()) {
                session.run("CREATE (:MopObject {name:'a', _t: 1}), (:MopObject {name:'b', _t: 2})").consume();
                String cypher = TenantCypherGuard.matchByTenant("MopObject");
                var names = session.run(cypher, org.neo4j.driver.Values.parameters("tenant", 1L))
                        .list(record -> record.get("n").get("name").asString());
                assertThat(names).containsExactly("a");
                var row = new java.util.HashMap<String, Object>();
                row.put("type", "Equip");
                row.put("pk", "1");
                row.put("rid", "ri.mop.1.object.Equip.1");
                row.put("propsJson", "{\"name\":\"MATCH (n) DETACH DELETE n\"}");
                session.run(TenantCypherGuard.MERGE_OBJECTS, org.neo4j.driver.Values.parameters("tenant", 1L, "rows", java.util.List.of(row))).consume();
                var stored = session.run(TenantCypherGuard.READ_OBJECTS, org.neo4j.driver.Values.parameters("tenant", 1L, "type", "Equip"))
                        .list(record -> record.get("propsJson").asString());
                assertThat(stored).containsExactly("{\"name\":\"MATCH (n) DETACH DELETE n\"}");
                assertThat(session.run(TenantCypherGuard.READ_OBJECTS, org.neo4j.driver.Values.parameters("tenant", 2L, "type", "Equip")).list()).isEmpty();
                assertThat(session.run(cypher, org.neo4j.driver.Values.parameters("tenant", 1L))
                        .list(record -> record.get("n").get("name").isNull() ? "" : record.get("n").get("name").asString())).contains("a");
            }
        }
    }
}
