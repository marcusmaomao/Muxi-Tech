package cn.muxicloud.mop.basic.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.muxicloud.mop.basic.crypto.SecretCipher;
import cn.muxicloud.mop.basic.parse.TextExtractor;
import cn.muxicloud.mop.common.error.MopException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JdbcAssetsTest {
    @Test
    void cipherRoundTrip() {
        SecretCipher cipher = new SecretCipher("integration-datasource-secret");
        assertThat(cipher.decrypt(cipher.encrypt("pg-secret-value"))).isEqualTo("pg-secret-value");
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        assertThatThrownBy(() -> SqlIdentifiers.table("users;drop")).isInstanceOf(MopException.class);
        assertThatThrownBy(() -> SqlIdentifiers.database("../db")).isInstanceOf(MopException.class);
        assertThatThrownBy(() -> SqlIdentifiers.host("host/path")).isInstanceOf(MopException.class);
    }

    @Test
    void dialectUrlsStayOnTheChosenDriver() {
        assertThat(new MysqlDialect().jdbcUrl("db.internal", 3306, "mop")).startsWith("jdbc:mysql://db.internal:3306/mop");
        assertThat(new PostgresDialect().jdbcUrl("db.internal", 5432, "mop")).isEqualTo("jdbc:postgresql://db.internal:5432/mop?connectTimeout=5");
        assertThat(new DamengDialect().jdbcUrl("db.internal", 5236, "SYSDBA")).isEqualTo("jdbc:dm://db.internal:5236?schema=SYSDBA");
        assertThat(new DamengDialect().driverPresent()).isTrue();
    }

    @Test
    void missingDriverFailsWithoutOpeningASocket() {
        JdbcProbe.ProbeResult result = JdbcProbe.probe(new MissingDialect(), "127.0.0.1", 1, "db", "user", "secret");
        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("classpath");
        assertThat(result.elapsedMs()).isLessThan(1000);
    }

    @Test
    void damengDriverRejectsAClosedPortQuickly() {
        JdbcProbe.ProbeResult result = JdbcProbe.probe(new DamengDialect(), "127.0.0.1", 1, "SYSDBA", "SYSDBA", "secret");
        assertThat(result.ok()).isFalse();
        assertThat(result.message()).doesNotContain("classpath");
        assertThat(result.elapsedMs()).isLessThan(5000);
    }

    @Test
    void tikaReadsPlainText() {
        TextExtractor.Extracted extracted = new TextExtractor().extract("变压器检修记录".getBytes(StandardCharsets.UTF_8), "note.txt");
        assertThat(extracted.status()).isEqualTo("OK");
        assertThat(extracted.text()).contains("变压器检修记录");
    }

    private static final class MissingDialect implements SqlDialect {
        @Override
        public String code() {
            return "MISSING";
        }

        @Override
        public String driverClass() {
            return "cn.muxicloud.mop.basic.NoSuchDriver";
        }

        @Override
        public String jdbcUrl(String host, int port, String database) {
            return "jdbc:missing://" + host;
        }

        @Override
        public String catalog(String database) {
            return null;
        }

        @Override
        public String schema(String database) {
            return null;
        }
    }
}
