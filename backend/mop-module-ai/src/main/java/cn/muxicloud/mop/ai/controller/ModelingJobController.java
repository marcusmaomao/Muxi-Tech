package cn.muxicloud.mop.ai.controller;

import cn.muxicloud.mop.ai.entity.AiCandidateEntity;
import cn.muxicloud.mop.ai.entity.AiEvidenceEntity;
import cn.muxicloud.mop.ai.entity.AiJobEntity;
import cn.muxicloud.mop.ai.entity.AiJobEventEntity;
import cn.muxicloud.mop.ai.service.ModelMarketService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai")
public class ModelingJobController {
    private final ModelMarketService market;

    public ModelingJobController(ModelMarketService market) {
        this.market = market;
    }

    @PostMapping("/modeling-jobs")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiJobEntity> start(@RequestBody StartRequest request, Authentication authentication) {
        return ApiResponse.ok(market.startJob(request.projectId(), request.branchId(), request.fileId(), request.modelId(), request.background(), user(authentication)));
    }

    @GetMapping("/jobs/{id}")
    @RequiresPermission("ai:model:list")
    public ApiResponse<AiJobEntity> job(@PathVariable Long id) {
        return ApiResponse.ok(market.job(id));
    }

    @GetMapping(value = "/jobs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiresPermission("ai:model:list")
    public SseEmitter events(@PathVariable Long id) {
        return market.stream(id);
    }

    @GetMapping("/jobs/{id}/log")
    @RequiresPermission("ai:model:list")
    public ApiResponse<List<AiJobEventEntity>> log(@PathVariable Long id) {
        return ApiResponse.ok(market.events(id));
    }

    @PostMapping("/jobs/{id}/resume")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiJobEntity> resume(@PathVariable Long id, @RequestParam(required = false) String through, Authentication authentication) {
        return ApiResponse.ok(market.resume(id, through, user(authentication)));
    }

    @GetMapping("/jobs/{id}/candidates")
    @RequiresPermission("ai:model:list")
    public ApiResponse<List<AiCandidateEntity>> candidates(@PathVariable Long id) {
        return ApiResponse.ok(market.candidates(id));
    }

    @GetMapping("/jobs/{id}/evidence")
    @RequiresPermission("ai:model:list")
    public ApiResponse<List<AiEvidenceEntity>> evidence(@PathVariable Long id) {
        return ApiResponse.ok(market.evidence(id));
    }

    @PutMapping("/candidates/{id}")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiCandidateEntity> edit(@PathVariable Long id, @RequestBody EditRequest request) {
        return ApiResponse.ok(market.editCandidate(id, request.displayName(), request.apiName()));
    }

    @PostMapping("/candidates/{id}/reject")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiCandidateEntity> reject(@PathVariable Long id) {
        return ApiResponse.ok(market.reject(id));
    }

    @PostMapping("/candidates/{id}/adopt")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiCandidateEntity> adopt(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(market.adopt(id, user(authentication)));
    }

    @PostMapping("/jobs/{id}/adopt-all")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<List<AiCandidateEntity>> adoptAll(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(market.adoptAll(id, user(authentication)));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record StartRequest(long projectId, long branchId, long fileId, long modelId, boolean background) {}

    public record EditRequest(String displayName, String apiName) {}
}
