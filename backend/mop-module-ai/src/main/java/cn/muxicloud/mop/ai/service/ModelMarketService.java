package cn.muxicloud.mop.ai.service;

import cn.muxicloud.mop.ai.config.AiKeyProperties;
import cn.muxicloud.mop.ai.crypto.KeyCipher;
import cn.muxicloud.mop.ai.entity.AiModelEntity;
import cn.muxicloud.mop.ai.entity.AiUsageEntity;
import cn.muxicloud.mop.ai.gateway.ModelRateLimiter;
import cn.muxicloud.mop.ai.gateway.OpenAiChatClient;
import cn.muxicloud.mop.ai.mapper.AiModelMapper;
import cn.muxicloud.mop.ai.mapper.AiUsageMapper;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelMarketService {
    private static final Logger log = LoggerFactory.getLogger(ModelMarketService.class);
    private final AiModelMapper modelMapper;
    private final AiUsageMapper usageMapper;
    private final ModelRateLimiter rateLimiter;
    private final AiKeyProperties secrets;
    private final OpenAiChatClient client = new OpenAiChatClient();

    public ModelMarketService(AiModelMapper modelMapper, AiUsageMapper usageMapper, ModelRateLimiter rateLimiter, AiKeyProperties secrets) {
        this.modelMapper = modelMapper;
        this.usageMapper = usageMapper;
        this.rateLimiter = rateLimiter;
        this.secrets = secrets;
    }

    public List<ModelView> list() {
        return modelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>().orderByDesc(AiModelEntity::getId))
                .stream().map(ModelView::from).toList();
    }

    public List<AiUsageEntity> usage(Long modelId) {
        LambdaQueryWrapper<AiUsageEntity> query = new LambdaQueryWrapper<AiUsageEntity>().orderByDesc(AiUsageEntity::getId).last("LIMIT 50");
        if (modelId != null) {
            query.eq(AiUsageEntity::getModelId, modelId);
        }
        return usageMapper.selectList(query);
    }

    @Transactional
    public ModelView create(ModelInput input, AuthenticatedUser actor) {
        validate(input, true);
        if (modelMapper.selectCount(new LambdaQueryWrapper<AiModelEntity>().eq(AiModelEntity::getModelName, input.modelName().trim())) > 0) {
            throw new MopException(ErrorCode.VALIDATION, "模型名称已存在", null);
        }
        AiModelEntity entity = new AiModelEntity();
        fill(entity, input, true);
        entity.setTenantId(actor.tenantId());
        entity.setCreatedBy(actor.userId());
        entity.setEnabled(1);
        modelMapper.insert(entity);
        return ModelView.from(entity);
    }

    @Transactional
    public ModelView update(Long id, ModelInput input, AuthenticatedUser actor) {
        AiModelEntity entity = required(id);
        validate(input, false);
        fill(entity, input, input.apiKey() != null && !input.apiKey().isBlank());
        entity.setUpdatedBy(actor.userId());
        modelMapper.updateById(entity);
        return ModelView.from(entity);
    }

    public void delete(Long id) {
        required(id);
        modelMapper.deleteById(id);
    }

    public Probe test(Long id, AuthenticatedUser actor) {
        return invoke(required(id), List.of(new OpenAiChatClient.Message("user", "ping")), actor, true);
    }

    public ChatView chat(Long id, List<OpenAiChatClient.Message> messages, AuthenticatedUser actor) {
        if (messages == null || messages.isEmpty() || messages.size() > 20) {
            throw new MopException(ErrorCode.VALIDATION, "消息不能为空，且最多 20 条", null);
        }
        Probe probe = invoke(required(id), messages, actor, false);
        return new ChatView(probe.ok(), probe.message(), probe.promptTokens(), probe.completionTokens(), probe.elapsedMs());
    }

    private Probe invoke(AiModelEntity model, List<OpenAiChatClient.Message> messages, AuthenticatedUser actor, boolean probe) {
        if (model.getEnabled() != null && model.getEnabled() == 0) {
            throw new MopException(ErrorCode.VALIDATION, "模型已停用", null);
        }
        if (!rateLimiter.allow(actor.tenantId(), model.getId(), model.getRpmLimit() == null ? 60 : model.getRpmLimit())) {
            throw new MopException(ErrorCode.RATE_LIMITED);
        }
        String apiKey = model.getApiKeyCipher() == null ? "" : cipher().decrypt(model.getApiKeyCipher());
        long started = System.nanoTime();
        try {
            OpenAiChatClient.ChatCompletion completion = client.complete(model.getBaseUrl(), model.getModelName(), apiKey, messages);
            int latency = latency(started);
            record(actor.tenantId(), model.getId(), completion.promptTokens(), completion.completionTokens(), latency, true);
            log.info("llm modelId={} status=ok latencyMs={}", model.getId(), latency);
            String message = probe ? "已连通" : completion.content();
            if (probe && completion.content() != null && !completion.content().isBlank()) {
                message = "已连通：" + shorten(completion.content());
            }
            return new Probe(true, message, completion.promptTokens(), completion.completionTokens(), latency);
        } catch (RuntimeException ex) {
            int latency = latency(started);
            record(actor.tenantId(), model.getId(), 0, 0, latency, false);
            log.info("llm modelId={} status=fail latencyMs={}", model.getId(), latency);
            if (probe) {
                return new Probe(false, ex.getMessage(), 0, 0, latency);
            }
            throw ex;
        }
    }

    private void record(Long tenantId, Long modelId, int prompt, int completion, int latency, boolean success) {
        AiUsageEntity usage = new AiUsageEntity();
        usage.setTenantId(tenantId);
        usage.setModelId(modelId);
        usage.setPromptTokens(prompt);
        usage.setCompletionTokens(completion);
        usage.setLatencyMs(latency);
        usage.setSuccess(success ? 1 : 0);
        usage.setCreatedAt(LocalDateTime.now());
        usageMapper.insert(usage);
    }

    private void fill(AiModelEntity entity, ModelInput input, boolean writeKey) {
        entity.setDisplayName(input.displayName().trim());
        entity.setModelName(input.modelName().trim());
        entity.setVendor(input.vendor().trim().toUpperCase());
        entity.setBaseUrl(input.baseUrl().trim());
        entity.setRpmLimit(input.rpmLimit());
        if (writeKey) {
            entity.setApiKeyCipher(cipher().encrypt(input.apiKey()));
        }
    }

    private void validate(ModelInput input, boolean keyRequired) {
        if (input.displayName() == null || input.displayName().isBlank() || input.modelName() == null || input.modelName().isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "请填写显示名和模型名", null);
        }
        if (input.modelName().length() > 128 || input.modelName().contains("\n")) {
            throw new MopException(ErrorCode.VALIDATION, "模型名不合法", null);
        }
        if (input.vendor() == null || input.vendor().isBlank() || input.vendor().length() > 32) {
            throw new MopException(ErrorCode.VALIDATION, "请选择协议来源", null);
        }
        URI uri;
        try {
            uri = URI.create(input.baseUrl().trim());
        } catch (IllegalArgumentException ex) {
            throw new MopException(ErrorCode.VALIDATION, "接口地址不合法", null);
        }
        if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https")) || uri.getUserInfo() != null) {
            throw new MopException(ErrorCode.VALIDATION, "接口地址必须是不含账号的 http 或 https", null);
        }
        if (input.rpmLimit() == null || input.rpmLimit() < 1 || input.rpmLimit() > 10000) {
            throw new MopException(ErrorCode.VALIDATION, "每分钟限额必须在 1 到 10000 之间", null);
        }
        if (keyRequired && (input.apiKey() == null || input.apiKey().isBlank())) {
            throw new MopException(ErrorCode.VALIDATION, "请填写 API 密钥", null);
        }
    }

    private AiModelEntity required(Long id) {
        AiModelEntity entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private KeyCipher cipher() {
        return new KeyCipher(secrets.getKeySecret());
    }

    private static int latency(long started) {
        return (int) ((System.nanoTime() - started) / 1_000_000L);
    }

    private static String shorten(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

    public record ModelInput(String displayName, String modelName, String vendor, String baseUrl, String apiKey, Integer rpmLimit) {}

    public record ModelView(Long id, String displayName, String modelName, String vendor, String baseUrl, Integer rpmLimit, boolean hasKey) {
        static ModelView from(AiModelEntity entity) {
            return new ModelView(entity.getId(), entity.getDisplayName(), entity.getModelName(), entity.getVendor(), entity.getBaseUrl(), entity.getRpmLimit(), entity.getApiKeyCipher() != null);
        }
    }

    public record Probe(boolean ok, String message, int promptTokens, int completionTokens, long elapsedMs) {}

    public record ChatView(boolean ok, String content, int promptTokens, int completionTokens, long elapsedMs) {}
}
