package cn.muxicloud.mop.system.controller;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.system.dto.LoginRequest;
import cn.muxicloud.mop.system.dto.LoginResponse;
import cn.muxicloud.mop.system.dto.UserProfile;
import cn.muxicloud.mop.system.service.AuthService;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request.username(), request.password(), clientIp(httpRequest)));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(Authentication authentication) {
        return ApiResponse.ok(authService.me((AuthenticatedUser) authentication.getPrincipal()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
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
