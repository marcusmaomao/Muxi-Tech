package cn.muxicloud.mop.common.cq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CqCypherPolicyTest {
    @Test
    void readQueryWithTenantParameterIsAllowed() {
        assertThatCode(() -> CqCypherPolicy.check("""
                MATCH (n:MopObject)
                WHERE n._t = $tenant AND n._type = 'Product'
                RETURN n._pk AS pk
                """)).doesNotThrowAnyException();
    }

    @Test
    void writeClauseAndMissingTenantAreRejected() {
        assertThatThrownBy(() -> CqCypherPolicy.check("MATCH (n) WHERE n._t = $tenant DELETE n RETURN n"))
                .hasMessageContaining("DELETE");
        assertThatThrownBy(() -> CqCypherPolicy.check("MATCH (n) WHERE n.name = 'DELETE' RETURN n"))
                .hasMessageContaining("$tenant");
        assertThatThrownBy(() -> CqCypherPolicy.check("MATCH (n) WHERE n._t = 1 RETURN n._pk"))
                .hasMessageContaining("$tenant");
    }

    @Test
    void keywordsInsideStringsAndCommentsDoNotCount() {
        assertThatCode(() -> CqCypherPolicy.check("""
                // DELETE should stay in the comment
                MATCH (n:MopObject) WHERE n._t = $tenant AND n.name = 'CREATE table'
                RETURN n._pk
                """)).doesNotThrowAnyException();
    }
}
