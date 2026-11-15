package cn.muxicloud.mop.ai.service;

import cn.muxicloud.mop.ai.config.AiKeyProperties;
import cn.muxicloud.mop.ai.crypto.KeyCipher;
import cn.muxicloud.mop.ai.entity.AiCandidateEntity;
import cn.muxicloud.mop.ai.entity.AiExtractionItemEntity;
import cn.muxicloud.mop.ai.entity.AiEvidenceEntity;
import cn.muxicloud.mop.ai.entity.AiJobEntity;
import cn.muxicloud.mop.ai.entity.AiJobEventEntity;
import cn.muxicloud.mop.ai.entity.AiModelEntity;
import cn.muxicloud.mop.ai.entity.AiUsageEntity;
import cn.muxicloud.mop.ai.extract.ExtractionPipeline;
import cn.muxicloud.mop.ai.gateway.ModelRateLimiter;
import cn.muxicloud.mop.ai.gateway.OpenAiChatClient;
import cn.muxicloud.mop.ai.mapper.AiCandidateMapper;
import cn.muxicloud.mop.ai.mapper.AiExtractionItemMapper;
import cn.muxicloud.mop.ai.mapper.AiEvidenceMapper;
import cn.muxicloud.mop.ai.mapper.AiJobEventMapper;
import cn.muxicloud.mop.ai.mapper.AiJobMapper;
import cn.muxicloud.mop.ai.mapper.AiModelMapper;
import cn.muxicloud.mop.ai.mapper.AiUsageMapper;
import cn.muxicloud.mop.ai.pipeline.RoundPlanner;
import cn.muxicloud.mop.basic.api.DocumentText;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.model.api.ElementWriter;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ModelMarketService {
    private static final Logger log = LoggerFactory.getLogger(ModelMarketService.class);
    private final AiModelMapper modelMapper;
    private final AiUsageMapper usageMapper;
    private final ModelRateLimiter rateLimiter;
    private final AiKeyProperties secrets;
    private final DocumentText documents;
    private final ElementWriter elementWriter;
    private final BranchCatalog catalog;
    private final AiJobMapper jobMapper;
    private final AiEvidenceMapper evidenceMapper;
    private final AiCandidateMapper candidateMapper;
    private final AiJobEventMapper eventMapper;
    private final AiExtractionItemMapper extractionItemMapper;
    private final OpenAiChatClient client = new OpenAiChatClient();
    private final ObjectMapper json = new ObjectMapper();
    private final RoundPlanner planner = new RoundPlanner();
    private final ExtractionPipeline extraction = new ExtractionPipeline();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public ModelMarketService(
            AiModelMapper modelMapper,
            AiUsageMapper usageMapper,
            ModelRateLimiter rateLimiter,
            AiKeyProperties secrets,
            DocumentText documents,
            ElementWriter elementWriter,
            BranchCatalog catalog,
            AiJobMapper jobMapper,
            AiEvidenceMapper evidenceMapper,
            AiCandidateMapper candidateMapper,
            AiJobEventMapper eventMapper,
            AiExtractionItemMapper extractionItemMapper) {
        this.modelMapper = modelMapper;
        this.usageMapper = usageMapper;
        this.rateLimiter = rateLimiter;
        this.secrets = secrets;
        this.documents = documents;
        this.elementWriter = elementWriter;
        this.catalog = catalog;
        this.jobMapper = jobMapper;
        this.evidenceMapper = evidenceMapper;
        this.candidateMapper = candidateMapper;
        this.eventMapper = eventMapper;
        this.extractionItemMapper = extractionItemMapper;
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

    public AiJobEntity startJob(long projectId, long branchId, long fileId, long modelId, boolean background, AuthenticatedUser actor) {
        DocumentText.Extracted document = documents.read(fileId);
        AiJobEntity job = new AiJobEntity();
        job.setTenantId(actor.tenantId());
        job.setProjectId(projectId);
        job.setBranchId(branchId);
        job.setFileId(fileId);
        job.setModelId(modelId);
        job.setKind("MODELING");
        job.setStatus("PAUSED");
        job.setStage("PARSE");
        job.setPromptVersion("v1");
        job.setCreatedBy(actor.userId());
        job.setCreatedAt(LocalDateTime.now());
        jobMapper.insert(job);
        int block = 1;
        for (String window : RoundPlanner.windows(document.text())) {
            AiEvidenceEntity evidence = new AiEvidenceEntity();
            evidence.setTenantId(actor.tenantId());
            evidence.setJobId(job.getId());
            evidence.setFileId(fileId);
            evidence.setBlockNo(block);
            evidence.setContent(window);
            evidence.setContentHash(sha(window));
            evidenceMapper.insert(evidence);
            event(job, "PARSE", "证据块 " + block + "：" + shorten(window));
            block++;
        }
        event(job, "PARSE", "文档已解析，等待四轮生成");
        if (background) {
            workers.submit(() -> runQuietly(job.getId(), null, actor));
        }
        return jobMapper.selectById(job.getId());
    }

    public AiJobEntity resume(long jobId, String throughStage, AuthenticatedUser actor) {
        runQuietly(jobId, throughStage, actor);
        return requiredJob(jobId);
    }

    public AiJobEntity job(long jobId) {
        return requiredJob(jobId);
    }

    public List<AiJobEventEntity> events(long jobId) {
        requiredJob(jobId);
        return eventMapper.selectList(new LambdaQueryWrapper<AiJobEventEntity>().eq(AiJobEventEntity::getJobId, jobId).orderByAsc(AiJobEventEntity::getSeqNo));
    }

    public List<AiCandidateEntity> candidates(long jobId) {
        requiredJob(jobId);
        return candidateMapper.selectList(new LambdaQueryWrapper<AiCandidateEntity>().eq(AiCandidateEntity::getJobId, jobId).orderByAsc(AiCandidateEntity::getId));
    }

    public List<AiEvidenceEntity> evidence(long jobId) {
        requiredJob(jobId);
        return evidenceMapper.selectList(new LambdaQueryWrapper<AiEvidenceEntity>().eq(AiEvidenceEntity::getJobId, jobId).orderByAsc(AiEvidenceEntity::getBlockNo));
    }

    public SseEmitter stream(long jobId) {
        requiredJob(jobId);
        SseEmitter emitter = new SseEmitter(120_000L);
        try {
            for (AiJobEventEntity row : events(jobId)) {
                emitter.send(SseEmitter.event().name(row.getStage()).data(row.getMessage()));
            }
            AiJobEntity job = requiredJob(jobId);
            if ("SUCCEEDED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
                emitter.send(SseEmitter.event().name("status").data(job.getStatus()));
                emitter.complete();
            } else {
                emitters.put(jobId, emitter);
                emitter.onCompletion(() -> emitters.remove(jobId, emitter));
                emitter.onTimeout(() -> emitters.remove(jobId, emitter));
            }
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @Transactional
    public AiCandidateEntity editCandidate(long candidateId, String displayName, String apiName) {
        AiCandidateEntity candidate = requiredCandidate(candidateId);
        if (!"PENDING".equals(candidate.getState())) {
            throw new MopException(ErrorCode.VALIDATION, "只有待处理候选可以编辑", null);
        }
        try {
            ObjectNode payload = (ObjectNode) json.readTree(candidate.getPayload());
            if (displayName != null && !displayName.isBlank()) {
                payload.put("displayName", displayName.trim());
            }
            if (apiName != null && !apiName.isBlank()) {
                payload.put("apiName", apiName.trim());
                candidate.setCandidateKey(candidate.getElementType() + ":" + apiName.trim());
            }
            candidate.setPayload(payload.toString());
            candidateMapper.updateById(candidate);
            return candidate;
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "候选内容无法编辑", null);
        }
    }

    public AiCandidateEntity reject(long candidateId) {
        AiCandidateEntity candidate = requiredCandidate(candidateId);
        candidate.setState("REJECTED");
        candidateMapper.updateById(candidate);
        return candidate;
    }

    public AiCandidateEntity adopt(long candidateId, AuthenticatedUser actor) {
        AiCandidateEntity candidate = requiredCandidate(candidateId);
        if (!"PENDING".equals(candidate.getState())) {
            throw new MopException(ErrorCode.VALIDATION, "该候选不能采纳", null);
        }
        AiJobEntity job = requiredJob(candidate.getJobId());
        try {
            ObjectNode payload = commitPayload(job.getBranchId(), candidate);
            ElementWriter.Adopted adopted = elementWriter.add(job.getBranchId(), candidate.getElementType(), payload, actor, "AI 采纳 " + candidate.getElementType());
            candidate.setState("ADOPTED");
            candidate.setAdoptedUid(adopted.uid());
            candidate.setMessage(null);
            candidateMapper.updateById(candidate);
            return candidate;
        } catch (MopException ex) {
            candidate.setMessage(ex.getMessage());
            candidateMapper.updateById(candidate);
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "候选无法写入", null);
        }
    }

    public List<AiCandidateEntity> adoptAll(long jobId, AuthenticatedUser actor) {
        List<String> order = List.of("CQ", "TERM", "ENUM_TYPE", "OBJECT_TYPE", "PROPERTY", "LINK_TYPE", "AXIOM", "DATA_RULE");
        for (String type : order) {
            for (AiCandidateEntity candidate : candidates(jobId)) {
                if (type.equals(candidate.getElementType()) && "PENDING".equals(candidate.getState())) {
                    adopt(candidate.getId(), actor);
                }
            }
        }
        return candidates(jobId);
    }

    private void runQuietly(long jobId, String throughStage, AuthenticatedUser actor) {
        Long previous = TenantContext.get();
        TenantContext.set(actor.tenantId());
        try {
            run(jobId, throughStage, actor);
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private void run(long jobId, String throughStage, AuthenticatedUser actor) {
        AiJobEntity job = requiredJob(jobId);
        if ("SUCCEEDED".equals(job.getStatus())) {
            return;
        }
        String previousStatus = job.getStatus();
        job.setStatus("RUNNING");
        job.setError(null);
        jobMapper.updateById(job);
        List<AiEvidenceEntity> blocks = evidence(jobId);
        List<String> rounds = RoundPlanner.rounds();
        int start = rounds.indexOf(job.getStage());
        if (start < 0) {
            start = 0;
        } else if (!"FAILED".equals(previousStatus)) {
            start++;
        }
        for (int index = start; index < rounds.size(); index++) {
            String round = rounds.get(index);
            job.setStage(round);
            jobMapper.updateById(job);
            event(job, round, "开始 " + round);
            int failed = 0;
            for (AiEvidenceEntity block : blocks) {
                if (!generate(job, block, round, actor)) {
                    failed++;
                }
            }
            if (failed == blocks.size()) {
                job.setStatus("FAILED");
                job.setError(round + " 的模型输出无法通过 JSON Schema 或证据校验");
                job.setFinishedAt(LocalDateTime.now());
                jobMapper.updateById(job);
                event(job, round, job.getError());
                return;
            }
            event(job, round, round + " 已完成");
            if (round.equals(throughStage)) {
                job.setStatus("PAUSED");
                jobMapper.updateById(job);
                event(job, round, "已在 " + round + " 暂停，可以续跑");
                return;
            }
        }
        job.setStatus("SUCCEEDED");
        job.setStage("DONE");
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        event(job, "DONE", "四轮生成结束");
    }

    private boolean generate(AiJobEntity job, AiEvidenceEntity block, String round, AuthenticatedUser actor) {
        String prior = priorIndex(job.getId());
        String output = ask(job, round, planner.prompt(round, block.getContent(), prior), actor);
        List<RoundPlanner.Draft> drafts = tryParse(output, block.getContent());
        if (drafts == null) {
            output = ask(job, round, planner.repair(round, block.getContent()), actor);
            drafts = tryParse(output, block.getContent());
        }
        if (drafts == null && block.getContent().length() > 80) {
            int middle = block.getContent().length() / 2;
            return generateText(job, block, round, block.getContent().substring(0, middle), actor)
                    && generateText(job, block, round, block.getContent().substring(middle), actor);
        }
        if (drafts == null) {
            return false;
        }
        saveDrafts(job, block, drafts);
        return true;
    }

    private boolean generateText(AiJobEntity job, AiEvidenceEntity block, String round, String text, AuthenticatedUser actor) {
        String output = ask(job, round, planner.prompt(round, text, priorIndex(job.getId())), actor);
        List<RoundPlanner.Draft> drafts = tryParse(output, text);
        if (drafts == null) {
            return false;
        }
        saveDrafts(job, block, drafts);
        return true;
    }

    private List<RoundPlanner.Draft> tryParse(String output, String evidence) {
        try {
            return planner.parse(output, evidence);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void saveDrafts(AiJobEntity job, AiEvidenceEntity block, List<RoundPlanner.Draft> drafts) {
        for (RoundPlanner.Draft draft : drafts) {
            String key = draft.type() + ":" + draft.apiName();
            Long existing = candidateMapper.selectCount(new LambdaQueryWrapper<AiCandidateEntity>()
                    .eq(AiCandidateEntity::getJobId, job.getId())
                    .eq(AiCandidateEntity::getCandidateKey, key));
            if (existing != null && existing > 0) {
                continue;
            }
            AiCandidateEntity candidate = new AiCandidateEntity();
            candidate.setTenantId(job.getTenantId());
            candidate.setJobId(job.getId());
            candidate.setCandidateKey(key);
            candidate.setElementType(draft.type());
            candidate.setPayload(draft.source().toString());
            candidate.setEvidenceQuote(draft.evidence());
            candidate.setBlockNo(block.getBlockNo());
            candidate.setConfidence(draft.confidence());
            candidate.setState("PENDING");
            candidateMapper.insert(candidate);
            event(job, job.getStage(), "候选 " + draft.displayName() + "，出处：" + shorten(draft.evidence()));
        }
    }

    private String ask(AiJobEntity job, String round, String prompt, AuthenticatedUser actor) {
        ChatView view = chat(job.getModelId(), List.of(
                new OpenAiChatClient.Message("system", "你只输出 JSON。轮次标记必须保留。"),
                new OpenAiChatClient.Message("user", prompt)), actor);
        return view.content();
    }

    private String priorIndex(long jobId) {
        StringBuilder builder = new StringBuilder();
        for (AiCandidateEntity candidate : candidates(jobId)) {
            builder.append(candidate.getElementType()).append(':').append(candidate.getCandidateKey()).append('\n');
        }
        return builder.toString();
    }

    private ObjectNode commitPayload(long branchId, AiCandidateEntity candidate) throws Exception {
        com.fasterxml.jackson.databind.JsonNode source = json.readTree(candidate.getPayload());
        ObjectNode payload = json.createObjectNode();
        payload.put("displayName", source.path("displayName").asText(candidate.getEvidenceQuote()));
        payload.put("apiName", source.path("apiName").asText());
        String type = candidate.getElementType();
        if ("CQ".equals(type)) {
            payload.put("priority", "P1");
            payload.put("acceptance", candidate.getEvidenceQuote());
        }
        if ("OBJECT_TYPE".equals(type) || "LINK_TYPE".equals(type)) {
            ArrayNode cqs = payload.putArray("cqUids");
            if (source.has("cqApiNames")) {
                for (com.fasterxml.jackson.databind.JsonNode name : source.path("cqApiNames")) {
                    String uid = findUid(branchId, "CQ", name.asText());
                    if (uid != null) {
                        cqs.add(uid);
                    }
                }
            }
        }
        if ("PROPERTY".equals(type)) {
            payload.put("ownerUid", findUid(branchId, "OBJECT_TYPE", source.path("ownerApiName").asText("Transformer")));
            payload.put("required", true);
            payload.put("minCount", source.path("minCount").asInt(1));
            payload.put("maxCount", source.path("maxCount").asInt(1));
            if (source.has("defaultValue")) {
                payload.put("defaultValue", source.path("defaultValue").asText());
            }
            payload.putObject("valueType").put("kind", "BASIC").put("basic", "DECIMAL");
        }
        if ("LINK_TYPE".equals(type)) {
            payload.put("sourceUid", findUid(branchId, "OBJECT_TYPE", source.path("sourceApiName").asText()));
            payload.put("targetUid", findUid(branchId, "OBJECT_TYPE", source.path("targetApiName").asText()));
            payload.put("cardinality", source.path("cardinality").asText("MANY_TO_ONE"));
            payload.put("invertible", false);
        }
        if ("AXIOM".equals(type)) {
            payload.put("kind", source.path("kind").asText("SUBCLASS"));
            payload.put("subjectUid", findUid(branchId, "OBJECT_TYPE", source.path("subjectApiName").asText()));
            payload.put("objectUid", findUid(branchId, "OBJECT_TYPE", source.path("objectApiName").asText()));
        }
        if ("DATA_RULE".equals(type)) {
            payload.put("kind", source.path("kind").asText("RANGE"));
            payload.put("targetObjectUid", findUid(branchId, "OBJECT_TYPE", source.path("targetApiName").asText()));
            payload.put("min", source.path("min").asDouble(0));
            payload.put("max", source.path("max").asDouble(1));
            payload.put("severity", "Violation");
            payload.put("message", candidate.getEvidenceQuote());
        }
        return payload;
    }

    private String findUid(long branchId, String type, String apiName) {
        for (BranchCatalog.CatalogElement element : catalog.snapshot(branchId).elements()) {
            if (type.equals(element.type()) && apiName.equals(element.apiName())) {
                return element.uid();
            }
        }
        return null;
    }

    private void event(AiJobEntity job, String stage, String message) {
        Integer count = eventMapper.selectCount(new LambdaQueryWrapper<AiJobEventEntity>().eq(AiJobEventEntity::getJobId, job.getId())).intValue();
        AiJobEventEntity row = new AiJobEventEntity();
        row.setTenantId(job.getTenantId());
        row.setJobId(job.getId());
        row.setSeqNo(count + 1);
        row.setStage(stage);
        row.setMessage(message);
        row.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(row);
        SseEmitter emitter = emitters.get(job.getId());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(stage).data(message));
                if ("SUCCEEDED".equals(job.getStatus()) || "FAILED".equals(job.getStatus()) || "PAUSED".equals(job.getStatus())) {
                    emitter.complete();
                }
            } catch (Exception ex) {
                emitters.remove(job.getId());
            }
        }
    }

    private AiJobEntity requiredJob(long jobId) {
        AiJobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return job;
    }

    private AiCandidateEntity requiredCandidate(long candidateId) {
        AiCandidateEntity candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return candidate;
    }

    public AiJobEntity extract(long projectId, long branchId, long fileId, long modelId, AuthenticatedUser actor) {
        DocumentText.Extracted document = documents.read(fileId);
        ExtractionPipeline.Index index = ontology(branchId);
        if (index.types().isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, "当前本体还没有对象类型", null);
        }
        AiJobEntity job = new AiJobEntity();
        job.setTenantId(actor.tenantId());
        job.setProjectId(projectId);
        job.setBranchId(branchId);
        job.setFileId(fileId);
        job.setModelId(modelId);
        job.setKind("EXTRACTION");
        job.setStatus("RUNNING");
        job.setStage("EXTRACT");
        job.setPromptVersion("v1");
        job.setCreatedBy(actor.userId());
        job.setCreatedAt(LocalDateTime.now());
        jobMapper.insert(job);
        String schemaText = extraction.schemaText(index);
        String output = ask(job, "EXTRACT", extraction.prompt(schemaText, document.text()), actor);
        List<ExtractionPipeline.Draft> drafts = extraction.interpret(output, document.text(), index);
        if (drafts.stream().anyMatch(item -> "JSON_INVALID".equals(item.reason()) || "JSON_SCHEMA".equals(item.reason()))) {
            output = ask(job, "EXTRACT", extraction.prompt(schemaText, document.text()), actor);
            drafts = extraction.interpret(output, document.text(), index);
        }
        for (ExtractionPipeline.Draft draft : drafts) {
            AiExtractionItemEntity item = new AiExtractionItemEntity();
            item.setTenantId(actor.tenantId());
            item.setJobId(job.getId());
            item.setKind(draft.kind());
            item.setPayload(payloadOf(draft));
            item.setEvidenceQuote(draft.evidence().length() <= 1000 ? draft.evidence() : draft.evidence().substring(0, 1000));
            item.setConfidence(draft.confidence());
            item.setReason(draft.reason());
            item.setState(draft.state());
            extractionItemMapper.insert(item);
        }
        job.setStatus("SUCCEEDED");
        job.setStage("REVIEW");
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    public List<AiExtractionItemEntity> extractionItems(long jobId) {
        requiredJob(jobId);
        return extractionItemMapper.selectList(new LambdaQueryWrapper<AiExtractionItemEntity>().eq(AiExtractionItemEntity::getJobId, jobId).orderByAsc(AiExtractionItemEntity::getId));
    }

    public AiExtractionItemEntity reviewExtraction(long itemId, boolean accept) {
        AiExtractionItemEntity item = requiredExtraction(itemId);
        if (!"REVIEW".equals(item.getState())) {
            throw new MopException(ErrorCode.VALIDATION, "只有待审核结果可以通过或驳回", null);
        }
        item.setState(accept ? "ACCEPTED" : "REJECTED");
        extractionItemMapper.updateById(item);
        return item;
    }

    public PublishBatch publicationBatch(long jobId) {
        List<PublishEntity> entities = new ArrayList<>();
        List<PublishLink> links = new ArrayList<>();
        Map<String, String> types = new HashMap<>();
        for (AiExtractionItemEntity item : extractionItems(jobId)) {
            if (!"ACCEPTED".equals(item.getState())) {
                continue;
            }
            JsonNode payload = readPayload(item);
            if ("ENTITY".equals(item.getKind())) {
                Map<String, String> props = new LinkedHashMap<>();
                payload.path("props").fields().forEachRemaining(entry -> props.put(entry.getKey(), entry.getValue().asText("")));
                props.put("_evidence", item.getEvidenceQuote());
                String name = payload.path("name").asText();
                String type = payload.path("type").asText();
                entities.add(new PublishEntity(type, name, props));
                types.put(name, type);
            }
        }
        for (AiExtractionItemEntity item : extractionItems(jobId)) {
            if (!"ACCEPTED".equals(item.getState()) || !"LINK".equals(item.getKind())) {
                continue;
            }
            JsonNode payload = readPayload(item);
            String from = payload.path("from").asText();
            String to = payload.path("to").asText();
            if (!types.containsKey(from) || !types.containsKey(to)) {
                continue;
            }
            links.add(new PublishLink(payload.path("type").asText(), types.get(from), from, types.get(to), to));
        }
        if (entities.isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, "没有已通过审核的抽取结果", null);
        }
        return new PublishBatch(entities, links);
    }

    public void markPublished(long jobId) {
        for (AiExtractionItemEntity item : extractionItems(jobId)) {
            if ("ACCEPTED".equals(item.getState())) {
                item.setState("PUBLISHED");
                extractionItemMapper.updateById(item);
            }
        }
        AiJobEntity job = requiredJob(jobId);
        job.setStage("PUBLISHED");
        jobMapper.updateById(job);
    }

    private ExtractionPipeline.Index ontology(long branchId) {
        Map<String, String> typeByUid = new HashMap<>();
        Map<String, Set<String>> properties = new LinkedHashMap<>();
        for (BranchCatalog.CatalogElement element : catalog.snapshot(branchId).elements()) {
            if ("OBJECT_TYPE".equals(element.type())) {
                typeByUid.put(element.uid(), element.apiName());
                properties.put(element.apiName(), new LinkedHashSet<>());
            }
        }
        for (BranchCatalog.CatalogElement element : catalog.snapshot(branchId).elements()) {
            if ("PROPERTY".equals(element.type())) {
                String owner = typeByUid.get(element.ownerUid());
                if (owner != null) {
                    properties.get(owner).add(element.apiName());
                }
            }
        }
        Map<String, ExtractionPipeline.TypeSpec> types = new LinkedHashMap<>();
        properties.forEach((name, props) -> types.put(name, new ExtractionPipeline.TypeSpec(name, props)));
        Map<String, ExtractionPipeline.LinkSpec> links = new LinkedHashMap<>();
        for (BranchCatalog.CatalogElement element : catalog.snapshot(branchId).elements()) {
            if (!"LINK_TYPE".equals(element.type())) {
                continue;
            }
            try {
                JsonNode payload = json.readTree(element.payload() == null ? "{}" : element.payload());
                String source = typeByUid.get(payload.path("sourceUid").asText());
                String target = typeByUid.get(payload.path("targetUid").asText());
                if (source != null && target != null) {
                    links.put(element.apiName(), new ExtractionPipeline.LinkSpec(element.apiName(), source, target));
                }
            } catch (Exception ex) {
                throw new MopException(ErrorCode.VALIDATION, "关系定义无法读取", null);
            }
        }
        return new ExtractionPipeline.Index(types, links);
    }

    private String payloadOf(ExtractionPipeline.Draft draft) {
        try {
            ObjectNode payload = json.createObjectNode();
            payload.put("type", draft.typeName());
            if ("ENTITY".equals(draft.kind())) {
                payload.put("name", draft.name());
                ObjectNode props = payload.putObject("props");
                draft.props().forEach(props::put);
            } else {
                payload.put("from", draft.from());
                payload.put("to", draft.to());
            }
            return payload.toString();
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "抽取结果无法保存", null);
        }
    }

    private JsonNode readPayload(AiExtractionItemEntity item) {
        try {
            return json.readTree(item.getPayload());
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "抽取结果无法读取", null);
        }
    }

    private AiExtractionItemEntity requiredExtraction(long itemId) {
        AiExtractionItemEntity item = extractionItemMapper.selectById(itemId);
        if (item == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private static String sha(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record ModelInput(String displayName, String modelName, String vendor, String baseUrl, String apiKey, Integer rpmLimit) {}

    public record ModelView(Long id, String displayName, String modelName, String vendor, String baseUrl, Integer rpmLimit, boolean hasKey) {
        static ModelView from(AiModelEntity entity) {
            return new ModelView(entity.getId(), entity.getDisplayName(), entity.getModelName(), entity.getVendor(), entity.getBaseUrl(), entity.getRpmLimit(), entity.getApiKeyCipher() != null);
        }
    }

    public record Probe(boolean ok, String message, int promptTokens, int completionTokens, long elapsedMs) {}

    public record ChatView(boolean ok, String content, int promptTokens, int completionTokens, long elapsedMs) {}

    public record PublishEntity(String type, String name, Map<String, String> props) {}

    public record PublishLink(String type, String fromType, String from, String toType, String to) {}

    public record PublishBatch(List<PublishEntity> entities, List<PublishLink> links) {}
}
