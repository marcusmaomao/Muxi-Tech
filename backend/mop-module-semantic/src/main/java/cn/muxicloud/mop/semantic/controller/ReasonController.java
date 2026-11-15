package cn.muxicloud.mop.semantic.controller;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.service.ReasonService;
import cn.muxicloud.mop.semantic.service.ReasonService.ReasonView;
import cn.muxicloud.mop.web.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReasonController {
    private final ReasonService reason;

    public ReasonController(ReasonService reason) {
        this.reason = reason;
    }

    @PostMapping("/api/v1/branches/{branchId}/reason")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ReasonView> run(@PathVariable Long branchId, @RequestBody(required = false) ReasonRequest request, Authentication authentication) {
        String engine = request == null ? "ELK" : request.reasoner();
        return ApiResponse.ok(reason.run(branchId, engine, user(authentication)));
    }

    @GetMapping("/api/v1/sem-runs/{id}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ReasonView> get(@PathVariable Long id) {
        return ApiResponse.ok(reason.get(id));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record ReasonRequest(String reasoner) {}
}
