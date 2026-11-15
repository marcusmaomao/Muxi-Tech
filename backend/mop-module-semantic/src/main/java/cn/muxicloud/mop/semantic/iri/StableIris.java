package cn.muxicloud.mop.semantic.iri;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;

/**
 * 稳定 IRI 只由命名空间和 API 名组成，不包含 ULID 或数据库自增 ID。
 */
public final class StableIris {
    private StableIris() {
    }

    public static String join(String namespace, String localName) {
        if (namespace == null || !(namespace.endsWith("#") || namespace.endsWith("/"))) {
            throw new MopException(ErrorCode.VALIDATION, "命名空间必须是以 # 或 / 结尾的绝对 URI", null);
        }
        if (localName == null || localName.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "API 名不能为空", null);
        }
        return namespace + localName;
    }
}
