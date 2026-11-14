package cn.muxicloud.mop.system.dto;

public record LoginResponse(String accessToken, String refreshToken, String tokenType, int expiresIn, UserProfile user) {
}
