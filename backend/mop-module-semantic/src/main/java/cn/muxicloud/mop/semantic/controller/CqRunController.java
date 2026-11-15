package cn.muxicloud.mop.semantic.controller;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.service.CqTestService;
import cn.muxicloud.mop.semantic.service.CqTestService.RunView;
import cn.muxicloud.mop.web.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CqRunController {
    private final CqTestService tests;

    public CqRunController(CqTestService tests) {
        this.tests = tests;
    }

    @PostMapping("/api/v1/branches/{branchId}/cq-runs")
    @RequiresPermission("model:form:edit")
    public ApiResponse<RunView> run(@PathVariable Long branchId, @RequestBody(required = false) RunRequest request, Authentication authentication) {
        RunRequest body = request == null ? new RunRequest(null, null, null) : request;
        return ApiResponse.ok(tests.run(branchId, body.cqUid(), body.testUid(), body.threshold(), user(authentication)));
    }

    @GetMapping("/api/v1/cq-runs/{id}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<RunView> get(@PathVariable Long id) {
        return ApiResponse.ok(tests.get(id));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record RunRequest(String cqUid, String testUid, Double threshold) {}
}
