package cn.muxicloud.mop.model.api;

import java.util.Optional;

/**
 * 已发布本体快照的只读查询。运行时和语义模块只依赖这个契约，不依赖模型模块的内部实现。
 */
public interface PublishedModelLookup {
    Optional<String> findCompiledModel(long tenantId, long releaseId);
}
