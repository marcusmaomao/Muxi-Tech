package cn.muxicloud.mop.server.controller;

import cn.muxicloud.mop.ai.service.ModelMarketService;
import cn.muxicloud.mop.ai.service.ModelMarketService.PublishBatch;
import cn.muxicloud.mop.ai.service.ModelMarketService.PublishEntity;
import cn.muxicloud.mop.ai.service.ModelMarketService.PublishLink;
import cn.muxicloud.mop.runtime.graph.GraphWriter;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/extraction-jobs")
public class ExtractionPublishController {
    private final ModelMarketService market;
    private final GraphWriter graph;

    public ExtractionPublishController(ModelMarketService market, GraphWriter graph) {
        this.market = market;
        this.graph = graph;
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("ai:model:edit")
    public ApiResponse<PublishResult> publish(@PathVariable long id, Authentication authentication) {
        AuthenticatedUser actor = (AuthenticatedUser) authentication.getPrincipal();
        PublishBatch batch = market.publicationBatch(id);
        List<GraphWriter.ObjectRow> objects = new ArrayList<>();
        for (PublishEntity entity : batch.entities()) {
            objects.add(new GraphWriter.ObjectRow(entity.type(), entity.name(), "ri.mop." + actor.tenantId() + ".extract." + entity.type() + "." + entity.name(), entity.props()));
        }
        List<GraphWriter.LinkRow> links = new ArrayList<>();
        for (PublishLink link : batch.links()) {
            links.add(new GraphWriter.LinkRow(link.type(), link.fromType(), link.from(), link.toType(), link.to()));
        }
        GraphWriter.WriteCount count = graph.mergeObjects(objects, "EXTRACT");
        int linked = graph.mergeLinks(links, "EXTRACT");
        market.markPublished(id);
        return ApiResponse.ok(new PublishResult(count.inserted(), count.updated(), linked));
    }

    public record PublishResult(int inserted, int updated, int linked) {}
}
