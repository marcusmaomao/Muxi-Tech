package cn.muxicloud.mop.runtime.controller;

import cn.muxicloud.mop.runtime.graph.GraphWriter;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.MappingDefinition;
import cn.muxicloud.mop.runtime.service.MappingService;
import cn.muxicloud.mop.runtime.service.MappingService.MappingView;
import cn.muxicloud.mop.runtime.service.MappingService.Preview;
import cn.muxicloud.mop.runtime.service.MappingService.RunView;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
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
public class SyncController {
    private final MappingService mappings;

    public SyncController(MappingService mappings) {
        this.mappings = mappings;
    }

    @PutMapping("/branches/{branchId}/bindings")
    @RequiresPermission("runtime:sync")
    public ApiResponse<MappingView> save(@PathVariable long branchId, @RequestBody SaveRequest request, Authentication authentication) {
        return ApiResponse.ok(mappings.save(branchId, request.datasourceId(), request.definition(), user(authentication)));
    }

    @GetMapping("/branches/{branchId}/bindings")
    @RequiresPermission("runtime:sync")
    public ApiResponse<List<MappingView>> list(@PathVariable long branchId, @RequestParam long datasourceId) {
        return ApiResponse.ok(mappings.list(branchId, datasourceId));
    }

    @PostMapping("/bindings/{id}/preview")
    @RequiresPermission("runtime:sync")
    public ApiResponse<Preview> preview(@PathVariable long id, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(mappings.preview(id, limit));
    }

    @PostMapping("/bindings/{id}/sync")
    @RequiresPermission("runtime:sync")
    public ApiResponse<RunView> sync(@PathVariable long id, @RequestBody ModeRequest request, Authentication authentication) {
        return ApiResponse.ok(mappings.sync(id, request.mode(), user(authentication)));
    }

    @GetMapping("/sync-runs")
    @RequiresPermission("runtime:sync")
    public ApiResponse<List<RunView>> runs(@RequestParam long branchId) {
        return ApiResponse.ok(mappings.runs(branchId));
    }

    @GetMapping("/sync-runs/{id}")
    @RequiresPermission("runtime:sync")
    public ApiResponse<RunView> run(@PathVariable long id) {
        return ApiResponse.ok(mappings.run(id));
    }

    @GetMapping("/graph/objects")
    @RequiresPermission("runtime:sync")
    public ApiResponse<List<GraphWriter.GraphObject>> objects(@RequestParam String type) {
        return ApiResponse.ok(mappings.objects(type));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record SaveRequest(long datasourceId, MappingDefinition definition) {}

    public record ModeRequest(String mode) {}
}
