package cn.muxicloud.mop.semantic.cq;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.semantic.cq.CqCoverage.CaseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CqExecutionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SparqlRunner sparql = new SparqlRunner();

    @Test
    void countAssertionPassesOnFixtureTriples() throws Exception {
        String namespace = "https://example.com/ont/cq#";
        SparqlRunner.Result result = sparql.run("""
                PREFIX ex: <https://example.com/ont/cq#>
                SELECT ?part WHERE { ?product ex:composedOf ?part }
                """, "", """
                @prefix ex: <https://example.com/ont/cq#> .
                ex:phone ex:composedOf ex:screen .
                """);
        assertThat(CqAssertions.check(mapper.readTree("{\"kind\":\"COUNT\",\"op\":\">=\",\"value\":1}"), result.ask(), result.askValue(), result.rows())).isNull();
        assertThat(CqCoverage.mentions(result.rows().toString(), namespace, "composedOf")).isFalse();
    }

    @Test
    void resultEqualsIgnoresOrder() throws Exception {
        var rows = List.of(java.util.Map.of("name", "b"), java.util.Map.of("name", "a"));
        String failure = CqAssertions.check(mapper.readTree("{\"kind\":\"RESULT_EQUALS\",\"rows\":[{\"name\":\"a\"},{\"name\":\"b\"}]}"), false, null, rows);
        assertThat(failure).isNull();
    }

    @Test
    void coverageIgnoresManualAcceptStatus() {
        List<CatalogElement> elements = List.of(
                new CatalogElement("cq1", "CQ", "parts", null, "{\"displayName\":\"组成\",\"apiName\":\"parts\",\"acceptStatus\":\"已覆盖\"}"),
                new CatalogElement("cq2", "CQ", "other", null, "{\"displayName\":\"另一个\",\"apiName\":\"other\",\"acceptStatus\":\"已覆盖\"}"),
                new CatalogElement("product", "OBJECT_TYPE", "Product", null, "{\"apiName\":\"Product\"}"),
                new CatalogElement("link", "LINK_TYPE", "composedOf", null, "{\"apiName\":\"composedOf\"}"));
        CqCoverage.Report none = CqCoverage.calculate("https://example.com/ont/cq#", elements, List.of());
        assertThat(none.cqPassed()).isZero();
        assertThat(none.cqTotal()).isEqualTo(2);
        CqCoverage.Report passed = CqCoverage.calculate("https://example.com/ont/cq#", elements, List.of(
                new CaseResult("cq1", true, "SELECT * WHERE { ?s <https://example.com/ont/cq#composedOf> ?o ; a <https://example.com/ont/cq#Product> }", Set.of())));
        assertThat(passed.cqPassed()).isEqualTo(1);
        assertThat(passed.elementPassed()).isEqualTo(2);
        CqCoverage.Report failed = CqCoverage.calculate("https://example.com/ont/cq#", elements, List.of(
                new CaseResult("cq1", false, "composedOf Product", Set.of())));
        assertThat(failed.cqPassed()).isZero();
        assertThat(failed.elementPassed()).isZero();
        assertThat(failed.gateOpen(1)).isFalse();
    }
}
