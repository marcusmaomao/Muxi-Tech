package cn.muxicloud.mop.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UlidsTest {

    @Test
    void zeroBytesEncodeToZeroUlid() {
        assertThat(Ulids.encode(new byte[16])).isEqualTo("00000000000000000000000000");
    }

    @Test
    void newUlidUsesCrockfordAlphabet() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String ulid = Ulids.newUlid();
            assertThat(ulid).hasSize(26);
            assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}");
            seen.add(ulid);
        }
        assertThat(seen).hasSize(20);
    }
}
