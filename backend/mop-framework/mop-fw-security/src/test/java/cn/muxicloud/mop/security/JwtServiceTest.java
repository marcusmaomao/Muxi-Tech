package cn.muxicloud.mop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void roundTrip() {
        JwtService service = new JwtService("local-dev-only-jwt-secret-change-me", 3600);
        AuthenticatedUser user = new AuthenticatedUser(42L, "01ARZ3NDEKTSV4RRFFQ69G5FAV", "admin", "平台管理员", 7L);
        String token = service.issue(user);
        assertThat(service.parse(token)).contains(user);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService("local-dev-only-jwt-secret-change-me", 3600);
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MOP_JWT_SECRET");
    }
}
