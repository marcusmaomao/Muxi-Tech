package cn.muxicloud.mop.system.service;

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
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminService {
    private final TenantMapper tenantMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public TenantAdminService(
            TenantMapper tenantMapper,
            RoleMapper roleMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.tenantMapper = tenantMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantEntity create(String code, String name, String adminUsername, String adminPassword, String adminDisplayName) {
        TenantIgnore.ignore();
        try {
            long id = IdWorker.getId();
            TenantEntity tenant = new TenantEntity();
            tenant.setId(id);
            tenant.setTenantId(id);
            tenant.setUid(Ulids.newUlid());
            tenant.setCode(code);
            tenant.setName(name);
            tenant.setStatus(1);
            tenantMapper.insert(tenant);
            RoleEntity role = new RoleEntity();
            role.setTenantId(id);
            role.setUid(Ulids.newUlid());
            role.setCode(AccessService.PLATFORM_ADMIN);
            role.setName("平台管理员");
            roleMapper.insert(role);
            UserEntity user = new UserEntity();
            user.setTenantId(id);
            user.setUid(Ulids.newUlid());
            user.setUsername(adminUsername);
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setDisplayName(adminDisplayName);
            user.setStatus(1);
            userMapper.insert(user);
            UserRoleEntity link = new UserRoleEntity();
            link.setTenantId(id);
            link.setUserId(user.getId());
            link.setRoleId(role.getId());
            userRoleMapper.insert(link);
            return tenant;
        } finally {
            TenantIgnore.use();
        }
    }
}
