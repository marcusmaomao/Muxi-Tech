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

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties.getSecret(), properties.getTtlSeconds());
    }

    JwtService(String secret, long ttlSeconds) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("MOP_JWT_SECRET 至少需要 32 个字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(user.userId()))
                .claim("uid", user.uid())
                .claim("username", user.username())
                .claim("displayName", user.displayName())
                .claim("tenantId", user.tenantId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("uid", String.class),
                    claims.get("username", String.class),
                    claims.get("displayName", String.class),
                    asLong(claims.get("tenantId"))));
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
