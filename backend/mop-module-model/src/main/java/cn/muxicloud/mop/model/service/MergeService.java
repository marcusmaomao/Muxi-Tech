package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.CommitEntity;
import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.entity.MergeRequestEntity;
import cn.muxicloud.mop.model.entity.MrReviewEntity;
import cn.muxicloud.mop.model.mapper.BranchMapper;
import cn.muxicloud.mop.model.mapper.CommitMapper;
import cn.muxicloud.mop.model.mapper.MergeRequestMapper;
import cn.muxicloud.mop.model.mapper.MrReviewMapper;
import cn.muxicloud.mop.model.version.MergePlanner;
import cn.muxicloud.mop.model.version.MergePlanner.Conflict;
import cn.muxicloud.mop.model.version.MergePlanner.ElementSide;
import cn.muxicloud.mop.model.version.MergePlanner.Outcome;
import cn.muxicloud.mop.model.version.MergePlanner.Plan;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MergeService {
    private static final List<String> TYPE_ORDER = List.of(
            "CQ", "TERM", "DATA_TYPE", "ENUM_TYPE", "ENUM_VALUE", "OBJECT_TYPE", "PROPERTY", "LINK_TYPE", "AXIOM", "DATA_RULE");

    private final BranchMapper branchMapper;
    private final CommitMapper commitMapper;
    private final MergeRequestMapper mergeRequestMapper;
    private final MrReviewMapper reviewMapper;
    private final BranchReader reader;
    private final ModelService models;
    private final ObjectMapper json;

    public MergeService(
            BranchMapper branchMapper,
            CommitMapper commitMapper,
            MergeRequestMapper mergeRequestMapper,
            MrReviewMapper reviewMapper,
            BranchReader reader,
            ModelService models,
            ObjectMapper json) {
        this.branchMapper = branchMapper;
        this.commitMapper = commitMapper;
        this.mergeRequestMapper = mergeRequestMapper;
        this.reviewMapper = reviewMapper;
        this.reader = reader;
        this.models = models;
        this.json = json;
    }

    @Transactional
    public MergeRequestEntity open(long projectId, long sourceId, long targetId, String title, String description, AuthenticatedUser actor) {
        if (title == null || title.isBlank() || title.trim().length() > 200) {
            throw new MopException(ErrorCode.VALIDATION, "合并请求需要标题，且最长 200", null);
        }
        if (description != null && description.length() > 2000) {
            throw new MopException(ErrorCode.VALIDATION, "说明最长 2000", null);
        }
        BranchEntity source = requiredBranch(projectId, sourceId);
        BranchEntity target = requiredBranch(projectId, targetId);
        if (source.getId().equals(target.getId())) {
            throw new MopException(ErrorCode.VALIDATION, "不能把分支合并到自己", null);
        }
        Long open = mergeRequestMapper.selectCount(new LambdaQueryWrapper<MergeRequestEntity>()
                .eq(MergeRequestEntity::getSourceBranchId, sourceId)
                .eq(MergeRequestEntity::getTargetBranchId, targetId)
                .in(MergeRequestEntity::getStatus, "OPEN", "CONFLICT"));
        if (open != null && open > 0) {
            throw new MopException(ErrorCode.VALIDATION, "这两个分支已有未完成的合并请求", null);
        }
        long baseRev = baseRev(source, target);
        Plan plan = plan(source, target, baseRev, Map.of());
        MergeRequestEntity request = new MergeRequestEntity();
        request.setTenantId(actor.tenantId());
        request.setProjectId(projectId);
        request.setSourceBranchId(sourceId);
        request.setTargetBranchId(targetId);
        request.setBaseRev(baseRev);
        request.setSourceRev(source.getHeadRev());
        request.setTargetRev(target.getHeadRev());
        request.setTitle(title.trim());
        request.setDescription(description == null || description.isBlank() ? null : description.trim());
        request.setStatus(plan.conflicts().isEmpty() ? "OPEN" : "CONFLICT");
        request.setConflicts(json(plan.conflicts()));
        request.setCreatedBy(actor.userId());
        request.setCreatedAt(LocalDateTime.now());
        mergeRequestMapper.insert(request);
        return request;
    }

    public List<MergeRequestEntity> list(long projectId) {
        return mergeRequestMapper.selectList(new LambdaQueryWrapper<MergeRequestEntity>()
                .eq(MergeRequestEntity::getProjectId, projectId)
                .orderByDesc(MergeRequestEntity::getId));
    }

    public MergeRequestEntity get(long id) {
        MergeRequestEntity request = mergeRequestMapper.selectById(id);
        if (request == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return request;
    }

    @Transactional
    public MergeRequestEntity resolve(long id, JsonNode resolutions) {
        MergeRequestEntity request = get(id);
        if ("MERGED".equals(request.getStatus()) || "CLOSED".equals(request.getStatus())) {
            throw new MopException(ErrorCode.VALIDATION, "合并请求已结束", null);
        }
        Map<String, String> choices = choices(resolutions);
        BranchEntity source = branchMapper.selectById(request.getSourceBranchId());
        BranchEntity target = branchMapper.selectById(request.getTargetBranchId());
        Plan plan = plan(source, target, request.getBaseRev(), choices);
        request.setResolutions(resolutions == null ? null : resolutions.toString());
        request.setConflicts(json(plan.conflicts()));
        request.setSourceRev(source.getHeadRev());
        request.setTargetRev(target.getHeadRev());
        request.setStatus(plan.conflicts().isEmpty() ? "OPEN" : "CONFLICT");
        mergeRequestMapper.updateById(request);
        return request;
    }

    @Transactional
    public MrReviewEntity review(long id, String decision, String comment, String elementUid, AuthenticatedUser actor) {
        MergeRequestEntity request = get(id);
        if (decision == null || !decision.matches("APPROVE|COMMENT|REQUEST_CHANGE")) {
            throw new MopException(ErrorCode.VALIDATION, "评审结论只能是 APPROVE、COMMENT 或 REQUEST_CHANGE", null);
        }
        MrReviewEntity review = new MrReviewEntity();
        review.setTenantId(TenantContext.get() == null ? request.getTenantId() : TenantContext.get());
        review.setMrId(request.getId());
        review.setReviewerId(actor.userId());
        review.setDecision(decision);
        review.setComment(comment);
        review.setElementUid(elementUid);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        return review;
    }

    @Transactional
    public CommitEntity merge(long id, AuthenticatedUser actor) {
        MergeRequestEntity request = get(id);
        if ("MERGED".equals(request.getStatus()) || "CLOSED".equals(request.getStatus())) {
            throw new MopException(ErrorCode.VALIDATION, "合并请求已结束", null);
        }
        BranchEntity source = branchMapper.selectById(request.getSourceBranchId());
        BranchEntity target = branchMapper.selectById(request.getTargetBranchId());
        long baseRev = baseRev(source, target);
        Map<String, String> choices = choices(parse(request.getResolutions()));
        Plan plan = plan(source, target, baseRev, choices);
        if (!plan.conflicts().isEmpty()) {
            request.setBaseRev(baseRev);
            request.setSourceRev(source.getHeadRev());
            request.setTargetRev(target.getHeadRev());
            request.setConflicts(json(plan.conflicts()));
            request.setStatus("CONFLICT");
            mergeRequestMapper.updateById(request);
            throw new MopException(ErrorCode.MERGE_CONFLICT, "还有未解决的冲突", Map.of("conflicts", plan.conflicts()));
        }
        List<ModelService.ChangeInput> changes = new ArrayList<>();
        List<Outcome> ordered = new ArrayList<>(plan.changes());
        ordered.sort(Comparator.comparingInt(change -> "DELETE".equals(change.op()) ? 100 : typeRank(change.type())));
        for (Outcome outcome : ordered) {
            changes.add(new ModelService.ChangeInput(outcome.op(), outcome.uid(), outcome.type(), outcome.apiName(), outcome.payload()));
        }
        CommitEntity commit = models.commit(target.getId(), request.getTitle(), changes, actor, "MERGE", source.getId(), source.getHeadRev());
        request.setBaseRev(baseRev);
        request.setSourceRev(source.getHeadRev());
        request.setTargetRev(commit.getRev());
        request.setConflicts("[]");
        request.setStatus("MERGED");
        request.setMergedBy(actor.userId());
        request.setMergedAt(LocalDateTime.now());
        mergeRequestMapper.updateById(request);
        return commit;
    }

    private Plan plan(BranchEntity source, BranchEntity target, long baseRev, Map<String, String> choices) {
        Map<String, ElementSide> base = sides(reader.viewAt(source.getId(), baseRev));
        Map<String, ElementSide> left = sides(reader.view(source.getId()));
        Map<String, ElementSide> right = sides(reader.view(target.getId()));
        return MergePlanner.plan(base, left, right, choices);
    }

    private long baseRev(BranchEntity source, BranchEntity target) {
        CommitEntity last = commitMapper.selectOne(new LambdaQueryWrapper<CommitEntity>()
                .eq(CommitEntity::getBranchId, target.getId())
                .eq(CommitEntity::getMergeSourceBranchId, source.getId())
                .orderByDesc(CommitEntity::getRev)
                .last("LIMIT 1"));
        if (last != null && last.getMergeSourceRev() != null) {
            return last.getMergeSourceRev();
        }
        if (target.getId().equals(source.getParentBranchId())) {
            return source.getForkRev() == null ? 0L : source.getForkRev();
        }
        if (source.getId().equals(target.getParentBranchId())) {
            return target.getForkRev() == null ? 0L : target.getForkRev();
        }
        throw new MopException(ErrorCode.VALIDATION, "请从目标分支拉出源分支后再合并", null);
    }

    private Map<String, ElementSide> sides(List<ElementEntity> rows) {
        Map<String, ElementSide> out = new LinkedHashMap<>();
        for (ElementEntity row : rows) {
            out.put(row.getUid(), new ElementSide(row.getElementType(), row.getApiName(), parse(row.getPayload())));
        }
        return out;
    }

    private Map<String, String> choices(JsonNode resolutions) {
        Map<String, String> choices = new LinkedHashMap<>();
        if (resolutions == null || !resolutions.isArray()) {
            return choices;
        }
        for (JsonNode item : resolutions) {
            String uid = item.path("uid").asText("");
            String choice = item.path("choice").asText("");
            if (!uid.isBlank() && ("SOURCE".equals(choice) || "TARGET".equals(choice))) {
                choices.put(uid, choice);
            }
        }
        return choices;
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return json.readTree(payload);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "合并决议无法读取", null);
        }
    }

    private String json(List<Conflict> conflicts) {
        if (conflicts.isEmpty()) {
            return null;
        }
        ArrayNode array = json.createArrayNode();
        for (Conflict conflict : conflicts) {
            ObjectNode node = array.addObject();
            node.put("uid", conflict.uid());
            node.put("elementType", conflict.type());
            node.put("apiName", conflict.apiName());
            node.put("reason", conflict.reason());
            ArrayNode paths = node.putArray("paths");
            conflict.paths().forEach(paths::add);
            if (conflict.base() != null) {
                node.set("base", conflict.base());
            }
            if (conflict.source() != null) {
                node.set("source", conflict.source());
            }
            if (conflict.target() != null) {
                node.set("target", conflict.target());
            }
        }
        return array.toString();
    }

    private BranchEntity requiredBranch(long projectId, long branchId) {
        BranchEntity branch = branchMapper.selectById(branchId);
        if (branch == null || !branch.getProjectId().equals(projectId) || !"ACTIVE".equals(branch.getStatus())) {
            throw new MopException(ErrorCode.NOT_FOUND, "分支不存在", null);
        }
        return branch;
    }

    private static int typeRank(String type) {
        int index = TYPE_ORDER.indexOf(type);
        return index < 0 ? 50 : index;
    }
}
