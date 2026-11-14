package cn.muxicloud.mop.tenant;

/**
 * 当前请求的租户。MyBatis 租户插件和 Neo4j 守卫在 M1 接入，这里只保留线程上下文。
 */
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
