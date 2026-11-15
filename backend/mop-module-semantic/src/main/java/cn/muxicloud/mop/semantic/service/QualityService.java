package cn.muxicloud.mop.semantic.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.semantic.entity.ReleaseEntity;
import cn.muxicloud.mop.semantic.entity.SemIssueEntity;
import cn.muxicloud.mop.semantic.entity.SemRunEntity;
import cn.muxicloud.mop.semantic.mapper.ReleaseMapper;
import cn.muxicloud.mop.semantic.mapper.SemIssueMapper;
import cn.muxicloud.mop.semantic.mapper.SemRunMapper;
import cn.muxicloud.mop.semantic.quality.CompiledModelBuilder;
import cn.muxicloud.mop.semantic.quality.QualityCheck;
import cn.muxicloud.mop.semantic.quality.QualityCheck.Finding;
import cn.muxicloud.mop.semantic.quality.QualityContext;
import cn.muxicloud.mop.semantic.quality.QualityRules;
import cn.muxicloud.mop.semantic.quality.QualityScore;
import cn.muxicloud.mop.semantic.quality.QualityScore.Scored;
import cn.muxicloud.mop.semantic.reason.ElkClassifier;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.Classification;
import cn.muxicloud.mop.semantic.shacl.ShapeCompiler;
import cn.muxicloud.mop.semantic.shacl.ShapeCompiler.Compiled;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityService {
    private final BranchCatalog catalog;
    private final SemRunMapper runMapper;
    private final SemIssueMapper issueMapper;
    private final ReleaseMapper releaseMapper;
    private final ElkClassifier classifier = new ElkClassifier();
    private final ShapeCompiler shapes = new ShapeCompiler();
    private final ObjectMapper objectMapper;
    private final QualityService self;

    public QualityService(BranchCatalog catalog, SemRunMapper runMapper, SemIssueMapper issueMapper, ReleaseMapper releaseMapper, ObjectMapper objectMapper, @Lazy QualityService self) {
        this.catalog = catalog;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
        this.releaseMapper = releaseMapper;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Transactional
    public QualityView run(long branchId, AuthenticatedUser actor) {
        CatalogSnapshot snapshot = catalog.snapshot(branchId);
        List<Finding> findings = evaluate(snapshot, branchId);
        Scored scored = QualityScore.score(findings);
        SemRunEntity run = new SemRunEntity();
        run.setTenantId(actor.tenantId());
        run.setProjectId(snapshot.projectId());
        run.setBranchId(branchId);
        run.setRev(catalog.headRevision(branchId));
        run.setKind("QUALITY");
        run.setStatus("SUCCEEDED");
        run.setScore(scored.total());
        run.setSummary(write(Map.of("blocked", QualityScore.blocks(findings), "dimensions", scored.dimensions())));
        run.setStartedAt(LocalDateTime.now());
        run.setFinishedAt(LocalDateTime.now());
        runMapper.insert(run);
        for (Finding finding : findings) {
            SemIssueEntity issue = new SemIssueEntity();
            issue.setTenantId(actor.tenantId());
            issue.setRunId(run.getId());
            issue.setCheckCode(finding.code());
            issue.setSeverity(finding.severity());
            issue.setElementUid(finding.elementUid());
            issue.setMessage(clip(finding.message(), 1000));
            issue.setState("OPEN");
            issueMapper.insert(issue);
        }
        return view(run, issues(run.getId()), scored.dimensions());
    }

    public QualityView get(long runId) {
        SemRunEntity run = requiredQuality(runId);
        return view(run, issues(runId), dimensions(run));
    }

    public List<TrendPoint> trend(long projectId) {
        return runMapper.selectList(new LambdaQueryWrapper<SemRunEntity>()
                        .eq(SemRunEntity::getProjectId, projectId)
                        .eq(SemRunEntity::getKind, "QUALITY")
                        .orderByAsc(SemRunEntity::getId))
                .stream()
                .map(run -> new TrendPoint(run.getId(), run.getScore(), run.getFinishedAt()))
                .toList();
    }

    @Transactional
    public QualityView updateIssue(long issueId, String state, String reason) {
        SemIssueEntity issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        if ("IGNORED".equals(state) && (reason == null || reason.isBlank())) {
            throw new MopException(ErrorCode.VALIDATION, "忽略问题必须填写理由", null);
        }
        if (!"OPEN".equals(state) && !"IGNORED".equals(state) && !"FIXED".equals(state)) {
            throw new MopException(ErrorCode.VALIDATION, "问题状态只能是待处理、已忽略或已修复", null);
        }
        issue.setState(state);
        issue.setIgnoreReason("IGNORED".equals(state) ? reason.trim() : null);
        issueMapper.updateById(issue);
        SemRunEntity run = requiredQuality(issue.getRunId());
        List<SemIssueEntity> rows = issues(run.getId());
        Scored scored = QualityScore.score(rows.stream().filter(row -> "OPEN".equals(row.getState())).map(this::finding).toList());
        run.setScore(scored.total());
        run.setSummary(write(Map.of("blocked", QualityScore.blocks(rows.stream().filter(row -> "OPEN".equals(row.getState())).map(this::finding).toList()), "dimensions", scored.dimensions())));
        runMapper.updateById(run);
        return view(run, rows, scored.dimensions());
    }

    public ReleaseView publish(long branchId, String version, AuthenticatedUser actor) {
        String clean = version == null ? "" : version.trim();
        if (clean.isBlank() || clean.length() > 32) {
            throw new MopException(ErrorCode.VALIDATION, "版本号不能为空，且最长 32", null);
        }
        QualityView quality = self.run(branchId, actor);
        if (quality.blocked()) {
            throw new MopException(ErrorCode.RELEASE_GATE, "发布门禁未通过", Map.of("runId", quality.runId(), "score", quality.score()));
        }
        return self.storeRelease(branchId, clean, quality.runId(), actor);
    }

    @Transactional
    public ReleaseView storeRelease(long branchId, String version, long qualityRunId, AuthenticatedUser actor) {
        CatalogSnapshot snapshot = catalog.snapshot(branchId);
        Long existing = releaseMapper.selectCount(new LambdaQueryWrapper<ReleaseEntity>()
                .eq(ReleaseEntity::getProjectId, snapshot.projectId())
                .eq(ReleaseEntity::getVersion, version));
        if (existing != null && existing > 0) {
            throw new MopException(ErrorCode.VALIDATION, "这个版本号已经发布过", null);
        }
        SemRunEntity cq = latestCq(branchId);
        ReleaseEntity release = new ReleaseEntity();
        release.setTenantId(actor.tenantId());
        release.setProjectId(snapshot.projectId());
        release.setVersion(version);
        release.setBranchId(branchId);
        release.setRev(catalog.headRevision(branchId));
        release.setCompiledModel(write(CompiledModelBuilder.build(snapshot, release.getRev())));
        release.setQualityRunId(qualityRunId);
        release.setCqRunId(cq == null ? null : cq.getId());
        release.setStatus("PUBLISHED");
        release.setPublishedBy(actor.userId());
        release.setPublishedAt(LocalDateTime.now());
        releaseMapper.insert(release);
        return new ReleaseView(release.getId(), release.getVersion(), release.getStatus(), release.getQualityRunId(), release.getRev());
    }

    public List<ReleaseView> releases(long projectId) {
        return releaseMapper.selectList(new LambdaQueryWrapper<ReleaseEntity>()
                        .eq(ReleaseEntity::getProjectId, projectId)
                        .orderByDesc(ReleaseEntity::getId))
                .stream()
                .map(row -> new ReleaseView(row.getId(), row.getVersion(), row.getStatus(), row.getQualityRunId(), row.getRev()))
                .toList();
    }

    public Map<String, Object> compiledModel(long releaseId) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        try {
            return objectMapper.readValue(release.getCompiledModel(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "编译结果无法读取", null);
        }
    }

    private List<Finding> evaluate(CatalogSnapshot snapshot, long branchId) {
        Classification classification = null;
        try {
            classification = classifier.classify(snapshot);
        } catch (MopException ex) {
            classification = null;
        }
        Compiled compiled = shapes.compile(snapshot);
        boolean tests = snapshot.elements().stream().anyMatch(element -> "CQ_TEST".equals(element.type()));
        SemRunEntity cq = latestCq(branchId);
        Boolean gate = null;
        Double elementRate = null;
        if (cq != null && cq.getSummary() != null) {
            try {
                JsonNode summary = objectMapper.readTree(cq.getSummary());
                gate = summary.path("gateOpen").asBoolean(false);
                if (summary.has("coverage") && summary.get("coverage").has("elementRate")) {
                    elementRate = summary.get("coverage").path("elementRate").asDouble();
                }
            } catch (Exception ex) {
                gate = false;
            }
        }
        QualityContext context = new QualityContext(snapshot, classification, compiled.errors(), gate, tests, cq != null, elementRate);
        List<Finding> findings = new ArrayList<>();
        for (QualityCheck check : QualityRules.checks()) {
            findings.addAll(check.run(context));
        }
        if (classification == null) {
            findings.add(new Finding("SEM_INCONSISTENT", "SEMANTIC", "Violation", null, "ELK 分类失败"));
        }
        return findings;
    }

    private SemRunEntity latestCq(long branchId) {
        return runMapper.selectOne(new LambdaQueryWrapper<SemRunEntity>()
                .eq(SemRunEntity::getBranchId, branchId)
                .eq(SemRunEntity::getKind, "CQ")
                .orderByDesc(SemRunEntity::getId)
                .last("LIMIT 1"));
    }

    private SemRunEntity requiredQuality(long runId) {
        SemRunEntity run = runMapper.selectById(runId);
        if (run == null || !"QUALITY".equals(run.getKind())) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return run;
    }

    private List<SemIssueEntity> issues(long runId) {
        return issueMapper.selectList(new LambdaQueryWrapper<SemIssueEntity>()
                .eq(SemIssueEntity::getRunId, runId)
                .orderByAsc(SemIssueEntity::getId));
    }

    private QualityView view(SemRunEntity run, List<SemIssueEntity> rows, Map<String, BigDecimal> dimensions) {
        boolean blocked = QualityScore.blocks(rows.stream().filter(row -> "OPEN".equals(row.getState())).map(this::finding).toList());
        List<IssueView> issues = rows.stream()
                .map(row -> new IssueView(row.getId(), row.getCheckCode(), row.getSeverity(), row.getElementUid(), row.getMessage(), row.getState(), row.getIgnoreReason()))
                .toList();
        return new QualityView(run.getId(), run.getScore(), blocked, dimensions, issues);
    }

    private Map<String, BigDecimal> dimensions(SemRunEntity run) {
        try {
            JsonNode node = objectMapper.readTree(run.getSummary()).path("dimensions");
            Map<String, BigDecimal> dimensions = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> dimensions.put(field.getKey(), field.getValue().decimalValue()));
            return dimensions;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Finding finding(SemIssueEntity issue) {
        String dimension = switch (issue.getCheckCode() == null ? "" : issue.getCheckCode()) {
            case String code when code.startsWith("SEM_") -> "SEMANTIC";
            case String code when code.startsWith("CON_") -> "CONSTRAINT";
            case String code when code.startsWith("GOAL_") -> "GOAL";
            default -> "STRUCTURE";
        };
        return new Finding(issue.getCheckCode(), dimension, issue.getSeverity(), issue.getElementUid(), issue.getMessage());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "质量结果无法保存", null);
        }
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public record IssueView(Long id, String code, String severity, String elementUid, String message, String state, String ignoreReason) {}

    public record QualityView(Long runId, BigDecimal score, boolean blocked, Map<String, BigDecimal> dimensions, List<IssueView> issues) {}

    public record TrendPoint(Long runId, BigDecimal score, LocalDateTime finishedAt) {}

    public record ReleaseView(Long id, String version, String status, Long qualityRunId, Long revision) {}
}
