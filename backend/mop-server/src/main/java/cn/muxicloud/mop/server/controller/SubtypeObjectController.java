package cn.muxicloud.mop.server.controller;

import cn.muxicloud.mop.runtime.graph.GraphWriter;
import cn.muxicloud.mop.runtime.graph.GraphWriter.TypedObject;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.service.ReasonService;
import cn.muxicloud.mop.web.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubtypeObjectController {
    private final ReasonService reason;
    private final GraphWriter graph;

    public SubtypeObjectController(ReasonService reason, GraphWriter graph) {
        this.reason = reason;
        this.graph = graph;
    }

    @GetMapping("/api/v1/branches/{branchId}/objects")
    @RequiresPermission("model:form:edit")
    public ApiResponse<List<TypedObject>> objects(@PathVariable Long branchId, @RequestParam String type,
                                                  @RequestParam(defaultValue = "false") boolean includeSubtypes) {
        return ApiResponse.ok(graph.objectsIn(reason.types(branchId, type, includeSubtypes)));
    }
}
