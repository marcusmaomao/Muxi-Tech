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
            }
        }
    }
}
