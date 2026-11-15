package cn.muxicloud.mop.basic.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.muxicloud.mop.common.error.MopException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceQueryTest {
    @Test
    void valuesStayOutOfTheSql() {
        String sql = SourceQuery.select("src_equipment", List.of("name"), "id", "updated_at", true);
        assertThat(sql).isEqualTo("SELECT `name`, `id`, `updated_at` FROM `src_equipment` WHERE `updated_at` > ? ORDER BY `id` LIMIT ?");
        assertThat(sql).doesNotContain("2026-01-01");
        assertThat(SourceQuery.junction("src_eq_tag", "eq_id", "tag_id")).contains("= ?").doesNotContain("e1");
    }

    @Test
    void rejectsIdentifiersThatCouldChangeTheStatement() {
        assertThatThrownBy(() -> SourceQuery.select("src_equipment", List.of("name`; DROP TABLE src_equipment"), "id", null, false))
                .isInstanceOf(MopException.class);
        assertThatThrownBy(() -> SourceQuery.max("src_equipment", "id OR 1=1"))
                .isInstanceOf(MopException.class);
    }
}
