package cn.muxicloud.mop.model.controller;

import cn.muxicloud.mop.model.entity.CanvasViewEntity;
import cn.muxicloud.mop.model.service.CanvasService;
import cn.muxicloud.mop.model.service.CanvasService.LockView;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches/{branchId}")
public class CanvasController {
    private final CanvasService canvas;

    public CanvasController(CanvasService canvas) {
        this.canvas = canvas;
    }

    @GetMapping("/canvas-views")
    @RequiresPermission("model:form:edit")
    public ApiResponse<List<CanvasViewEntity>> list(@PathVariable Long branchId) {
        return ApiResponse.ok(canvas.list(branchId));
    }

    @PostMapping("/canvas-views")
    @RequiresPermission("model:form:edit")
    public ApiResponse<CanvasViewEntity> save(@PathVariable Long branchId, @RequestBody ViewRequest request, Authentication authentication) {
        return ApiResponse.ok(canvas.save(branchId, request.name(), request.layout(), user(authentication)));
    }

    @PostMapping("/locks/{uid}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<LockView> lock(@PathVariable Long branchId, @PathVariable String uid, Authentication authentication) {
        return ApiResponse.ok(canvas.lock(branchId, uid, user(authentication)));
    }

    @DeleteMapping("/locks/{uid}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<Void> unlock(@PathVariable Long branchId, @PathVariable String uid, Authentication authentication) {
        canvas.unlock(branchId, uid, user(authentication));
        return ApiResponse.ok(null);
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record ViewRequest(@NotBlank String name, @NotBlank String layout) {}
}
