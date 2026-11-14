package cn.muxicloud.mop.system.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.common.id.Ulids;
import cn.muxicloud.mop.system.entity.ConfigEntity;
import cn.muxicloud.mop.system.entity.DeptEntity;
import cn.muxicloud.mop.system.entity.DictItemEntity;
import cn.muxicloud.mop.system.entity.DictTypeEntity;
import cn.muxicloud.mop.system.entity.MenuEntity;
import cn.muxicloud.mop.system.entity.NoticeEntity;
import cn.muxicloud.mop.system.entity.PostEntity;
import cn.muxicloud.mop.system.entity.RoleEntity;
import cn.muxicloud.mop.system.entity.RoleMenuEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.entity.UserRoleEntity;
import cn.muxicloud.mop.system.mapper.ConfigMapper;
import cn.muxicloud.mop.system.mapper.DeptMapper;
import cn.muxicloud.mop.system.mapper.DictItemMapper;
import cn.muxicloud.mop.system.mapper.DictTypeMapper;
import cn.muxicloud.mop.system.mapper.MenuMapper;
import cn.muxicloud.mop.system.entity.LoginLogEntity;
import cn.muxicloud.mop.system.entity.OperLogEntity;
import cn.muxicloud.mop.system.mapper.LoginLogMapper;
import cn.muxicloud.mop.system.mapper.NoticeMapper;
import cn.muxicloud.mop.system.mapper.OperLogMapper;
import cn.muxicloud.mop.system.mapper.PostMapper;
import cn.muxicloud.mop.system.mapper.RoleMapper;
import cn.muxicloud.mop.system.mapper.RoleMenuMapper;
import cn.muxicloud.mop.system.mapper.UserMapper;
import cn.muxicloud.mop.system.mapper.UserRoleMapper;
import cn.muxicloud.mop.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final DeptMapper deptMapper;
    private final PostMapper postMapper;
    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final ConfigMapper configMapper;
    private final NoticeMapper noticeMapper;
    private final LoginLogMapper loginLogMapper;
    private final OperLogMapper operLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccessService accessService;

    public SystemAdminService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            MenuMapper menuMapper,
            RoleMenuMapper roleMenuMapper,
            DeptMapper deptMapper,
            PostMapper postMapper,
            DictTypeMapper dictTypeMapper,
            DictItemMapper dictItemMapper,
            ConfigMapper configMapper,
            NoticeMapper noticeMapper,
            LoginLogMapper loginLogMapper,
            OperLogMapper operLogMapper,
            PasswordEncoder passwordEncoder,
            AccessService accessService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
        this.configMapper = configMapper;
        this.noticeMapper = noticeMapper;
        this.loginLogMapper = loginLogMapper;
        this.operLogMapper = operLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.accessService = accessService;
    }

    public Page<UserEntity> users(long page, long size) {
        return userMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<UserEntity>().orderByDesc(UserEntity::getId));
    }

    @Transactional
    public UserEntity createUser(String username, String password, String displayName, Long deptId) {
        UserEntity user = new UserEntity();
        user.setTenantId(requiredTenant());
        user.setUid(Ulids.newUlid());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setDeptId(deptId);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    public void deleteUser(Long id) {
        if (id.equals(accessService.currentUser().userId())) {
            throw new MopException(ErrorCode.VALIDATION, "不能删除当前登录用户", null);
        }
        userMapper.deleteById(id);
    }

    public List<RoleEntity> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId));
    }

    @Transactional
    public RoleEntity createRole(String code, String name) {
        RoleEntity role = new RoleEntity();
        role.setTenantId(requiredTenant());
        role.setUid(Ulids.newUlid());
        role.setCode(code);
        role.setName(name);
        roleMapper.insert(role);
        return role;
    }

    @Transactional
    public void grantMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getRoleId, roleId));
        for (Long menuId : menuIds) {
            RoleMenuEntity link = new RoleMenuEntity();
            link.setTenantId(requiredTenant());
            link.setRoleId(roleId);
            link.setMenuId(menuId);
            link.setDeleted(0);
            link.setCreatedAt(LocalDateTime.now());
            roleMenuMapper.insert(link);
        }
    }

    @Transactional
    public void assignRole(Long userId, Long roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getRoleId, roleId));
        if (count != null && count > 0) {
            return;
        }
        UserRoleEntity link = new UserRoleEntity();
        link.setTenantId(requiredTenant());
        link.setUserId(userId);
        link.setRoleId(roleId);
        userRoleMapper.insert(link);
    }

    public List<MenuEntity> menus() {
        return menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>().orderByAsc(MenuEntity::getSortNo));
    }

    public List<DeptEntity> depts() {
        return deptMapper.selectList(new LambdaQueryWrapper<DeptEntity>().orderByAsc(DeptEntity::getSortNo));
    }

    @Transactional
    public DeptEntity createDept(String name, Long parentId) {
        DeptEntity dept = new DeptEntity();
        dept.setTenantId(requiredTenant());
        dept.setName(name);
        dept.setParentId(parentId);
        dept.setSortNo(0);
        dept.setStatus(1);
        deptMapper.insert(dept);
        return dept;
    }

    public List<PostEntity> posts() {
        return postMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Transactional
    public PostEntity createPost(String code, String name) {
        PostEntity post = new PostEntity();
        post.setTenantId(requiredTenant());
        post.setCode(code);
        post.setName(name);
        post.setStatus(1);
        postMapper.insert(post);
        return post;
    }

    public List<DictTypeEntity> dictTypes() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Transactional
    public DictTypeEntity createDictType(String code, String name) {
        DictTypeEntity type = new DictTypeEntity();
        type.setTenantId(requiredTenant());
        type.setCode(code);
        type.setName(name);
        dictTypeMapper.insert(type);
        return type;
    }

    public List<DictItemEntity> dictItems(Long typeId) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>().eq(DictItemEntity::getTypeId, typeId));
    }

    @Transactional
    public DictItemEntity createDictItem(Long typeId, String label, String value) {
        DictItemEntity item = new DictItemEntity();
        item.setTenantId(requiredTenant());
        item.setTypeId(typeId);
        item.setLabel(label);
        item.setItemValue(value);
        item.setSortNo(0);
        dictItemMapper.insert(item);
        return item;
    }

    public List<ConfigEntity> configs() {
        return configMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Transactional
    public ConfigEntity createConfig(String key, String value, String remark) {
        ConfigEntity config = new ConfigEntity();
        config.setTenantId(requiredTenant());
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setRemark(remark);
        configMapper.insert(config);
        return config;
    }

    public List<LoginLogEntity> loginLogs() {
        return loginLogMapper.selectList(new LambdaQueryWrapper<LoginLogEntity>().orderByDesc(LoginLogEntity::getId).last("LIMIT 100"));
    }

    public List<OperLogEntity> operLogs() {
        return operLogMapper.selectList(new LambdaQueryWrapper<OperLogEntity>().orderByDesc(OperLogEntity::getId).last("LIMIT 100"));
    }

    public List<NoticeEntity> notices() {
        return noticeMapper.selectList(new LambdaQueryWrapper<NoticeEntity>().orderByDesc(NoticeEntity::getId));
    }

    @Transactional
    public NoticeEntity createNotice(String title, String content) {
        NoticeEntity notice = new NoticeEntity();
        notice.setTenantId(requiredTenant());
        notice.setTitle(title);
        notice.setContent(content);
        noticeMapper.insert(notice);
        return notice;
    }

    private static Long requiredTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new MopException(ErrorCode.UNAUTHENTICATED);
        }
        return tenantId;
    }
}
