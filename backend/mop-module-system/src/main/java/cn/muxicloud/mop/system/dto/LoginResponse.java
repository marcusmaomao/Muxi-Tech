package cn.muxicloud.mop.system.dto;

public record LoginResponse(String accessToken, String tokenType, int expiresIn, UserProfile user) {
}
