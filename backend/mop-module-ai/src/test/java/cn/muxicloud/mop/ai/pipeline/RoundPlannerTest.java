package cn.muxicloud.mop.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoundPlannerTest {
    private final RoundPlanner planner = new RoundPlanner();

    @Test
    void keepsCandidatesWhoseEvidenceIsInTheSource() {
        String evidence = "变压器是电网的主设备";
        String output = "{\"candidates\":[{\"type\":\"CQ\",\"apiName\":\"whichDevices\",\"displayName\":\"有哪些设备\",\"evidence\":\"变压器是电网的主设备\",\"confidence\":0.9},{\"type\":\"TERM\",\"apiName\":\"madeUp\",\"displayName\":\"虚构\",\"evidence\":\"原文里没有这句话\",\"confidence\":0.2}]}";
        assertThat(planner.parse(output, evidence)).extracting(RoundPlanner.Draft::apiName).containsExactly("whichDevices");
        assertThat(planner.prompt("R1", evidence, "")).contains("轮次：1").contains(evidence);
    }

    @Test
    void rejectsOutputThatIsNotJson() {
        assertThatThrownBy(() -> planner.parse("not-json", "原文")).isInstanceOf(IllegalArgumentException.class);
    }
}
