package cn.muxicloud.mop.semantic.reason;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.ClassNode;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.Classification;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElkClassifierTest {
    private static final String NAMESPACE = "https://example.com/ont/reason#";
    private final ElkClassifier classifier = new ElkClassifier();

    @Test
    void disjointParentsMakeTheChildUnsatisfiable() {
        Classification classification = classifier.classify(new CatalogSnapshot(NAMESPACE, List.of(
                element("a", "OBJECT_TYPE", "ClassA", null, "{\"displayName\":\"甲类\",\"apiName\":\"ClassA\"}"),
                element("b", "OBJECT_TYPE", "ClassB", null, "{\"displayName\":\"乙类\",\"apiName\":\"ClassB\"}"),
                element("c", "OBJECT_TYPE", "Clash", null, "{\"displayName\":\"冲突类\",\"apiName\":\"Clash\",\"parentUids\":[\"a\",\"b\"]}"),
                element("d", "AXIOM", "classADisjointClassB", null, "{\"kind\":\"DISJOINT\",\"subjectUid\":\"a\",\"objectUid\":\"b\"}")
        ), 1L));
        assertThat(classification.consistent()).isTrue();
        assertThat(classification.unsatisfiable()).extracting(type -> type.apiName()).containsExactly("Clash");
        assertThat(classification.unsatisfiable().get(0).displayName()).isEqualTo("冲突类");
    }

    @Test
    void includeSubtypesExpandsTheClassifiedDescendants() {
        Classification classification = classifier.classify(new CatalogSnapshot(NAMESPACE, List.of(
                element("device", "OBJECT_TYPE", "Device", null, "{\"displayName\":\"设备\",\"apiName\":\"Device\"}"),
                element("transformer", "OBJECT_TYPE", "Transformer", null, "{\"displayName\":\"变压器\",\"apiName\":\"Transformer\",\"parentUids\":[\"device\"]}"),
                element("breaker", "OBJECT_TYPE", "Breaker", null, "{\"displayName\":\"断路器\",\"apiName\":\"Breaker\",\"parentUids\":[\"device\"]}")
        ), 1L));
        assertThat(classification.unsatisfiable()).isEmpty();
        ClassNode device = classification.types().stream().filter(type -> type.apiName().equals("Device")).findFirst().orElseThrow();
        assertThat(device.descendants()).contains("Device", "Transformer", "Breaker");
        assertThat(classifier.expand(classification, "Device", false)).containsExactly("Device");
        assertThat(classifier.expand(classification, "Device", true)).contains("Transformer", "Breaker");
        ClassNode transformer = classification.types().stream().filter(type -> type.apiName().equals("Transformer")).findFirst().orElseThrow();
        assertThat(transformer.directSuperClasses()).contains("Device");
    }

    private static CatalogElement element(String uid, String type, String apiName, String ownerUid, String payload) {
        return new CatalogElement(uid, type, apiName, ownerUid, payload);
    }
}
