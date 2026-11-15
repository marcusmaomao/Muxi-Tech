package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.model.api.BranchViewCache;
import cn.muxicloud.mop.model.entity.ElementEntity;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mop.model.view-cache", havingValue = "memory", matchIfMissing = true)
public class MemoryBranchViewCache implements BranchViewCache {
    private final ConcurrentHashMap<String, List<ElementEntity>> data = new ConcurrentHashMap<>();

    @Override
    public List<ElementEntity> get(String key) {
        List<ElementEntity> hit = data.get(key);
        return hit == null ? null : List.copyOf(hit);
    }

    @Override
    public void put(String key, List<ElementEntity> elements) {
        if (data.size() > 128) {
            data.clear();
        }
        data.put(key, List.copyOf(elements));
    }
}
