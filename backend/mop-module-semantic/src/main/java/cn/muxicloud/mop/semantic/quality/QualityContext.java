package cn.muxicloud.mop.semantic.quality;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.Classification;
import java.util.List;

public record QualityContext(
        CatalogSnapshot snapshot,
        Classification classification,
        List<String> shapeErrors,
        Boolean cqGateOpen,
        boolean cqTestsPresent,
        boolean cqRunPresent,
        Double elementCoverage) {

    public List<CatalogElement> ofType(String type) {
        return snapshot.elements().stream().filter(element -> type.equals(element.type())).toList();
    }
}
