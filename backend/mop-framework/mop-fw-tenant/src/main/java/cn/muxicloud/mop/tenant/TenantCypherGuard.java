package cn.muxicloud.mop.tenant;

import java.util.regex.Pattern;

/**
 * Neo4j 租户守卫雏形。标签只接受模型白名单形态，查询必须带租户参数，不拼接外部字符串。
 */
public final class TenantCypherGuard {
    private static final Pattern LABEL = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private TenantCypherGuard() {
    }

    public static String matchByTenant(String label) {
        if (label == null || !LABEL.matcher(label).matches()) {
            throw new IllegalArgumentException("图标签不在白名单");
        }
        return "MATCH (n:`" + label + "`) WHERE n._t = $tenant RETURN n";
    }
}
