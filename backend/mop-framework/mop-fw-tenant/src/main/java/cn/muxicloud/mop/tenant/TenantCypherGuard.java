package cn.muxicloud.mop.tenant;

import java.util.regex.Pattern;

/**
 * Neo4j 租户守卫雏形。标签只接受模型白名单形态，查询必须带租户参数，不拼接外部字符串。
 */
public final class TenantCypherGuard {
    private static final Pattern LABEL = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /**
     * 同步写入只允许这几条常量语句。类型、主键和属性都是参数，调用方不能传入 Cypher 文本。
     */
    public static final String MERGE_OBJECTS = """
            UNWIND $rows AS row
            OPTIONAL MATCH (existing:MopObject { _t: $tenant, _type: row.type, _pk: row.pk })
            WITH row, existing
            MERGE (n:MopObject { _t: $tenant, _type: row.type, _pk: row.pk })
            SET n._rid = row.rid, n._src = 'SYNC', n.propsJson = row.propsJson, n._updatedAt = datetime()
            RETURN sum(CASE WHEN existing IS NULL THEN 1 ELSE 0 END) AS inserted,
                   sum(CASE WHEN existing IS NULL THEN 0 ELSE 1 END) AS updated
            """;

    public static final String MERGE_LINKS = """
            UNWIND $links AS link
            MATCH (a:MopObject { _t: $tenant, _type: link.fromType, _pk: link.fromPk })
            MATCH (b:MopObject { _t: $tenant, _type: link.toType, _pk: link.toPk })
            MERGE (a)-[r:MOP_LINK { _t: $tenant, _link: link.linkType, _key: link.key }]->(b)
            SET r._src = 'SYNC'
            RETURN count(r) AS linked
            """;

    public static final String READ_OBJECTS = """
            MATCH (n:MopObject)
            WHERE n._t = $tenant AND n._type = $type
            RETURN n._pk AS pk, n.propsJson AS propsJson
            ORDER BY pk
            """;

    private TenantCypherGuard() {
    }

    public static void requireApiName(String name) {
        if (name == null || !LABEL.matcher(name).matches()) {
            throw new IllegalArgumentException("图标签不在白名单");
        }
    }

    public static String matchByTenant(String label) {
        requireApiName(label);
        return "MATCH (n:`" + label + "`) WHERE n._t = $tenant RETURN n";
    }
}
