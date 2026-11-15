package cn.muxicloud.mop.model.api;

import java.util.List;

/**
 * 语义模块读取当前分支元素的契约。IRI 只使用命名空间和 API 名。
 */
public interface BranchCatalog {
    CatalogSnapshot snapshot(long branchId);

    record CatalogElement(String uid, String type, String apiName, String ownerUid, String payload) {}

    record CatalogSnapshot(String namespace, List<CatalogElement> elements, long projectId) {}
}
