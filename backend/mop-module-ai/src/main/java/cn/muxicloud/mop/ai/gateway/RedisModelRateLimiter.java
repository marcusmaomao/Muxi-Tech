package cn.muxicloud.mop.ai.gateway;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mop.ai.rate-limit", havingValue = "redis")
public class RedisModelRateLimiter implements ModelRateLimiter {
    private final StringRedisTemplate redis;

    public RedisModelRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(long tenantId, long modelId, int limit) {
        String key = "mop:llm:" + tenantId + ":" + modelId + ":" + (System.currentTimeMillis() / 60_000L);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(70));
        }
        return count != null && count <= Math.max(limit, 1);
    }
}
