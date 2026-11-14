package cn.muxicloud.mop.basic.datasource;

import org.springframework.stereotype.Component;

@Component
public class MysqlDialect implements SqlDialect {
    @Override
    public String code() {
        return "MYSQL";
    }

    @Override
    public String driverClass() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public String jdbcUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=5000";
    }

    @Override
    public String catalog(String database) {
        return database;
    }

    @Override
    public String schema(String database) {
        return null;
    }
}
