package cn.muxicloud.mop.server.version;

import cn.muxicloud.mop.model.api.BranchViewCache;
import cn.muxicloud.mop.model.entity.ElementEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mop.model.view-cache", havingValue = "redis")
public class RedisBranchViewCache implements BranchViewCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RedisBranchViewCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public List<ElementEntity> get(String key) {
        try {
            String raw = redis.opsForValue().get(redisKey(key));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return json.readValue(raw, new TypeReference<List<ElementEntity>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void put(String key, List<ElementEntity> elements) {
        try {
            redis.opsForValue().set(redisKey(key), json.writeValueAsString(elements), Duration.ofHours(6));
        } catch (Exception ignored) {
            // 缓存不可用时直接读库，演示环境不因此中断。
        }
    }

    private static String redisKey(String key) {
        return "mop:branch-view:" + key;
    }
}
