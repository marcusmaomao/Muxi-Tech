package cn.muxicloud.mop.system.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.JwtService;
import cn.muxicloud.mop.security.ParsedToken;
import cn.muxicloud.mop.system.entity.RefreshTokenEntity;
import cn.muxicloud.mop.system.mapper.RefreshTokenMapper;
import cn.muxicloud.mop.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final RefreshTokenMapper mapper;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenMapper mapper, JwtService jwtService) {
        this.mapper = mapper;
        this.jwtService = jwtService;
    }

    @Transactional
    public String issue(AuthenticatedUser user) {
        String tokenId = UUID.randomUUID().toString();
        RefreshTokenEntity row = new RefreshTokenEntity();
        row.setTenantId(user.tenantId());
        row.setUserId(user.userId());
        row.setTokenId(tokenId);
        row.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.refreshTtlSeconds()));
        row.setRevoked(0);
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return jwtService.issueRefresh(user, tokenId);
    }

    @Transactional
    public ParsedToken rotate(String refreshToken) {
        ParsedToken parsed = jwtService.parseRefresh(refreshToken).orElseThrow(() -> new MopException(ErrorCode.REFRESH_INVALID));
        TenantContext.set(parsed.user().tenantId());
        RefreshTokenEntity row = mapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenId, parsed.tokenId())
                .last("LIMIT 1"));
        if (row == null || Integer.valueOf(1).equals(row.getRevoked()) || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new MopException(ErrorCode.REFRESH_INVALID);
        }
        row.setRevoked(1);
        mapper.updateById(row);
        return parsed;
    }

    @Transactional
    public void revoke(String refreshToken) {
        jwtService.parseRefresh(refreshToken).ifPresent(parsed -> {
            TenantContext.set(parsed.user().tenantId());
            RefreshTokenEntity row = mapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                    .eq(RefreshTokenEntity::getTokenId, parsed.tokenId())
                    .last("LIMIT 1"));
            if (row != null) {
                row.setRevoked(1);
                mapper.updateById(row);
            }
        });
    }
}
