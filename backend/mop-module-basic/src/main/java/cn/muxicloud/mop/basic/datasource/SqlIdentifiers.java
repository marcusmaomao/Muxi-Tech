package cn.muxicloud.mop.basic.datasource;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;

public final class SqlIdentifiers {
    private static final String TOKEN = "[A-Za-z0-9_-]{1,128}";

    private SqlIdentifiers() {
    }

    public static void host(String host) {
        if (host == null || !host.matches("[A-Za-z0-9._-]{1,255}")) {
            throw new MopException(ErrorCode.VALIDATION, "主机名不合法", null);
        }
    }

    public static void database(String database) {
        if (database == null || !database.matches(TOKEN)) {
            throw new MopException(ErrorCode.VALIDATION, "数据库名只能包含字母、数字、下划线和短横线", null);
        }
    }

    public static void table(String table) {
        if (table == null || !table.matches("[A-Za-z0-9_]{1,128}")) {
            throw new MopException(ErrorCode.VALIDATION, "表名不合法", null);
        }
    }

    public static void port(int port) {
        if (port < 1 || port > 65535) {
            throw new MopException(ErrorCode.VALIDATION, "端口必须在 1 到 65535 之间", null);
        }
    }
}
