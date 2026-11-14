package cn.muxicloud.mop.system.service;

import cn.muxicloud.mop.audit.AuditRecorder;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.JwtService;
import cn.muxicloud.mop.system.dto.LoginResponse;
import cn.muxicloud.mop.system.dto.UserProfile;
import cn.muxicloud.mop.system.entity.LoginLogEntity;
import cn.muxicloud.mop.system.entity.TenantEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.mapper.LoginLogMapper;
import cn.muxicloud.mop.system.mapper.TenantMapper;
import cn.muxicloud.mop.system.mapper.UserMapper;
import cn.muxicloud.mop.web.trace.TraceIds;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditRecorder auditRecorder;

    public AuthService(
            UserMapper userMapper,
            TenantMapper tenantMapper,
            LoginLogMapper loginLogMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditRecorder auditRecorder) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(noRollbackFor = MopException.class)
    public LoginResponse login(String username, String password, String ip) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)
                .last("LIMIT 1"));
        boolean enabled = user != null && Integer.valueOf(1).equals(user.getStatus());
        boolean matched = user != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (!enabled || !matched) {
            writeLog(user, username, false, ip, "用户名或密码错误");
            auditRecorder.record("AUTH", "LOGIN", username, "FAIL", null);
            throw new MopException(ErrorCode.BAD_CREDENTIALS);
        }
        AuthenticatedUser principal = toPrincipal(user);
        String token = jwtService.issue(principal);
        writeLog(user, username, true, ip, "登录成功");
        auditRecorder.record("AUTH", "LOGIN", username, "SUCCESS", null);
        int expiresIn = (int) Math.min(Integer.MAX_VALUE, jwtService.ttlSeconds());
        return new LoginResponse(token, "Bearer", expiresIn, toProfile(user));
    }

    public UserProfile me(AuthenticatedUser principal) {
        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new MopException(ErrorCode.UNAUTHENTICATED);
        }
        return toProfile(user);
    }

    private AuthenticatedUser toPrincipal(UserEntity user) {
        return new AuthenticatedUser(user.getId(), user.getUid(), user.getUsername(), user.getDisplayName(), user.getTenantId());
    }

    private UserProfile toProfile(UserEntity user) {
        TenantEntity tenant = tenantMapper.selectById(user.getTenantId());
        String tenantName = tenant == null ? "" : tenant.getName();
        return new UserProfile(user.getUid(), user.getUsername(), user.getDisplayName(), user.getTenantId(), tenantName);
    }

    private void writeLog(UserEntity user, String username, boolean success, String ip, String message) {
        LoginLogEntity log = new LoginLogEntity();
        log.setTenantId(user == null || user.getTenantId() == null ? 0L : user.getTenantId());
        log.setUserId(user == null ? null : user.getId());
        log.setUsername(username);
        log.setSuccess(success ? 1 : 0);
        log.setMessage(message);
        log.setIp(trimIp(ip));
        log.setTraceId(TraceIds.current());
        log.setCreatedAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    private static String trimIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }
        String trimmed = ip.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }
}
