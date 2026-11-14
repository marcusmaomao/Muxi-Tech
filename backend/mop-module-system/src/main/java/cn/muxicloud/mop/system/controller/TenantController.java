package cn.muxicloud.mop.system.controller;

import cn.muxicloud.mop.system.entity.TenantEntity;
import cn.muxicloud.mop.system.security.OperLog;
import cn.muxicloud.mop.system.security.RequiresPermission;
import cn.muxicloud.mop.system.service.TenantAdminService;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {
    private final TenantAdminService tenantAdminService;

    public TenantController(TenantAdminService tenantAdminService) {
        this.tenantAdminService = tenantAdminService;
    }

    @PostMapping
    @RequiresPermission("system:user:create")
    @OperLog(title = "租户", action = "CREATE")
    public ApiResponse<TenantEntity> create(@RequestBody TenantRequest request) {
        return ApiResponse.ok(tenantAdminService.create(
                request.code(), request.name(), request.adminUsername(), request.adminPassword(), request.adminDisplayName()));
    }

    public record TenantRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String adminUsername,
            @NotBlank String adminPassword,
            @NotBlank String adminDisplayName) {
    }
}
