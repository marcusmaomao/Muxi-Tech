package cn.muxicloud.mop.semantic.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.semantic.entity.SemIssueEntity;
import cn.muxicloud.mop.semantic.entity.SemRunEntity;
import cn.muxicloud.mop.semantic.mapper.SemIssueMapper;
import cn.muxicloud.mop.semantic.mapper.SemRunMapper;
import cn.muxicloud.mop.semantic.reason.ElkClassifier;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.Classification;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.NamedClass;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReasonService {
    static final int TIMEOUT_SECONDS = 120;

    private final BranchCatalog catalog;
    private final SemRunMapper runMapper;
    private final SemIssueMapper issueMapper;
    private final ElkClassifier classifier = new ElkClassifier();
    private final ObjectMapper objectMapper;

    public ReasonService(BranchCatalog catalog, SemRunMapper runMapper, SemIssueMapper issueMapper, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
        this.objectMapper = objectMapper;
    }

    public List<String> types(long branchId, String apiName, boolean includeSubtypes) {
        if (apiName == null || apiName.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "请选择对象类型", null);
        }
        return classifier.expand(classify(catalog.snapshot(branchId)), apiName.trim(), includeSubtypes);
    }

    @Transactional
    public ReasonView run(long branchId, String reasoner, AuthenticatedUser actor) {
        String engine = reasoner == null || reasoner.isBlank() ? "ELK" : reasoner.trim().toUpperCase(Locale.ROOT);
        if (!"ELK".equals(engine)) {
            throw new MopException(ErrorCode.VALIDATION, "当前推理只支持 ELK", null);
        }
        CatalogSnapshot snapshot = catalog.snapshot(branchId);
        LocalDateTime started = LocalDateTime.now();
        Classification classification = classify(snapshot);
        SemRunEntity run = new SemRunEntity();
        run.setTenantId(actor.tenantId());
        run.setProjectId(snapshot.projectId());
        run.setBranchId(branchId);
        run.setKind("REASON");
        run.setStatus("SUCCEEDED");
        run.setSummary(write(classification));
        run.setStartedAt(started);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.insert(run);
        for (NamedClass type : classification.unsatisfiable()) {
            SemIssueEntity issue = new SemIssueEntity();
            issue.setTenantId(actor.tenantId());
            issue.setRunId(run.getId());
            issue.setCheckCode("SEM_UNSAT");
            issue.setSeverity("Violation");
            issue.setFocusNode(type.iri());
            issue.setMessage("类不可满足：" + type.displayName());
            issue.setState("OPEN");
            issueMapper.insert(issue);
        }
        return view(run, classification);
    }

    public ReasonView get(long runId) {
        SemRunEntity run = runMapper.selectById(runId);
        if (run == null || !"REASON".equals(run.getKind())) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return view(run, read(run.getSummary()));
    }

    private Classification classify(CatalogSnapshot snapshot) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Classification> future = executor.submit(() -> classifier.classify(snapshot));
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new MopException(ErrorCode.VALIDATION, "推理超过 " + TIMEOUT_SECONDS + " 秒，已停止", null);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof MopException mop) {
                throw mop;
            }
            throw new MopException(ErrorCode.INTERNAL, "ELK 分类失败", null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MopException(ErrorCode.INTERNAL, "ELK 分类失败", null);
        } finally {
            executor.shutdownNow();
        }
    }

    private ReasonView view(SemRunEntity run, Classification classification) {
        List<SemIssueEntity> issues = issueMapper.selectList(new LambdaQueryWrapper<SemIssueEntity>()
                .eq(SemIssueEntity::getRunId, run.getId())
                .orderByAsc(SemIssueEntity::getId));
        return new ReasonView(run.getId(), run.getStatus(), "ELK", classification.consistent(), classification.unsatisfiable(), classification.types(),
                issues.stream().map(issue -> issue.getMessage()).toList());
    }

    private String write(Classification classification) {
        try {
            return objectMapper.writeValueAsString(classification);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "推理结果无法保存", null);
        }
    }

    private Classification read(String summary) {
        try {
            return objectMapper.readValue(summary, Classification.class);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "推理结果无法读取", null);
        }
    }

    public record ReasonView(
            Long runId,
            String status,
            String reasoner,
            boolean consistent,
            List<NamedClass> unsatisfiable,
            List<ElkClassifier.ClassNode> types,
            List<String> messages) {}
}
