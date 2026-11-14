package cn.muxicloud.mop.system.seed;

import cn.muxicloud.mop.system.entity.MenuEntity;
import cn.muxicloud.mop.system.entity.RoleEntity;
import cn.muxicloud.mop.system.entity.RoleMenuEntity;
import cn.muxicloud.mop.system.mapper.MenuMapper;
import cn.muxicloud.mop.system.mapper.RoleMapper;
import cn.muxicloud.mop.system.mapper.RoleMenuMapper;
import cn.muxicloud.mop.tenant.TenantContext;
import cn.muxicloud.mop.tenant.TenantIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class SystemMenuSeed implements ApplicationRunner {
    private static final String[][] MENUS = {
            {"工作台", "/workbench", "MENU", "", "10"},
            {"用户", "/system/users", "MENU", "system:user:list", "20"},
            {"新建用户", "", "BUTTON", "system:user:create", "21"},
            {"删除用户", "", "BUTTON", "system:user:delete", "22"},
            {"分配角色", "", "BUTTON", "system:user:grant", "23"},
            {"角色", "/system/roles", "MENU", "system:role:list", "30"},
            {"新建角色", "", "BUTTON", "system:role:create", "31"},
            {"分配菜单", "", "BUTTON", "system:role:grant", "32"},
            {"菜单", "/system/menus", "MENU", "system:menu:list", "40"},
            {"部门", "/system/depts", "MENU", "system:dept:list", "50"},
            {"新建部门", "", "BUTTON", "system:dept:create", "51"},
            {"岗位", "/system/posts", "MENU", "system:post:list", "60"},
            {"新建岗位", "", "BUTTON", "system:post:create", "61"},
            {"字典", "/system/dicts", "MENU", "system:dict:list", "70"},
            {"新建字典", "", "BUTTON", "system:dict:create", "71"},
            {"参数", "/system/configs", "MENU", "system:config:list", "80"},
            {"新建参数", "", "BUTTON", "system:config:create", "81"},
            {"通知", "/system/notices", "MENU", "system:notice:list", "90"},
            {"新建通知", "", "BUTTON", "system:notice:create", "91"},
            {"日志", "/system/logs", "MENU", "system:log:list", "100"}
    };

    private final DemoSeedProperties properties;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    public SystemMenuSeed(DemoSeedProperties properties, RoleMapper roleMapper, MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.properties = properties;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isSeed()) {
            return;
        }
        RoleEntity role;
        TenantIgnore.ignore();
        try {
            role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                    .eq(RoleEntity::getCode, DemoSeedService.ADMIN_ROLE)
                    .last("LIMIT 1"));
        } finally {
            TenantIgnore.use();
        }
        if (role == null) {
            return;
        }
        TenantContext.set(role.getTenantId());
        if (menuMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }
        for (String[] spec : MENUS) {
            MenuEntity menu = new MenuEntity();
            menu.setTenantId(role.getTenantId());
            menu.setName(spec[0]);
            menu.setPath(spec[1]);
            menu.setMenuType(spec[2]);
            menu.setPermission(spec[3]);
            menu.setSortNo(Integer.valueOf(spec[4]));
            menuMapper.insert(menu);
            RoleMenuEntity link = new RoleMenuEntity();
            link.setTenantId(role.getTenantId());
            link.setRoleId(role.getId());
            link.setMenuId(menu.getId());
            link.setDeleted(0);
            link.setCreatedAt(LocalDateTime.now());
            roleMenuMapper.insert(link);
        }
    }
}
