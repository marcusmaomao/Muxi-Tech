package cn.muxicloud.mop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long ttlSeconds;
    private final long refreshTtlSeconds;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties.getSecret(), properties.getTtlSeconds(), properties.getRefreshTtlSeconds());
    }

    JwtService(String secret, long ttlSeconds) {
        this(secret, ttlSeconds, 604800);
    }

    JwtService(String secret, long ttlSeconds, long refreshTtlSeconds) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("MOP_JWT_SECRET 至少需要 32 个字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlSeconds = ttlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String issue(AuthenticatedUser user) {
        return issueTyped(user, "access", ttlSeconds, null);
    }

    public String issueRefresh(AuthenticatedUser user, String tokenId) {
        return issueTyped(user, "refresh", refreshTtlSeconds, tokenId);
    }

    private String issueTyped(AuthenticatedUser user, String type, long ttl, String tokenId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(Long.toString(user.userId()))
                .claim("uid", user.uid())
                .claim("username", user.username())
                .claim("displayName", user.displayName())
                .claim("tenantId", user.tenantId())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttl)))
                .signWith(key);
        if (tokenId != null) {
            builder.id(tokenId);
        }
        return builder.compact();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public Optional<AuthenticatedUser> parse(String token) {
        return parseTyped(token, "access").map(ParsedToken::user);
    }

    public Optional<ParsedToken> parseRefresh(String token) {
        return parseTyped(token, "refresh");
    }

    private Optional<ParsedToken> parseTyped(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String type = claims.get("typ", String.class);
            if (type != null && !expectedType.equals(type)) {
                return Optional.empty();
            }
            AuthenticatedUser user = new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("uid", String.class),
                    claims.get("username", String.class),
                    claims.get("displayName", String.class),
                    asLong(claims.get("tenantId")));
            return Optional.of(new ParsedToken(user, claims.getId()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
