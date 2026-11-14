package cn.muxicloud.mop.system.seed;

import cn.muxicloud.mop.common.id.Ulids;
import cn.muxicloud.mop.system.entity.RoleEntity;
import cn.muxicloud.mop.system.entity.TenantEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.entity.UserRoleEntity;
import cn.muxicloud.mop.system.mapper.RoleMapper;
import cn.muxicloud.mop.system.mapper.TenantMapper;
import cn.muxicloud.mop.system.mapper.UserMapper;
import cn.muxicloud.mop.system.mapper.UserRoleMapper;
import cn.muxicloud.mop.tenant.TenantIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
@EnableConfigurationProperties(DemoSeedProperties.class)
public class DemoSeedService implements ApplicationRunner {
    static final String ADMIN_ROLE = "PLATFORM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    private final DemoSeedProperties properties;
    private final TenantMapper tenantMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public DemoSeedService(
            DemoSeedProperties properties,
            TenantMapper tenantMapper,
            RoleMapper roleMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.tenantMapper = tenantMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isSeed()) {
            return;
        }
        if (properties.getAdminPassword() == null || properties.getAdminPassword().isBlank()) {
            throw new IllegalStateException("已开启 MOP_DEMO_SEED，但 MOP_DEMO_ADMIN_PASSWORD 为空");
        }
        TenantIgnore.ignore();
        try {
            TenantEntity tenant = ensureTenant();
            RoleEntity role = ensureRole(tenant.getId());
            UserEntity user = ensureUser(tenant.getId());
            ensureLink(tenant.getId(), user.getId(), role.getId());
            log.info("演示账号已就绪 username={} tenant={}", user.getUsername(), tenant.getCode());
        } finally {
            TenantIgnore.use();
        }
    }

    private TenantEntity ensureTenant() {
        TenantEntity existing = tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getCode, properties.getTenantCode())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        long id = IdWorker.getId();
        TenantEntity tenant = new TenantEntity();
        tenant.setId(id);
        tenant.setTenantId(id);
        tenant.setUid(Ulids.newUlid());
        tenant.setCode(properties.getTenantCode());
        tenant.setName(properties.getTenantName());
        tenant.setStatus(1);
        tenantMapper.insert(tenant);
        return tenant;
    }

    private RoleEntity ensureRole(Long tenantId) {
        RoleEntity existing = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getTenantId, tenantId)
                .eq(RoleEntity::getCode, ADMIN_ROLE)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        RoleEntity role = new RoleEntity();
        role.setTenantId(tenantId);
        role.setUid(Ulids.newUlid());
        role.setCode(ADMIN_ROLE);
        role.setName("平台管理员");
        roleMapper.insert(role);
        return role;
    }

    private UserEntity ensureUser(Long tenantId) {
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getTenantId, tenantId)
                .eq(UserEntity::getUsername, properties.getAdminUsername())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        UserEntity user = new UserEntity();
        user.setTenantId(tenantId);
        user.setUid(Ulids.newUlid());
        user.setUsername(properties.getAdminUsername());
        user.setPasswordHash(passwordEncoder.encode(properties.getAdminPassword()));
        user.setDisplayName(properties.getAdminDisplayName());
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private void ensureLink(Long tenantId, Long userId, Long roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getRoleId, roleId));
        if (count != null && count > 0) {
            return;
        }
        UserRoleEntity link = new UserRoleEntity();
        link.setTenantId(tenantId);
        link.setUserId(userId);
        link.setRoleId(roleId);
        userRoleMapper.insert(link);
    }
}
