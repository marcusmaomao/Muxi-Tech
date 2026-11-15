package cn.muxicloud.mop.model.version;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergePlannerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void differentFieldsMergeAndTheSameFieldConflicts() {
        ObjectNode base = element("主设备", "主干名");
        ObjectNode source = element("功能分支", "主干名");
        ObjectNode target = element("主设备", "目标名");
        MergePlanner.Plan plan = MergePlanner.plan(Map.of("u1", side(base)), Map.of("u1", side(source)), Map.of("u1", side(target)), Map.of());
        assertThat(plan.conflicts()).isEmpty();
        assertThat(plan.changes()).hasSize(1);
        assertThat(plan.changes().get(0).payload().path("description").asText()).isEqualTo("功能分支");
        assertThat(plan.changes().get(0).payload().path("displayName").asText()).isEqualTo("目标名");

        ObjectNode both = element("另一边", "目标名");
        MergePlanner.Plan clash = MergePlanner.plan(Map.of("u1", side(base)), Map.of("u1", side(source)), Map.of("u1", side(both)), Map.of());
        assertThat(clash.conflicts()).hasSize(1);
        assertThat(clash.conflicts().get(0).paths()).contains("/description");
        assertThat(clash.changes()).isEmpty();
    }

    @Test
    void deleteAgainstEditConflictsUntilTheSourceIsChosen() {
        ObjectNode base = element("主设备", "变压器");
        ObjectNode edited = element("已修改", "变压器");
        MergePlanner.Plan clash = MergePlanner.plan(Map.of("u1", side(base)), Map.of(), Map.of("u1", side(edited)), Map.of());
        assertThat(clash.conflicts()).hasSize(1);
        assertThat(clash.conflicts().get(0).reason()).isEqualTo("DELETE");

        MergePlanner.Plan chosen = MergePlanner.plan(
                Map.of("u1", side(base)), Map.of("u1", side(edited)), Map.of("u1", side(base)), Map.of("u1", "SOURCE"));
        assertThat(chosen.conflicts()).isEmpty();
        assertThat(chosen.changes()).hasSize(1);
        assertThat(chosen.changes().get(0).payload().path("description").asText()).isEqualTo("已修改");
    }

    private MergePlanner.ElementSide side(ObjectNode payload) {
        return new MergePlanner.ElementSide("OBJECT_TYPE", "Transformer", payload);
    }

    private ObjectNode element(String description, String name) {
        return json.createObjectNode().put("displayName", name).put("apiName", "Transformer").put("description", description);
    }
}
