package cn.muxicloud.mop.basic.datasource;

import org.springframework.stereotype.Component;

/**
 * 达梦 JDBC 坐标 {@code com.dameng:DmJdbcDriver18} 已在 Maven Central。
 * 类不在 classpath 时，连通测试返回明确失败，而不是在启动时中断。
 */
@Component
public class DamengDialect implements SqlDialect {
    @Override
    public String code() {
        return "DAMENG";
    }

    @Override
    public String driverClass() {
        return "dm.jdbc.driver.DmDriver";
    }

    @Override
    public String jdbcUrl(String host, int port, String database) {
        return "jdbc:dm://" + host + ":" + port + "?schema=" + database;
    }

    @Override
    public String catalog(String database) {
        return null;
    }

    @Override
    public String schema(String database) {
        return database;
    }
}
