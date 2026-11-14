package cn.muxicloud.mop.ai.bridge;

import cn.muxicloud.mop.model.api.PublishedModelLookup;
import cn.muxicloud.mop.semantic.api.SemanticFacade;

/** AI 流水线可以读模型和语义结果，不能反向被它们依赖。 */
public final class AiModuleAccess {
    private final PublishedModelLookup models;
    private final SemanticFacade semantic;

    public AiModuleAccess(PublishedModelLookup models, SemanticFacade semantic) {
        this.models = models;
        this.semantic = semantic;
    }

    public PublishedModelLookup models() {
        return models;
    }

    public SemanticFacade semantic() {
        return semantic;
    }
}
