package cn.muxicloud.mop.security;

public record ParsedToken(AuthenticatedUser user, String tokenId) {
}
