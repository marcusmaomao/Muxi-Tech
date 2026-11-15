package cn.muxicloud.mop.semantic.controller;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.service.QualityService;
import cn.muxicloud.mop.semantic.service.QualityService.QualityView;
import cn.muxicloud.mop.semantic.service.QualityService.ReleaseView;
import cn.muxicloud.mop.semantic.service.QualityService.TrendPoint;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QualityController {
    private final QualityService quality;

    public QualityController(QualityService quality) {
        this.quality = quality;
    }

    @PostMapping("/api/v1/branches/{branchId}/quality-runs")
    @RequiresPermission("model:form:edit")
    public ApiResponse<QualityView> run(@PathVariable Long branchId, Authentication authentication) {
        return ApiResponse.ok(quality.run(branchId, user(authentication)));
    }

    @GetMapping("/api/v1/quality-runs/{id}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<QualityView> get(@PathVariable Long id) {
        return ApiResponse.ok(quality.get(id));
    }

    @GetMapping("/api/v1/projects/{projectId}/quality/trend")
    @RequiresPermission("model:form:edit")
    public ApiResponse<List<TrendPoint>> trend(@PathVariable Long projectId) {
        return ApiResponse.ok(quality.trend(projectId));
    }

    @PutMapping("/api/v1/sem-issues/{id}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<QualityView> update(@PathVariable Long id, @RequestBody IssueUpdate update) {
        return ApiResponse.ok(quality.updateIssue(id, update.state(), update.ignoreReason()));
    }

    @PostMapping("/api/v1/branches/{branchId}/releases")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ReleaseView> publish(@PathVariable Long branchId, @RequestBody ReleaseRequest request, Authentication authentication) {
        return ApiResponse.ok(quality.publish(branchId, request.version(), user(authentication)));
    }

    @GetMapping("/api/v1/projects/{projectId}/releases")
    @RequiresPermission("model:form:edit")
    public ApiResponse<List<ReleaseView>> releases(@PathVariable Long projectId) {
        return ApiResponse.ok(quality.releases(projectId));
    }

    @GetMapping("/api/v1/releases/{id}/compiled-model")
    @RequiresPermission("model:form:edit")
    public ApiResponse<Map<String, Object>> compiled(@PathVariable Long id) {
        return ApiResponse.ok(quality.compiledModel(id));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record IssueUpdate(@NotBlank String state, String ignoreReason) {}

    public record ReleaseRequest(@NotBlank String version) {}
}
