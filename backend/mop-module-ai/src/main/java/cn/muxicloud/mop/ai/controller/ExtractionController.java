package cn.muxicloud.mop.ai.controller;

import cn.muxicloud.mop.ai.entity.AiExtractionItemEntity;
import cn.muxicloud.mop.ai.entity.AiJobEntity;
import cn.muxicloud.mop.ai.service.ModelMarketService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class ExtractionController {
    private final ModelMarketService market;

    public ExtractionController(ModelMarketService market) {
        this.market = market;
    }

    @PostMapping("/extraction-jobs")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiJobEntity> start(@RequestBody StartRequest request, Authentication authentication) {
        return ApiResponse.ok(market.extract(request.projectId(), request.branchId(), request.fileId(), request.modelId(), user(authentication)));
    }

    @GetMapping("/extraction-jobs/{id}/items")
    @RequiresPermission("ai:model:list")
    public ApiResponse<List<AiExtractionItemEntity>> items(@PathVariable long id) {
        return ApiResponse.ok(market.extractionItems(id));
    }

    @PostMapping("/extraction-items/{id}/accept")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiExtractionItemEntity> accept(@PathVariable long id) {
        return ApiResponse.ok(market.reviewExtraction(id, true));
    }

    @PostMapping("/extraction-items/{id}/reject")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<AiExtractionItemEntity> reject(@PathVariable long id) {
        return ApiResponse.ok(market.reviewExtraction(id, false));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record StartRequest(long projectId, long branchId, long fileId, long modelId) {}
}
