package cn.muxicloud.mop.system.controller;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.system.dto.LoginRequest;
import cn.muxicloud.mop.system.dto.LoginResponse;
import cn.muxicloud.mop.system.dto.RefreshRequest;
import cn.muxicloud.mop.system.dto.SessionProfile;
import cn.muxicloud.mop.system.service.AuthService;
import cn.muxicloud.mop.system.service.CaptchaService;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaService.CaptchaChallenge> captcha() {
        return ApiResponse.ok(captchaService.create());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(
                request.username(), request.password(), request.captchaId(), request.captchaCode(), clientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<SessionProfile> me(Authentication authentication) {
        return ApiResponse.ok(authService.me((AuthenticatedUser) authentication.getPrincipal()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ApiResponse.ok(null);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
