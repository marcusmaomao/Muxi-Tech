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
import cn.muxicloud.mop.semantic.shacl.ShaclEngine;
import cn.muxicloud.mop.semantic.shacl.ShaclEngine.Finding;
import cn.muxicloud.mop.semantic.shacl.ShaclEngine.Result;
import cn.muxicloud.mop.semantic.shacl.ShapeCompiler;
import cn.muxicloud.mop.semantic.shacl.ShapeCompiler.Compiled;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShaclService {
    private final BranchCatalog catalog;
    private final SemRunMapper runMapper;
    private final SemIssueMapper issueMapper;
    private final ShapeCompiler compiler = new ShapeCompiler();
    private final ShaclEngine engine = new ShaclEngine();

    public ShaclService(BranchCatalog catalog, SemRunMapper runMapper, SemIssueMapper issueMapper) {
        this.catalog = catalog;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
    }

    public String shapes(long branchId) {
        return compiled(catalog.snapshot(branchId)).turtle();
    }

    @Transactional
    public Report validate(long branchId, String data, AuthenticatedUser actor) {
        if (data == null || data.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "待校验数据不能为空", null);
        }
        CatalogSnapshot snapshot = catalog.snapshot(branchId);
        String shapes = compiled(snapshot).turtle();
        LocalDateTime started = LocalDateTime.now();
        Result result = engine.check(shapes, data);
        SemRunEntity run = new SemRunEntity();
        run.setTenantId(actor.tenantId());
        run.setProjectId(snapshot.projectId());
        run.setBranchId(branchId);
        run.setKind("SHACL");
        run.setStatus("SUCCEEDED");
        run.setSummary("{\"conforms\":" + result.conforms() + ",\"issues\":" + result.findings().size() + "}");
        run.setStartedAt(started);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.insert(run);
        for (Finding finding : result.findings()) {
            SemIssueEntity issue = new SemIssueEntity();
            issue.setTenantId(actor.tenantId());
            issue.setRunId(run.getId());
            issue.setCheckCode("SHACL");
            issue.setSeverity(finding.severity());
            issue.setFocusNode(clip(finding.focusNode(), 512));
            issue.setResultPath(clip(finding.path(), 512));
            issue.setMessage(clip(finding.message() == null ? "约束未满足" : finding.message(), 1000));
            issue.setState("OPEN");
            issueMapper.insert(issue);
        }
        return report(run, issues(run.getId()), result.conforms());
    }

    public Report report(long runId) {
        SemRunEntity run = runMapper.selectById(runId);
        if (run == null || !"SHACL".equals(run.getKind())) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        boolean conforms = run.getSummary() != null && run.getSummary().contains("\"conforms\":true");
        return report(run, issues(runId), conforms);
    }

    private List<SemIssueEntity> issues(long runId) {
        return issueMapper.selectList(new LambdaQueryWrapper<SemIssueEntity>()
                .eq(SemIssueEntity::getRunId, runId)
                .orderByAsc(SemIssueEntity::getId));
    }

    private Report report(SemRunEntity run, List<SemIssueEntity> rows, boolean conforms) {
        List<IssueView> issues = rows.stream()
                .map(row -> new IssueView(row.getSeverity(), row.getFocusNode(), row.getResultPath(), row.getMessage()))
                .toList();
        return new Report(run.getId(), conforms, issues);
    }

    private Compiled compiled(CatalogSnapshot snapshot) {
        Compiled compiled = compiler.compile(snapshot);
        if (!compiled.errors().isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, String.join("；", compiled.errors()), null);
        }
        return compiled;
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public record IssueView(String severity, String focusNode, String path, String message) {}

    public record Report(Long runId, boolean conforms, List<IssueView> issues) {}
}
