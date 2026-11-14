package cn.muxicloud.mop.system.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.system.entity.MenuEntity;
import cn.muxicloud.mop.system.entity.RoleEntity;
import cn.muxicloud.mop.system.entity.RoleMenuEntity;
import cn.muxicloud.mop.system.entity.UserRoleEntity;
import cn.muxicloud.mop.system.mapper.MenuMapper;
import cn.muxicloud.mop.system.mapper.RoleMapper;
import cn.muxicloud.mop.system.mapper.RoleMenuMapper;
import cn.muxicloud.mop.system.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    public AccessService(UserRoleMapper userRoleMapper, RoleMapper roleMapper, RoleMenuMapper roleMenuMapper, MenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new MopException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    public void require(String permission) {
        java.util.Set<String> codes = permissionsOf(currentUser().userId());
        if (!codes.contains("*") && !codes.contains(permission)) {
            throw new MopException(ErrorCode.FORBIDDEN);
        }
    }

    public Set<String> permissionsOf(Long userId) {
        if (isPlatformAdmin(userId)) {
            Set<String> all = new LinkedHashSet<>();
            for (MenuEntity menu : menuMapper.selectList(new LambdaQueryWrapper<>())) {
                if (menu.getPermission() != null && !menu.getPermission().isBlank()) {
                    all.add(menu.getPermission());
                }
            }
            all.add("*");
            return all;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Long roleId : roleIds(userId)) {
            List<RoleMenuEntity> links = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                    .eq(RoleMenuEntity::getRoleId, roleId));
            for (RoleMenuEntity link : links) {
                MenuEntity menu = menuMapper.selectById(link.getMenuId());
                if (menu != null && menu.getPermission() != null) {
                    codes.add(menu.getPermission());
                }
            }
        }
        return codes;
    }

    public List<MenuEntity> menusOf(Long userId) {
        if (isPlatformAdmin(userId)) {
            return menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>().orderByAsc(MenuEntity::getSortNo));
        }
        List<MenuEntity> menus = new ArrayList<>();
        for (Long roleId : roleIds(userId)) {
            for (RoleMenuEntity link : roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getRoleId, roleId))) {
                MenuEntity menu = menuMapper.selectById(link.getMenuId());
                if (menu != null) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    public boolean isPlatformAdmin(Long userId) {
        for (Long roleId : roleIds(userId)) {
            RoleEntity role = roleMapper.selectById(roleId);
            if (role != null && PLATFORM_ADMIN.equals(role.getCode())) {
                return true;
            }
        }
        return false;
    }

    private List<Long> roleIds(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId))
                .stream().map(UserRoleEntity::getRoleId).toList();
    }
}
