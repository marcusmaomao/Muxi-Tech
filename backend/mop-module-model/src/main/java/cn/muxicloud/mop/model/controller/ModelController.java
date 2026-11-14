package cn.muxicloud.mop.model.controller;

import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.CommitEntity;
import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.entity.ProjectEntity;
import cn.muxicloud.mop.model.entity.ProjectMemberEntity;
import cn.muxicloud.mop.model.service.ModelService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
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
public class ModelController {
    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/projects")
    @RequiresPermission("basic:project:list")
    public ApiResponse<List<ProjectEntity>> projects() {
        return ApiResponse.ok(modelService.projects());
    }

    @GetMapping("/projects/{id}")
    @RequiresPermission("basic:project:list")
    public ApiResponse<ProjectEntity> project(@PathVariable Long id) {
        return ApiResponse.ok(modelService.project(id));
    }

    @PostMapping("/projects")
    @RequiresPermission("basic:project:create")
    public ApiResponse<ProjectEntity> createProject(@Valid @RequestBody ProjectRequest request, Authentication authentication) {
        return ApiResponse.ok(modelService.createProject(
                request.code(), request.name(), request.namespace(), request.domain(), request.scopeInclude(),
                request.scopeExclude(), request.description(), user(authentication)));
    }

    @PutMapping("/projects/{id}")
    @RequiresPermission("basic:project:update")
    public ApiResponse<ProjectEntity> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectUpdate request) {
        return ApiResponse.ok(modelService.updateProject(
                id, request.name(), request.namespace(), request.domain(), request.scopeInclude(),
                request.scopeExclude(), request.description(), request.ownerUserId()));
    }

    @DeleteMapping("/projects/{id}")
    @RequiresPermission("basic:project:delete")
    public ApiResponse<Void> deleteProject(@PathVariable Long id) {
        modelService.deleteProject(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/projects/{id}/members")
    @RequiresPermission("basic:project:member")
    public ApiResponse<List<ProjectMemberEntity>> members(@PathVariable Long id) {
        return ApiResponse.ok(modelService.members(id));
    }

    @PutMapping("/projects/{id}/members")
    @RequiresPermission("basic:project:member")
    public ApiResponse<List<ProjectMemberEntity>> replaceMembers(@PathVariable Long id, @RequestBody MembersRequest request) {
        List<ModelService.MemberInput> members = request.members() == null
                ? List.of()
                : request.members().stream().map(member -> new ModelService.MemberInput(member.userId(), member.role())).toList();
        return ApiResponse.ok(modelService.replaceMembers(id, members));
    }

    @GetMapping("/projects/{id}/branches")
    public ApiResponse<List<BranchEntity>> branches(@PathVariable Long id) {
        return ApiResponse.ok(modelService.branches(id));
    }

    @GetMapping("/branches/{id}/elements")
    public ApiResponse<List<ElementEntity>> elements(@PathVariable Long id, @RequestParam(required = false) String type) {
        return ApiResponse.ok(modelService.elements(id, type));
    }

    @PostMapping("/branches/{id}/commits")
    public ApiResponse<CommitEntity> commit(@PathVariable Long id, @RequestBody CommitRequest request, Authentication authentication) {
        List<ModelService.ChangeInput> changes = request.changes().stream()
                .map(change -> new ModelService.ChangeInput(change.op(), change.uid(), change.type(), change.apiName(), change.payload()))
                .toList();
        return ApiResponse.ok(modelService.commit(id, request.message(), changes, user(authentication)));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record ProjectRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String namespace,
            String domain,
            String scopeInclude,
            String scopeExclude,
            String description) {}

    public record ProjectUpdate(
            @NotBlank String name,
            @NotBlank String namespace,
            String domain,
            String scopeInclude,
            String scopeExclude,
            String description,
            Long ownerUserId) {}

    public record MembersRequest(List<MemberRequest> members) {}

    public record MemberRequest(Long userId, String role) {}
    public record CommitRequest(@NotBlank String message, List<ChangeRequest> changes) {}
    public record ChangeRequest(String op, String uid, String type, String apiName, JsonNode payload) {}
}
