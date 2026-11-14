package cn.muxicloud.mop.system.service;

import cn.muxicloud.mop.audit.AuditRecorder;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.JwtService;
import cn.muxicloud.mop.security.ParsedToken;
import cn.muxicloud.mop.system.dto.LoginResponse;
import cn.muxicloud.mop.system.dto.MenuNode;
import cn.muxicloud.mop.system.dto.SessionProfile;
import cn.muxicloud.mop.system.entity.LoginLogEntity;
import cn.muxicloud.mop.system.entity.MenuEntity;
import cn.muxicloud.mop.system.entity.TenantEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.mapper.LoginLogMapper;
import cn.muxicloud.mop.system.mapper.TenantMapper;
import cn.muxicloud.mop.system.mapper.UserMapper;
import cn.muxicloud.mop.tenant.TenantContext;
import cn.muxicloud.mop.tenant.TenantIgnore;
import cn.muxicloud.mop.web.trace.TraceIds;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final CaptchaService captchaService;
    private final RefreshTokenService refreshTokenService;
    private final AccessService accessService;

    public AuthService(
            UserMapper userMapper,
            TenantMapper tenantMapper,
            LoginLogMapper loginLogMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditRecorder auditRecorder,
            CaptchaService captchaService,
            RefreshTokenService refreshTokenService,
            AccessService accessService) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditRecorder = auditRecorder;
        this.captchaService = captchaService;
        this.refreshTokenService = refreshTokenService;
        this.accessService = accessService;
    }

    @Transactional(noRollbackFor = MopException.class)
    public LoginResponse login(String username, String password, String captchaId, String captchaCode, String ip) {
        captchaService.verify(captchaId, captchaCode);
        UserEntity user = findByUsername(username);
        boolean enabled = user != null && Integer.valueOf(1).equals(user.getStatus());
        boolean matched = user != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (!enabled || !matched) {
            writeLog(user, username, false, ip, "用户名或密码错误");
            auditRecorder.record("AUTH", "LOGIN", username, "FAIL", null);
            throw new MopException(ErrorCode.BAD_CREDENTIALS);
        }
        TenantContext.set(user.getTenantId());
        AuthenticatedUser principal = toPrincipal(user);
        String access = jwtService.issue(principal);
        String refresh = refreshTokenService.issue(principal);
        writeLog(user, username, true, ip, "登录成功");
        auditRecorder.record("AUTH", "LOGIN", username, "SUCCESS", null);
        int expiresIn = (int) Math.min(Integer.MAX_VALUE, jwtService.ttlSeconds());
        return new LoginResponse(access, refresh, "Bearer", expiresIn, toProfile(user));
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        ParsedToken parsed = refreshTokenService.rotate(refreshToken);
        UserEntity user = userMapper.selectById(parsed.user().userId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new MopException(ErrorCode.REFRESH_INVALID);
        }
        AuthenticatedUser principal = toPrincipal(user);
        String access = jwtService.issue(principal);
        String nextRefresh = refreshTokenService.issue(principal);
        int expiresIn = (int) Math.min(Integer.MAX_VALUE, jwtService.ttlSeconds());
        return new LoginResponse(access, nextRefresh, "Bearer", expiresIn, toProfile(user));
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    public SessionProfile me(AuthenticatedUser principal) {
        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new MopException(ErrorCode.UNAUTHENTICATED);
        }
        List<MenuNode> menus = new ArrayList<>();
        for (MenuEntity menu : accessService.menusOf(user.getId())) {
            menus.add(new MenuNode(menu.getId(), menu.getParentId(), menu.getName(), menu.getPath(), menu.getMenuType(), menu.getPermission()));
        }
        return new SessionProfile(toProfile(user), new ArrayList<>(accessService.permissionsOf(user.getId())), menus);
    }

    private UserEntity findByUsername(String username) {
        TenantIgnore.ignore();
        try {
            return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getUsername, username)
                    .last("LIMIT 1"));
        } finally {
            TenantIgnore.use();
        }
    }

    private AuthenticatedUser toPrincipal(UserEntity user) {
        return new AuthenticatedUser(user.getId(), user.getUid(), user.getUsername(), user.getDisplayName(), user.getTenantId());
    }

    private cn.muxicloud.mop.system.dto.UserProfile toProfile(UserEntity user) {
        TenantIgnore.ignore();
        try {
            TenantEntity tenant = tenantMapper.selectById(user.getTenantId());
            String tenantName = tenant == null ? "" : tenant.getName();
            return new cn.muxicloud.mop.system.dto.UserProfile(user.getUid(), user.getUsername(), user.getDisplayName(), user.getTenantId(), tenantName);
        } finally {
            TenantIgnore.use();
        }
    }

    private void writeLog(UserEntity user, String username, boolean success, String ip, String message) {
        if (user != null && user.getTenantId() != null) {
            TenantContext.set(user.getTenantId());
        } else {
            TenantIgnore.ignore();
        }
        try {
            LoginLogEntity log = new LoginLogEntity();
            log.setTenantId(user == null || user.getTenantId() == null ? 0L : user.getTenantId());
            log.setUserId(user == null ? null : user.getId());
            log.setUsername(username);
            log.setSuccess(success ? 1 : 0);
            log.setMessage(message);
            log.setIp(ip == null ? "" : ip);
            log.setTraceId(TraceIds.current());
            log.setCreatedAt(LocalDateTime.now());
            loginLogMapper.insert(log);
        } finally {
            TenantIgnore.use();
        }
    }
}
