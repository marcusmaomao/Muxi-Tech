package cn.muxicloud.mop.basic.service;

import cn.muxicloud.mop.basic.config.DatasourceSecretProperties;
import cn.muxicloud.mop.basic.crypto.SecretCipher;
import cn.muxicloud.mop.basic.datasource.JdbcProbe;
import cn.muxicloud.mop.basic.datasource.SqlDialect;
import cn.muxicloud.mop.basic.datasource.SqlIdentifiers;
import cn.muxicloud.mop.basic.entity.DatasourceEntity;
import cn.muxicloud.mop.basic.mapper.DatasourceMapper;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasourceService {
    private final DatasourceMapper datasourceMapper;
    private final List<SqlDialect> dialects;
    private final DatasourceSecretProperties secrets;

    public DatasourceService(DatasourceMapper datasourceMapper, List<SqlDialect> dialects, DatasourceSecretProperties secrets) {
        this.datasourceMapper = datasourceMapper;
        this.dialects = dialects;
        this.secrets = secrets;
    }

    public List<DatasourceView> list() {
        return datasourceMapper.selectList(new LambdaQueryWrapper<DatasourceEntity>().orderByDesc(DatasourceEntity::getId))
                .stream().map(DatasourceView::from).toList();
    }

    @Transactional
    public DatasourceView create(DatasourceInput input, AuthenticatedUser actor) {
        validate(input, true);
        if (datasourceMapper.selectCount(new LambdaQueryWrapper<DatasourceEntity>().eq(DatasourceEntity::getName, input.name().trim())) > 0) {
            throw new MopException(ErrorCode.VALIDATION, "数据源名称已存在", null);
        }
        DatasourceEntity entity = new DatasourceEntity();
        fill(entity, input, true);
        entity.setTenantId(actor.tenantId());
        entity.setCreatedBy(actor.userId());
        datasourceMapper.insert(entity);
        return DatasourceView.from(entity);
    }

    @Transactional
    public DatasourceView update(Long id, DatasourceInput input, AuthenticatedUser actor) {
        DatasourceEntity entity = required(id);
        validate(input, false);
        fill(entity, input, input.password() != null && !input.password().isBlank());
        entity.setUpdatedBy(actor.userId());
        datasourceMapper.updateById(entity);
        return DatasourceView.from(entity);
    }

    public void delete(Long id) {
        required(id);
        datasourceMapper.deleteById(id);
    }

    public JdbcProbe.ProbeResult test(Long id) {
        DatasourceEntity entity = required(id);
        SqlDialect dialect = dialect(entity.getDbType());
        return JdbcProbe.probe(dialect, entity.getHost(), entity.getPort(), entity.getDatabaseName(), entity.getUsername(), cipher().decrypt(entity.getPasswordCipher()));
    }

    public List<TableView> tables(Long id) {
        DatasourceEntity entity = required(id);
        SqlDialect dialect = dialect(entity.getDbType());
        try (Connection connection = open(entity, dialect)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<TableView> tables = new ArrayList<>();
            try (ResultSet rows = meta.getTables(dialect.catalog(entity.getDatabaseName()), dialect.schema(entity.getDatabaseName()), "%", new String[] {"TABLE", "VIEW"})) {
                while (rows.next()) {
                    tables.add(new TableView(rows.getString("TABLE_NAME"), rows.getString("TABLE_TYPE")));
                }
            }
            return tables;
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "无法读取表：" + JdbcProbe.shorten(ex.getMessage()), null);
        }
    }

    public List<ColumnView> columns(Long id, String table) {
        SqlIdentifiers.table(table);
        DatasourceEntity entity = required(id);
        SqlDialect dialect = dialect(entity.getDbType());
        try (Connection connection = open(entity, dialect)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<ColumnView> columns = new ArrayList<>();
            try (ResultSet rows = meta.getColumns(dialect.catalog(entity.getDatabaseName()), dialect.schema(entity.getDatabaseName()), table, "%")) {
                while (rows.next()) {
                    columns.add(new ColumnView(
                            rows.getString("COLUMN_NAME"),
                            rows.getString("TYPE_NAME"),
                            rows.getInt("COLUMN_SIZE"),
                            rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls));
                }
            }
            return columns;
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "无法读取字段：" + JdbcProbe.shorten(ex.getMessage()), null);
        }
    }

    private Connection open(DatasourceEntity entity, SqlDialect dialect) throws Exception {
        if (!dialect.driverPresent()) {
            throw new MopException(ErrorCode.VALIDATION, "JDBC 驱动不在 classpath：" + dialect.driverClass(), null);
        }
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(
                dialect.jdbcUrl(entity.getHost(), entity.getPort(), entity.getDatabaseName()),
                entity.getUsername(),
                cipher().decrypt(entity.getPasswordCipher()));
    }

    private void fill(DatasourceEntity entity, DatasourceInput input, boolean writePassword) {
        entity.setProjectId(input.projectId());
        entity.setName(input.name().trim());
        entity.setDbType(input.dbType().trim().toUpperCase());
        entity.setHost(input.host().trim());
        entity.setPort(input.port());
        entity.setDatabaseName(input.databaseName().trim());
        entity.setUsername(input.username().trim());
        if (writePassword) {
            entity.setPasswordCipher(cipher().encrypt(input.password()));
        }
    }

    private void validate(DatasourceInput input, boolean passwordRequired) {
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 128) {
            throw new MopException(ErrorCode.VALIDATION, "数据源名称不能为空", null);
        }
        dialect(input.dbType());
        SqlIdentifiers.host(input.host() == null ? null : input.host().trim());
        SqlIdentifiers.port(input.port() == null ? 0 : input.port());
        SqlIdentifiers.database(input.databaseName() == null ? null : input.databaseName().trim());
        if (input.username() == null || input.username().isBlank() || input.username().length() > 128) {
            throw new MopException(ErrorCode.VALIDATION, "用户名不能为空", null);
        }
        if (passwordRequired && (input.password() == null || input.password().isBlank())) {
            throw new MopException(ErrorCode.VALIDATION, "密码不能为空", null);
        }
    }

    private SqlDialect dialect(String type) {
        if (type == null) {
            throw new MopException(ErrorCode.VALIDATION, "不支持的数据源类型", null);
        }
        String code = type.trim().toUpperCase();
        return dialects.stream().filter(dialect -> dialect.code().equals(code)).findFirst()
                .orElseThrow(() -> new MopException(ErrorCode.VALIDATION, "不支持的数据源类型", null));
    }

    private DatasourceEntity required(Long id) {
        DatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private SecretCipher cipher() {
        return new SecretCipher(secrets.getSecret());
    }

    public record DatasourceInput(Long projectId, String name, String dbType, String host, Integer port, String databaseName, String username, String password) {}

    public record DatasourceView(Long id, Long projectId, String name, String dbType, String host, Integer port, String databaseName, String username) {
        static DatasourceView from(DatasourceEntity entity) {
            return new DatasourceView(entity.getId(), entity.getProjectId(), entity.getName(), entity.getDbType(), entity.getHost(), entity.getPort(), entity.getDatabaseName(), entity.getUsername());
        }
    }

    public record TableView(String name, String kind) {}

    public record ColumnView(String name, String typeName, Integer size, boolean nullable) {}
}
