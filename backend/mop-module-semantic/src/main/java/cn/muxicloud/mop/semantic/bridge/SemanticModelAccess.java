package cn.muxicloud.mop.semantic.bridge;

import cn.muxicloud.mop.model.api.PublishedModelLookup;

/** 语义模块读取模型时只走 api 包。 */
public final class SemanticModelAccess {
    private final PublishedModelLookup lookup;

    public SemanticModelAccess(PublishedModelLookup lookup) {
        this.lookup = lookup;
    }

    public PublishedModelLookup lookup() {
        return lookup;
    }
}
