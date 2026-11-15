package cn.muxicloud.mop.ai.gateway;

public interface ModelRateLimiter {
    boolean allow(long tenantId, long modelId, int limit);
}
