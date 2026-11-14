package cn.muxicloud.mop.runtime.bridge;

import cn.muxicloud.mop.model.api.PublishedModelLookup;

/** 运行时只读已发布快照，不依赖模型模块的写入实现。 */
public final class RuntimeModelAccess {
    private final PublishedModelLookup lookup;

    public RuntimeModelAccess(PublishedModelLookup lookup) {
        this.lookup = lookup;
    }

    public PublishedModelLookup lookup() {
        return lookup;
    }
}
