package cn.muxicloud.mop.basic.datasource;

public interface SqlDialect {
    String code();

    String driverClass();

    String jdbcUrl(String host, int port, String database);

    String catalog(String database);

    String schema(String database);

    default boolean driverPresent() {
        try {
            Class.forName(driverClass());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
