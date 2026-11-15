package cn.muxicloud.mop.semantic.quality;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.quality.QualityCheck.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityScoreTest {
    @Test
    void scoreComesFromOpenFindingsAndViolationsBlockRelease() {
        QualityScore.Scored clean = QualityScore.score(List.of());
        assertThat(clean.total()).isEqualByComparingTo("100.00");
        assertThat(QualityScore.blocks(List.of())).isFalse();
        QualityScore.Scored hit = QualityScore.score(List.of(new Finding("SEM_UNSAT", "SEMANTIC", "Violation", "c", "类不可满足")));
        assertThat(hit.total()).isLessThan(clean.total());
        assertThat(hit.dimensions().get("SEMANTIC")).isEqualByComparingTo("80");
        assertThat(QualityScore.blocks(List.of(new Finding("SEM_UNSAT", "SEMANTIC", "Violation", "c", "类不可满足")))).isTrue();
        assertThat(QualityScore.blocks(List.of(new Finding("STR_NO_DESC", "STRUCTURE", "Warning", "a", "缺少说明")))).isFalse();
    }

    @Test
    void directCycleIsReported() {
        CatalogSnapshot snapshot = new CatalogSnapshot("https://example.com/ont/q#", List.of(
                new CatalogElement("a", "OBJECT_TYPE", "ClassA", null, "{\"apiName\":\"ClassA\",\"displayName\":\"甲\",\"parentUids\":[\"b\"],\"cqUids\":[\"cq\"],\"description\":\"甲\"}"),
                new CatalogElement("b", "OBJECT_TYPE", "ClassB", null, "{\"apiName\":\"ClassB\",\"displayName\":\"乙\",\"parentUids\":[\"a\"],\"cqUids\":[\"cq\"],\"description\":\"乙\"}"),
                new CatalogElement("cq", "CQ", "which", null, "{\"apiName\":\"which\",\"displayName\":\"有哪些\"}")
        ), 1L);
        QualityContext context = new QualityContext(snapshot, null, List.of(), null, false, false, null);
        assertThat(QualityRules.checks().stream().filter(check -> "STR_CYCLE".equals(check.code())).findFirst().orElseThrow().run(context))
                .isNotEmpty();
    }
}
