package cn.muxicloud.mop.basic.datasource;

import org.springframework.stereotype.Component;

@Component
public class PostgresDialect implements SqlDialect {
    @Override
    public String code() {
        return "POSTGRESQL";
    }

    @Override
    public String driverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    public String jdbcUrl(String host, int port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?connectTimeout=5";
    }

    @Override
    public String catalog(String database) {
        return null;
    }

    @Override
    public String schema(String database) {
        return "public";
    }
}
