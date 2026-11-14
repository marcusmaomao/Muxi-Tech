package cn.muxicloud.mop.security;

public record AuthenticatedUser(Long userId, String uid, String username, String displayName, Long tenantId) {
}
