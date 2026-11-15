package cn.muxicloud.mop.model.api;

import cn.muxicloud.mop.model.entity.ElementEntity;
import java.util.List;

/**
 * 分支视图缓存。键里带修订号，新提交自然不再命中旧快照。
 */
public interface BranchViewCache {
    List<ElementEntity> get(String key);

    void put(String key, List<ElementEntity> elements);
}
