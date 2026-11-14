package cn.muxicloud.mop.tenant;

public final class TenantIgnore {
    private static final ThreadLocal<Boolean> IGNORE = new ThreadLocal<>();

    private TenantIgnore() {
    }

    public static void ignore() {
        IGNORE.set(Boolean.TRUE);
    }

    public static void use() {
        IGNORE.remove();
    }

    public static boolean ignored() {
        return Boolean.TRUE.equals(IGNORE.get());
    }
}
