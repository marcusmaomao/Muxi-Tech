package cn.muxicloud.mop.ai.gateway;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mop.ai.rate-limit", havingValue = "memory", matchIfMissing = true)
public class MemoryModelRateLimiter implements ModelRateLimiter {
    private final ConcurrentHashMap<String, AtomicInteger> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean allow(long tenantId, long modelId, int limit) {
        String key = tenantId + ":" + modelId + ":" + (System.currentTimeMillis() / 60_000L);
        int count = buckets.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        return count <= Math.max(limit, 1);
    }
}
