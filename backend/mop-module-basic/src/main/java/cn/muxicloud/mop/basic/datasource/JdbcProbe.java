package cn.muxicloud.mop.basic.datasource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class JdbcProbe {
    private JdbcProbe() {
    }

    public record ProbeResult(boolean ok, String message, long elapsedMs) {
        public static ProbeResult success(long elapsedMs) {
            return new ProbeResult(true, "已连通", elapsedMs);
        }

        public static ProbeResult failure(String message, long elapsedMs) {
            return new ProbeResult(false, message, elapsedMs);
        }
    }

    public static ProbeResult probe(SqlDialect dialect, String host, int port, String database, String username, String password) {
        long started = System.nanoTime();
        if (!dialect.driverPresent()) {
            return ProbeResult.failure("JDBC 驱动不在 classpath：" + dialect.driverClass(), elapsed(started));
        }
        int previous = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(5);
        try (Connection connection = DriverManager.getConnection(dialect.jdbcUrl(host, port, database), username, password)) {
            if (!connection.isValid(5)) {
                return ProbeResult.failure("连接无效", elapsed(started));
            }
            return ProbeResult.success(elapsed(started));
        } catch (SQLException ex) {
            return ProbeResult.failure(shorten(ex.getMessage()), elapsed(started));
        } finally {
            DriverManager.setLoginTimeout(previous);
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    public static String shorten(String message) {
        if (message == null || message.isBlank()) {
            return "连接失败";
        }
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 180 ? oneLine : oneLine.substring(0, 180);
    }
}
