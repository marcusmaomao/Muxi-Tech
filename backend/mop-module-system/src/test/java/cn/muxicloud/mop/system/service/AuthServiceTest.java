package cn.muxicloud.mop.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.muxicloud.mop.audit.AuditRecorder;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.JwtService;
import cn.muxicloud.mop.system.dto.LoginResponse;
import cn.muxicloud.mop.system.entity.TenantEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.mapper.LoginLogMapper;
import cn.muxicloud.mop.system.mapper.TenantMapper;
import cn.muxicloud.mop.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private TenantMapper tenantMapper;
    @Mock private LoginLogMapper loginLogMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditRecorder auditRecorder;
    @Mock private cn.muxicloud.mop.system.service.CaptchaService captchaService;
    @Mock private cn.muxicloud.mop.system.service.RefreshTokenService refreshTokenService;
    @Mock private cn.muxicloud.mop.system.service.AccessService accessService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userMapper, tenantMapper, loginLogMapper, passwordEncoder, jwtService, auditRecorder,
                captchaService, refreshTokenService, accessService);
    }

    @Test
    void loginIssuesToken() {
        when(userMapper.selectOne(any())).thenReturn(user());
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(true);
        when(jwtService.issue(any())).thenReturn("token-1");
        when(jwtService.ttlSeconds()).thenReturn(7200L);
        when(refreshTokenService.issue(any())).thenReturn("refresh-1");
        when(tenantMapper.selectById(7L)).thenReturn(tenant());

        LoginResponse response = service.login("admin", "secret1", "cap", "ABCD", "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("token-1");
        assertThat(response.refreshToken()).isEqualTo("refresh-1");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().displayName()).isEqualTo("平台管理员");
        assertThat(response.user().tenantName()).isEqualTo("演示租户");
        verify(loginLogMapper).insert(any(cn.muxicloud.mop.system.entity.LoginLogEntity.class));
        verify(auditRecorder).record("AUTH", "LOGIN", "admin", "SUCCESS", null);
    }

    @Test
    void unknownUserIsRejected() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.login("admin", "secret1", "cap", "ABCD", "127.0.0.1"))
                .isInstanceOf(MopException.class)
                .hasMessage("用户名或密码错误");
        verify(jwtService, never()).issue(any());
        verify(loginLogMapper).insert(any(cn.muxicloud.mop.system.entity.LoginLogEntity.class));
    }

    @Test
    void wrongPasswordIsRejected() {
        when(userMapper.selectOne(any())).thenReturn(user());
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("admin", "secret1", "cap", "ABCD", null))
                .isInstanceOf(MopException.class);
        verify(jwtService, never()).issue(any());
    }

    private static UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setTenantId(7L);
        user.setUid("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        user.setUsername("admin");
        user.setDisplayName("平台管理员");
        user.setPasswordHash("hash");
        user.setStatus(1);
        return user;
    }

    private static TenantEntity tenant() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(7L);
        tenant.setName("演示租户");
        return tenant;
    }
}
