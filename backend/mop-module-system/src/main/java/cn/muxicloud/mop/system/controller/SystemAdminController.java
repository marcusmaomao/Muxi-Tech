package cn.muxicloud.mop.system.controller;

import cn.muxicloud.mop.system.entity.ConfigEntity;
import cn.muxicloud.mop.system.entity.DeptEntity;
import cn.muxicloud.mop.system.entity.DictItemEntity;
import cn.muxicloud.mop.system.entity.DictTypeEntity;
import cn.muxicloud.mop.system.entity.LoginLogEntity;
import cn.muxicloud.mop.system.entity.MenuEntity;
import cn.muxicloud.mop.system.entity.NoticeEntity;
import cn.muxicloud.mop.system.entity.OperLogEntity;
import cn.muxicloud.mop.system.entity.PostEntity;
import cn.muxicloud.mop.system.entity.RoleEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.security.OperLog;
import cn.muxicloud.mop.system.security.RequiresPermission;
import cn.muxicloud.mop.system.service.SystemAdminService;
import cn.muxicloud.mop.web.api.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemAdminController {
    private final SystemAdminService admin;

    public SystemAdminController(SystemAdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    @RequiresPermission("system:user:list")
    public ApiResponse<Page<UserEntity>> users(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(admin.users(page, Math.min(size, 200)));
    }

    @PostMapping("/users")
    @RequiresPermission("system:user:create")
    @OperLog(title = "用户", action = "CREATE")
    public ApiResponse<UserEntity> createUser(@RequestBody UserRequest request) {
        return ApiResponse.ok(admin.createUser(request.username(), request.password(), request.displayName(), request.deptId()));
    }

    @DeleteMapping("/users/{id}")
    @RequiresPermission("system:user:delete")
    @OperLog(title = "用户", action = "DELETE")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        admin.deleteUser(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles")
    @RequiresPermission("system:role:list")
    public ApiResponse<List<RoleEntity>> roles() {
        return ApiResponse.ok(admin.roles());
    }

    @PostMapping("/roles")
    @RequiresPermission("system:role:create")
    @OperLog(title = "角色", action = "CREATE")
    public ApiResponse<RoleEntity> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.ok(admin.createRole(request.code(), request.name()));
    }

    @PostMapping("/roles/{id}/menus")
    @RequiresPermission("system:role:grant")
    @OperLog(title = "角色", action = "GRANT")
    public ApiResponse<Void> grant(@PathVariable Long id, @RequestBody IdListRequest request) {
        admin.grantMenus(id, request.ids());
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{id}/roles/{roleId}")
    @RequiresPermission("system:user:grant")
    @OperLog(title = "用户", action = "GRANT")
    public ApiResponse<Void> assignRole(@PathVariable Long id, @PathVariable Long roleId) {
        admin.assignRole(id, roleId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/menus")
    @RequiresPermission("system:menu:list")
    public ApiResponse<List<MenuEntity>> menus() {
        return ApiResponse.ok(admin.menus());
    }

    @GetMapping("/depts")
    @RequiresPermission("system:dept:list")
    public ApiResponse<List<DeptEntity>> depts() {
        return ApiResponse.ok(admin.depts());
    }

    @PostMapping("/depts")
    @RequiresPermission("system:dept:create")
    @OperLog(title = "部门", action = "CREATE")
    public ApiResponse<DeptEntity> createDept(@RequestBody DeptRequest request) {
        return ApiResponse.ok(admin.createDept(request.name(), request.parentId()));
    }

    @GetMapping("/posts")
    @RequiresPermission("system:post:list")
    public ApiResponse<List<PostEntity>> posts() {
        return ApiResponse.ok(admin.posts());
    }

    @PostMapping("/posts")
    @RequiresPermission("system:post:create")
    @OperLog(title = "岗位", action = "CREATE")
    public ApiResponse<PostEntity> createPost(@RequestBody PostRequest request) {
        return ApiResponse.ok(admin.createPost(request.code(), request.name()));
    }

    @GetMapping("/dicts")
    @RequiresPermission("system:dict:list")
    public ApiResponse<List<DictTypeEntity>> dicts() {
        return ApiResponse.ok(admin.dictTypes());
    }

    @PostMapping("/dicts")
    @RequiresPermission("system:dict:create")
    @OperLog(title = "字典", action = "CREATE")
    public ApiResponse<DictTypeEntity> createDict(@RequestBody DictTypeRequest request) {
        return ApiResponse.ok(admin.createDictType(request.code(), request.name()));
    }

    @GetMapping("/dicts/{typeId}/items")
    @RequiresPermission("system:dict:list")
    public ApiResponse<List<DictItemEntity>> dictItems(@PathVariable Long typeId) {
        return ApiResponse.ok(admin.dictItems(typeId));
    }

    @PostMapping("/dicts/{typeId}/items")
    @RequiresPermission("system:dict:create")
    @OperLog(title = "字典", action = "CREATE_ITEM")
    public ApiResponse<DictItemEntity> createDictItem(@PathVariable Long typeId, @RequestBody DictItemRequest request) {
        return ApiResponse.ok(admin.createDictItem(typeId, request.label(), request.value()));
    }

    @GetMapping("/configs")
    @RequiresPermission("system:config:list")
    public ApiResponse<List<ConfigEntity>> configs() {
        return ApiResponse.ok(admin.configs());
    }

    @PostMapping("/configs")
    @RequiresPermission("system:config:create")
    @OperLog(title = "参数", action = "CREATE")
    public ApiResponse<ConfigEntity> createConfig(@RequestBody ConfigRequest request) {
        return ApiResponse.ok(admin.createConfig(request.key(), request.value(), request.remark()));
    }

    @GetMapping("/notices")
    @RequiresPermission("system:notice:list")
    public ApiResponse<List<NoticeEntity>> notices() {
        return ApiResponse.ok(admin.notices());
    }

    @PostMapping("/notices")
    @RequiresPermission("system:notice:create")
    @OperLog(title = "通知", action = "CREATE")
    public ApiResponse<NoticeEntity> createNotice(@RequestBody NoticeRequest request) {
        return ApiResponse.ok(admin.createNotice(request.title(), request.content()));
    }

    @GetMapping("/logs/login")
    @RequiresPermission("system:log:list")
    public ApiResponse<List<LoginLogEntity>> loginLogs() {
        return ApiResponse.ok(admin.loginLogs());
    }

    @GetMapping("/logs/oper")
    @RequiresPermission("system:log:list")
    public ApiResponse<List<OperLogEntity>> operLogs() {
        return ApiResponse.ok(admin.operLogs());
    }

    public record UserRequest(@NotBlank String username, @NotBlank String password, @NotBlank String displayName, Long deptId) {}
    public record RoleRequest(@NotBlank String code, @NotBlank String name) {}
    public record IdListRequest(List<Long> ids) {}
    public record DeptRequest(@NotBlank String name, Long parentId) {}
    public record PostRequest(@NotBlank String code, @NotBlank String name) {}
    public record DictTypeRequest(@NotBlank String code, @NotBlank String name) {}
    public record DictItemRequest(@NotBlank String label, @NotBlank String value) {}
    public record ConfigRequest(@NotBlank String key, @NotBlank String value, String remark) {}
    public record NoticeRequest(@NotBlank String title, @NotBlank String content) {}
}
