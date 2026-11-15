package cn.muxicloud.mop.basic.datasource;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 只把校验过的标识符放进 SQL。比较值和条数一律用占位符。
 */
public final class SourceQuery {
    private SourceQuery() {
    }

    public static String select(String table, List<String> columns, String orderColumn, String watermarkColumn, boolean filtered) {
        SqlIdentifiers.table(table);
        SqlIdentifiers.column(orderColumn);
        if (filtered) {
            SqlIdentifiers.column(watermarkColumn);
        }
        if (columns == null || columns.isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, "请选择字段", null);
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String column : columns) {
            SqlIdentifiers.column(column);
            selected.add(column);
        }
        selected.add(orderColumn);
        if (watermarkColumn != null && !watermarkColumn.isBlank()) {
            SqlIdentifiers.column(watermarkColumn);
            selected.add(watermarkColumn);
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        boolean first = true;
        for (String column : selected) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(quote(column));
            first = false;
        }
        sql.append(" FROM ").append(quote(table));
        if (filtered) {
            sql.append(" WHERE ").append(quote(watermarkColumn)).append(" > ?");
        }
        sql.append(" ORDER BY ").append(quote(orderColumn));
        sql.append(" LIMIT ?");
        return sql.toString();
    }

    public static String max(String table, String column) {
        SqlIdentifiers.table(table);
        SqlIdentifiers.column(column);
        return "SELECT MAX(" + quote(column) + ") AS watermark FROM " + quote(table);
    }

    public static String junction(String table, String sourceColumn, String targetColumn) {
        SqlIdentifiers.table(table);
        SqlIdentifiers.column(sourceColumn);
        SqlIdentifiers.column(targetColumn);
        return "SELECT " + quote(targetColumn) + " AS target_pk FROM " + quote(table) + " WHERE " + quote(sourceColumn) + " = ?";
    }

    private static String quote(String identifier) {
        return "`" + identifier + "`";
    }
}
