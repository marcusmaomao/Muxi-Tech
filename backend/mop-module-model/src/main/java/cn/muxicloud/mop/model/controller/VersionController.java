package cn.muxicloud.mop.model.controller;

import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.CommitEntity;
import cn.muxicloud.mop.model.entity.MergeRequestEntity;
import cn.muxicloud.mop.model.entity.MrReviewEntity;
import cn.muxicloud.mop.model.service.DiffService;
import cn.muxicloud.mop.model.service.MergeService;
import cn.muxicloud.mop.model.service.ModelService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VersionController {
    private final ModelService models;
    private final DiffService diffs;
    private final MergeService merges;

    public VersionController(ModelService models, DiffService diffs, MergeService merges) {
        this.models = models;
        this.diffs = diffs;
        this.merges = merges;
    }

    @PostMapping("/projects/{id}/branches")
    @RequiresPermission("model:form:edit")
    public ApiResponse<BranchEntity> createBranch(@PathVariable Long id, @RequestBody BranchRequest request, Authentication authentication) {
        return ApiResponse.ok(models.createBranch(id, request.name(), request.parentBranchId(), user(authentication)));
    }

    @DeleteMapping("/branches/{id}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        models.archive(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/branches/{id}/protect")
    @RequiresPermission("model:form:edit")
    public ApiResponse<BranchEntity> protect(@PathVariable Long id, @RequestBody ProtectRequest request) {
        return ApiResponse.ok(models.protect(id, request.enabled()));
    }

    @GetMapping("/branches/{id}/commits")
    @RequiresPermission("model:form:edit")
    public ApiResponse<List<CommitEntity>> commits(@PathVariable Long id) {
        return ApiResponse.ok(models.commits(id));
    }

    @PostMapping("/branches/{id}/revert/{rev}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<CommitEntity> revert(@PathVariable Long id, @PathVariable long rev, Authentication authentication) {
        return ApiResponse.ok(models.revertTo(id, rev, user(authentication)));
    }

    @GetMapping("/projects/{id}/diff")
    @RequiresPermission("model:form:edit")
    public ApiResponse<DiffService.DiffResult> diff(@PathVariable Long id, @RequestParam("from") String from, @RequestParam("to") String to) {
        return ApiResponse.ok(diffs.diff(id, from, to));
    }

    @GetMapping("/projects/{id}/merge-requests")
    @RequiresPermission("model:form:edit")
    public ApiResponse<List<MergeRequestEntity>> mergeRequests(@PathVariable Long id) {
        return ApiResponse.ok(merges.list(id));
    }

    @PostMapping("/projects/{id}/merge-requests")
    @RequiresPermission("model:form:edit")
    public ApiResponse<MergeRequestEntity> open(@PathVariable Long id, @RequestBody MergeRequest request, Authentication authentication) {
        return ApiResponse.ok(merges.open(id, request.sourceBranchId(), request.targetBranchId(), request.title(), request.description(), user(authentication)));
    }

    @GetMapping("/merge-requests/{id}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<MergeRequestEntity> mergeRequest(@PathVariable Long id) {
        return ApiResponse.ok(merges.get(id));
    }

    @PostMapping("/merge-requests/{id}/resolve")
    @RequiresPermission("model:form:edit")
    public ApiResponse<MergeRequestEntity> resolve(@PathVariable Long id, @RequestBody ResolveRequest request) {
        return ApiResponse.ok(merges.resolve(id, request.resolutions()));
    }

    @PostMapping("/merge-requests/{id}/reviews")
    @RequiresPermission("model:form:edit")
    public ApiResponse<MrReviewEntity> review(@PathVariable Long id, @RequestBody ReviewRequest request, Authentication authentication) {
        return ApiResponse.ok(merges.review(id, request.decision(), request.comment(), request.elementUid(), user(authentication)));
    }

    @PostMapping("/merge-requests/{id}/merge")
    @RequiresPermission("model:form:edit")
    public ApiResponse<CommitEntity> merge(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(merges.merge(id, user(authentication)));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record BranchRequest(@NotBlank String name, Long parentBranchId) {}

    public record ProtectRequest(@JsonProperty("protected") boolean enabled) {}

    public record MergeRequest(Long sourceBranchId, Long targetBranchId, @NotBlank String title, String description) {}

    public record ResolveRequest(JsonNode resolutions) {}

    public record ReviewRequest(String decision, String comment, String elementUid) {}
}
