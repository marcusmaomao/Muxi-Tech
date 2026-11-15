package cn.muxicloud.mop.model.inherit;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.model.inherit.EffectiveProperties.NameConflict;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.PropNode;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.PropertyView;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.Report;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.TypeNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectivePropertiesTest {
    @Test
    void twoParentsContributeDifferentPropertiesAndADiamondKeepsOne() {
        List<TypeNode> types = List.of(
                type("asset", "Asset", List.of()),
                type("line", "Line", List.of()),
                type("combo", "Combo", List.of("asset", "line")),
                type("child", "Child", List.of("combo")));
        List<PropNode> properties = List.of(
                prop("voltage", "ratedVoltage", "额定电压", "asset", false),
                prop("length", "lengthKm", "长度", "line", false));
        Report combo = EffectiveProperties.of("combo", types, properties);
        assertThat(combo.conflicts()).isEmpty();
        assertThat(combo.properties()).extracting(PropertyView::apiName).containsExactlyInAnyOrder("ratedVoltage", "lengthKm");
        assertThat(combo.properties()).allMatch(property -> "INHERITED".equals(property.origin()));
        assertThat(combo.properties()).extracting(PropertyView::sourceTypeApiName).contains("Asset", "Line");

        Report child = EffectiveProperties.of("child", types, properties);
        assertThat(child.conflicts()).isEmpty();
        assertThat(child.properties()).extracting(PropertyView::uid).containsExactlyInAnyOrder("voltage", "length");
    }

    @Test
    void sameNameFromTwoParentsConflictsUntilTheChildOverrides() {
        List<TypeNode> types = List.of(
                type("a", "Asset", List.of()),
                type("b", "Line", List.of()),
                type("c", "Combo", List.of("a", "b")));
        List<PropNode> clash = List.of(
                prop("p1", "name", "名称甲", "a", false),
                prop("p2", "name", "名称乙", "b", false));
        Report conflicted = EffectiveProperties.of("c", types, clash);
        assertThat(conflicted.conflicts()).extracting(NameConflict::apiName).containsExactly("name");
        assertThat(conflicted.conflicts().get(0).sourceTypeApiNames()).containsExactlyInAnyOrder("Asset", "Line");

        List<PropNode> resolved = List.of(
                prop("p1", "name", "名称甲", "a", false),
                prop("p2", "name", "名称乙", "b", false),
                prop("p3", "name", "组合名", "c", true));
        Report ok = EffectiveProperties.of("c", types, resolved);
        assertThat(ok.conflicts()).isEmpty();
        assertThat(ok.properties()).extracting(PropertyView::origin).containsExactly("OWN");
    }

    @Test
    void diamondDoesNotTreatTheSamePropertyAsAConflict() {
        List<TypeNode> types = List.of(
                type("a", "Asset", List.of()),
                type("b", "Box", List.of("a")),
                type("c", "Device", List.of("a")),
                type("d", "Combo", List.of("b", "c")));
        List<PropNode> properties = List.of(prop("code", "assetCode", "编码", "a", false));
        Report report = EffectiveProperties.of("d", types, properties);
        assertThat(report.conflicts()).isEmpty();
        assertThat(report.properties()).extracting(PropertyView::uid).containsExactly("code");
        assertThat(report.properties().get(0).sourceTypeApiName()).isEqualTo("Asset");
    }

    private static TypeNode type(String uid, String api, List<String> parents) {
        return new TypeNode(uid, api, api, parents);
    }

    private static PropNode prop(String uid, String api, String name, String owner, boolean override) {
        return new PropNode(uid, api, name, owner, override);
    }
}
