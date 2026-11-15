package cn.muxicloud.mop.ai.controller;

import cn.muxicloud.mop.ai.entity.AiUsageEntity;
import cn.muxicloud.mop.ai.gateway.OpenAiChatClient;
import cn.muxicloud.mop.ai.service.ModelMarketService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class ModelMarketController {
    private final ModelMarketService market;

    public ModelMarketController(ModelMarketService market) {
        this.market = market;
    }

    @GetMapping("/models")
    @RequiresPermission("ai:model:list")
    public ApiResponse<List<ModelMarketService.ModelView>> list() {
        return ApiResponse.ok(market.list());
    }

    @PostMapping("/models")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<ModelMarketService.ModelView> create(@RequestBody ModelRequest request, Authentication authentication) {
        return ApiResponse.ok(market.create(request.toInput(), user(authentication)));
    }

    @PutMapping("/models/{id}")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<ModelMarketService.ModelView> update(@PathVariable Long id, @RequestBody ModelRequest request, Authentication authentication) {
        return ApiResponse.ok(market.update(id, request.toInput(), user(authentication)));
    }

    @DeleteMapping("/models/{id}")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        market.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/models/{id}/test")
    @RequiresPermission("ai:model:test")
    public ApiResponse<ModelMarketService.Probe> test(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(market.test(id, user(authentication)));
    }

    @PostMapping("/chat")
    @RequiresPermission("ai:chat")
    public ApiResponse<ModelMarketService.ChatView> chat(@RequestBody ChatRequest request, Authentication authentication) {
        List<OpenAiChatClient.Message> messages = request.messages() == null
                ? List.of()
                : request.messages().stream().map(message -> new OpenAiChatClient.Message(message.role(), message.content())).toList();
        return ApiResponse.ok(market.chat(request.modelId(), messages, user(authentication)));
    }

    @GetMapping("/usage")
    @RequiresPermission("ai:model:list")
    public ApiResponse<List<AiUsageEntity>> usage(@RequestParam(required = false) Long modelId) {
        return ApiResponse.ok(market.usage(modelId));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record ModelRequest(
            @NotBlank String displayName,
            @NotBlank String modelName,
            @NotBlank String vendor,
            @NotBlank String baseUrl,
            String apiKey,
            Integer rpmLimit) {
        ModelMarketService.ModelInput toInput() {
            return new ModelMarketService.ModelInput(displayName, modelName, vendor, baseUrl, apiKey, rpmLimit);
        }
    }

    public record ChatRequest(Long modelId, List<ChatMessage> messages) {}

    public record ChatMessage(String role, String content) {}
}
