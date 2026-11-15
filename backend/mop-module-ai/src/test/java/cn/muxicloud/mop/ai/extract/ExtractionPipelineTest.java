package cn.muxicloud.mop.ai.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtractionPipelineTest {
    private final ExtractionPipeline pipeline = new ExtractionPipeline();
    private final ExtractionPipeline.Index index = new ExtractionPipeline.Index(
            Map.of("Equipment", new ExtractionPipeline.TypeSpec("Equipment", Set.of("equipmentName"))),
            Map.of("installedAt", new ExtractionPipeline.LinkSpec("installedAt", "Equipment", "Substation")));

    @Test
    void keepsAQuoteThatAppearsInTheSource() {
        String evidence = "一号变压器安装在城东站";
        String output = "{\"entities\":[{\"type\":\"Equipment\",\"name\":\"一号变压器\",\"props\":{\"equipmentName\":\"一号变压器\"},\"evidence\":\"一号变压器安装在城东站\",\"confidence\":0.9},{\"type\":\"Substation\",\"name\":\"城东站\",\"props\":{},\"evidence\":\"一号变压器安装在城东站\",\"confidence\":0.8}],\"links\":[]}";
        assertThat(pipeline.interpret(output, evidence, wider())).filteredOn(item -> "REVIEW".equals(item.state())).hasSize(2);
    }

    @Test
    void quarantinesUnknownTypesAndMissingEvidence() {
        String evidence = "一号变压器安装在城东站";
        String output = "{\"entities\":[{\"type\":\"Ghost\",\"name\":\"幽灵\",\"props\":{},\"evidence\":\"原文没有\",\"confidence\":0.2}],\"links\":[]}";
        assertThat(pipeline.interpret(output, evidence, wider())).allMatch(item -> "QUARANTINE".equals(item.state()));
        assertThat(pipeline.prompt(pipeline.schemaText(index), evidence)).contains("抽取：1").contains(evidence);
    }

    private ExtractionPipeline.Index wider() {
        return new ExtractionPipeline.Index(
                Map.of(
                        "Equipment", new ExtractionPipeline.TypeSpec("Equipment", Set.of("equipmentName")),
                        "Substation", new ExtractionPipeline.TypeSpec("Substation", Set.of())),
                Map.of("installedAt", new ExtractionPipeline.LinkSpec("installedAt", "Equipment", "Substation")));
    }
}
