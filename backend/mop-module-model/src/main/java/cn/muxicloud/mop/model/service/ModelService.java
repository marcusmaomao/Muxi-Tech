package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.common.id.Ulids;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.ChangeEntity;
import cn.muxicloud.mop.model.entity.CommitEntity;
import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.entity.ElementRefEntity;
import cn.muxicloud.mop.model.api.ElementWriter;
import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.model.form.ElementRules;
import cn.muxicloud.mop.model.inherit.EffectiveProperties;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.NameConflict;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.PropNode;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.Report;
import cn.muxicloud.mop.model.inherit.EffectiveProperties.TypeNode;
import cn.muxicloud.mop.model.entity.ProjectEntity;
import cn.muxicloud.mop.model.entity.ProjectMemberEntity;
import cn.muxicloud.mop.model.mapper.BranchMapper;
import cn.muxicloud.mop.model.mapper.ChangeMapper;
import cn.muxicloud.mop.model.mapper.CommitMapper;
import cn.muxicloud.mop.model.mapper.ElementMapper;
import cn.muxicloud.mop.model.mapper.ElementRefMapper;
import cn.muxicloud.mop.model.mapper.ProjectMapper;
import cn.muxicloud.mop.model.mapper.ProjectMemberMapper;
import cn.muxicloud.mop.model.schema.PayloadSchemaValidator;
import cn.muxicloud.mop.model.version.FieldChange;
import cn.muxicloud.mop.model.version.JsonLeaves;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelService implements BranchCatalog, ElementWriter {
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "EDITOR", "VIEWER");

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final BranchMapper branchMapper;
    private final ElementMapper elementMapper;
    private final ElementRefMapper elementRefMapper;
    private final CommitMapper commitMapper;
    private final ChangeMapper changeMapper;
    private final PayloadSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final BranchReader reader;

    public ModelService(
            ProjectMapper projectMapper,
            ProjectMemberMapper memberMapper,
            BranchMapper branchMapper,
            ElementMapper elementMapper,
            ElementRefMapper elementRefMapper,
            CommitMapper commitMapper,
            ChangeMapper changeMapper,
            PayloadSchemaValidator schemaValidator,
            ObjectMapper objectMapper,
            BranchReader reader) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.branchMapper = branchMapper;
        this.elementMapper = elementMapper;
        this.elementRefMapper = elementRefMapper;
        this.commitMapper = commitMapper;
        this.changeMapper = changeMapper;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.reader = reader;
    }

    public List<ProjectEntity> projects() {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>().orderByDesc(ProjectEntity::getId));
    }

    public ProjectEntity project(Long id) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return project;
    }

    @Transactional
    public ProjectEntity createProject(
            String code, String name, String namespace, String domain, String scopeInclude, String scopeExclude,
            String description, AuthenticatedUser actor) {
        requireNamespace(namespace);
        requireText(code, 64, "编码");
        requireText(name, 128, "名称");
        requireOptional(domain, 128, "领域");
        requireOptional(scopeInclude, 1000, "范围包含");
        requireOptional(scopeExclude, 1000, "范围排除");
        requireOptional(description, 1000, "说明");
        if (projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getCode, code)) > 0) {
            throw new MopException(ErrorCode.VALIDATION, "项目编码已存在", null);
        }
        ProjectEntity project = new ProjectEntity();
        project.setTenantId(actor.tenantId());
        project.setCode(code.trim());
        project.setName(name.trim());
        project.setNamespace(namespace.trim());
        project.setDomain(blankToNull(domain));
        project.setScopeInclude(blankToNull(scopeInclude));
        project.setScopeExclude(blankToNull(scopeExclude));
        project.setDescription(blankToNull(description));
        project.setOwnerUserId(actor.userId());
        project.setRequireCqLink(1);
        project.setCreatedBy(actor.userId());
        project.setHeadRev(0L);
        projectMapper.insert(project);
        addMember(project, actor.userId(), "OWNER");
        BranchEntity branch = new BranchEntity();
        branch.setTenantId(actor.tenantId());
        branch.setProjectId(project.getId());
        branch.setName("main");
        branch.setHeadRev(0L);
        branch.setProtectedFlag(0);
        branch.setStatus("ACTIVE");
        branch.setCreatedBy(actor.userId());
        branch.setCreatedAt(LocalDateTime.now());
        branchMapper.insert(branch);
        return project;
    }

    @Transactional
    public ProjectEntity updateProject(
            Long id, String name, String namespace, String domain, String scopeInclude, String scopeExclude,
            String description, Long ownerUserId) {
        ProjectEntity project = project(id);
        requireText(name, 128, "名称");
        requireNamespace(namespace);
        requireOptional(domain, 128, "领域");
        requireOptional(scopeInclude, 1000, "范围包含");
        requireOptional(scopeExclude, 1000, "范围排除");
        requireOptional(description, 1000, "说明");
        project.setName(name.trim());
        project.setNamespace(namespace.trim());
        project.setDomain(blankToNull(domain));
        project.setScopeInclude(blankToNull(scopeInclude));
        project.setScopeExclude(blankToNull(scopeExclude));
        project.setDescription(blankToNull(description));
        if (ownerUserId != null) {
            project.setOwnerUserId(ownerUserId);
        }
        projectMapper.updateById(project);
        return project;
    }

    public void deleteProject(Long id) {
        project(id);
        projectMapper.deleteById(id);
    }

    public List<ProjectMemberEntity> members(Long projectId) {
        project(projectId);
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .orderByAsc(ProjectMemberEntity::getId));
    }

    @Transactional
    public List<ProjectMemberEntity> replaceMembers(Long projectId, List<MemberInput> members) {
        ProjectEntity project = project(projectId);
        Set<Long> seen = new LinkedHashSet<>();
        List<MemberInput> clean = new ArrayList<>();
        for (MemberInput member : members) {
            if (member.userId() == null || !MEMBER_ROLES.contains(member.role())) {
                throw new MopException(ErrorCode.VALIDATION, "成员需要用户和角色 OWNER、EDITOR 或 VIEWER", null);
            }
            if (!seen.add(member.userId())) {
                throw new MopException(ErrorCode.VALIDATION, "同一用户不能重复加入项目", null);
            }
            clean.add(member);
        }
        memberMapper.delete(new LambdaQueryWrapper<ProjectMemberEntity>().eq(ProjectMemberEntity::getProjectId, projectId));
        for (MemberInput member : clean) {
            addMember(project, member.userId(), member.role());
        }
        return members(projectId);
    }

    public List<BranchEntity> branches(Long projectId) {
        return branchMapper.selectList(new LambdaQueryWrapper<BranchEntity>().eq(BranchEntity::getProjectId, projectId));
    }

    @Override
    public CatalogSnapshot snapshot(long branchId) {
        BranchEntity branch = branchMapper.selectById(branchId);
        if (branch == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        ProjectEntity project = projectMapper.selectById(branch.getProjectId());
        if (project == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        List<CatalogElement> rows = elements(branchId, null).stream()
                .map(row -> new CatalogElement(row.getUid(), row.getElementType(), row.getApiName(), row.getOwnerUid(), row.getPayload()))
                .toList();
        return new CatalogSnapshot(project.getNamespace(), rows);
    }

    public List<ElementEntity> elements(Long branchId, String type) {
        List<ElementEntity> rows = reader.view(branchId);
        if (type == null || type.isBlank()) {
            return rows;
        }
        return rows.stream().filter(row -> type.equals(row.getElementType())).toList();
    }

    public List<CommitEntity> commits(Long branchId) {
        requiredBranch(branchId);
        return commitMapper.selectList(new LambdaQueryWrapper<CommitEntity>()
                .eq(CommitEntity::getBranchId, branchId)
                .orderByDesc(CommitEntity::getRev));
    }

    @Transactional
    public BranchEntity createBranch(Long projectId, String name, Long parentBranchId, AuthenticatedUser actor) {
        project(projectId);
        String clean = name == null ? "" : name.trim();
        if (!clean.matches("[\\p{IsHan}A-Za-z0-9][\\p{IsHan}A-Za-z0-9_\\-/]{0,127}")) {
            throw new MopException(ErrorCode.VALIDATION, "分支名需以字母或汉字开头，只能包含字母、数字、汉字、下划线、短横和斜杠", null);
        }
        BranchEntity parent = parentBranchId == null
                ? branchMapper.selectOne(new LambdaQueryWrapper<BranchEntity>()
                        .eq(BranchEntity::getProjectId, projectId)
                        .eq(BranchEntity::getName, "main")
                        .last("LIMIT 1"))
                : branchMapper.selectById(parentBranchId);
        if (parent == null || !parent.getProjectId().equals(projectId) || !"ACTIVE".equals(parent.getStatus())) {
            throw new MopException(ErrorCode.VALIDATION, "父分支不存在", null);
        }
        if (branchMapper.selectCount(new LambdaQueryWrapper<BranchEntity>()
                .eq(BranchEntity::getProjectId, projectId)
                .eq(BranchEntity::getName, clean)) > 0) {
            throw new MopException(ErrorCode.VALIDATION, "分支名已存在", null);
        }
        BranchEntity branch = new BranchEntity();
        branch.setTenantId(actor.tenantId());
        branch.setProjectId(projectId);
        branch.setName(clean);
        branch.setParentBranchId(parent.getId());
        branch.setForkRev(parent.getHeadRev());
        branch.setHeadRev(parent.getHeadRev());
        branch.setProtectedFlag(0);
        branch.setStatus("ACTIVE");
        branch.setCreatedBy(actor.userId());
        branch.setCreatedAt(LocalDateTime.now());
        branchMapper.insert(branch);
        return branch;
    }

    @Transactional
    public BranchEntity protect(Long branchId, boolean enabled) {
        BranchEntity branch = requiredBranch(branchId);
        branch.setProtectedFlag(enabled ? 1 : 0);
        branchMapper.updateById(branch);
        return branch;
    }

    @Transactional
    public void archive(Long branchId) {
        BranchEntity branch = requiredBranch(branchId);
        if ("main".equals(branch.getName())) {
            throw new MopException(ErrorCode.VALIDATION, "不能归档 main", null);
        }
        Long children = branchMapper.selectCount(new LambdaQueryWrapper<BranchEntity>()
                .eq(BranchEntity::getParentBranchId, branchId)
                .eq(BranchEntity::getStatus, "ACTIVE"));
        if (children != null && children > 0) {
            throw new MopException(ErrorCode.VALIDATION, "还有从它拉出的分支，不能归档", null);
        }
        branch.setStatus("ARCHIVED");
        branchMapper.updateById(branch);
    }

    @Transactional
    public CommitEntity revertTo(Long branchId, long rev, AuthenticatedUser actor) {
        BranchEntity branch = requiredBranch(branchId);
        long head = branch.getHeadRev() == null ? 0L : branch.getHeadRev();
        if (rev < 0 || rev > head) {
            throw new MopException(ErrorCode.VALIDATION, "修订号超出该分支", null);
        }
        if (rev == head) {
            throw new MopException(ErrorCode.VALIDATION, "分支已经在这个修订", null);
        }
        Map<String, ElementEntity> desired = index(reader.viewAt(branchId, rev));
        Map<String, ElementEntity> current = index(reader.view(branchId));
        List<ChangeInput> changes = new ArrayList<>();
        for (ElementEntity row : current.values()) {
            if (!desired.containsKey(row.getUid())) {
                changes.add(new ChangeInput("DELETE", row.getUid(), row.getElementType(), row.getApiName(), null));
            }
        }
        for (ElementEntity row : desired.values()) {
            ElementEntity live = current.get(row.getUid());
            JsonNode payload = readJson(row.getPayload());
            if (live == null) {
                changes.add(new ChangeInput("ADD", row.getUid(), row.getElementType(), row.getApiName(), payload));
            } else if (!Objects.equals(live.getContentHash(), row.getContentHash())) {
                changes.add(new ChangeInput("MODIFY", row.getUid(), row.getElementType(), row.getApiName(), payload));
            }
        }
        if (changes.isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, "没有需要回退的变更", null);
        }
        return commit(branchId, "回退到修订 " + rev, changes, actor, "REVERT", null, null);
    }

    @Transactional
    public CommitEntity commit(Long branchId, String message, List<ChangeInput> changes, AuthenticatedUser actor) {
        return commit(branchId, message, changes, actor, "MANUAL", null, null);
    }

    @Transactional
    public CommitEntity commit(
            Long branchId, String message, List<ChangeInput> changes, AuthenticatedUser actor, String source,
            Long mergeSourceBranchId, Long mergeSourceRev) {
        BranchEntity branch = requiredBranch(branchId);
        if (branch.getStatus() != null && !"ACTIVE".equals(branch.getStatus())) {
            throw new MopException(ErrorCode.VALIDATION, "分支已归档", null);
        }
        boolean governed = "MERGE".equals(source) || "REVERT".equals(source);
        if (branch.getProtectedFlag() != null && branch.getProtectedFlag() == 1 && !governed) {
            throw new MopException(ErrorCode.VALIDATION, "受保护分支不能直接提交，请通过合并写入", null);
        }
        if (changes == null) {
            changes = List.of();
        }
        projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getId, branch.getProjectId())
                .last("FOR UPDATE"));
        ProjectEntity project = projectMapper.selectById(branch.getProjectId());
        long rev = project.getHeadRev() + 1;
        long parentRev = branch.getHeadRev() == null ? 0L : branch.getHeadRev();
        List<Applied> applied = new ArrayList<>();
        reader.reading(rev);
        try {
            for (ChangeInput change : changes) {
                applied.add(apply(project, branch, rev, change));
            }
        } finally {
            reader.clearReading();
        }
        project.setHeadRev(rev);
        projectMapper.updateById(project);
        branch.setHeadRev(rev);
        branchMapper.updateById(branch);
        CommitEntity commit = new CommitEntity();
        commit.setTenantId(actor.tenantId());
        commit.setProjectId(project.getId());
        commit.setBranchId(branch.getId());
        commit.setRev(rev);
        commit.setParentRev(parentRev);
        commit.setMergeSourceBranchId(mergeSourceBranchId);
        commit.setMergeSourceRev(mergeSourceRev);
        commit.setMessage(message);
        commit.setSource(source == null ? "MANUAL" : source);
        commit.setChangeCount(changes.size());
        commit.setAuthorId(actor.userId());
        commit.setCreatedAt(LocalDateTime.now());
        commitMapper.insert(commit);
        for (int i = 0; i < changes.size(); i++) {
            Applied change = applied.get(i);
            ChangeEntity row = new ChangeEntity();
            row.setTenantId(actor.tenantId());
            row.setProjectId(project.getId());
            row.setCommitId(commit.getId());
            row.setUid(change.uid());
            row.setElementType(change.type());
            row.setOp(change.op());
            row.setBeforeJson(change.before());
            row.setAfterJson(change.after());
            row.setFieldPaths(change.fields());
            changeMapper.insert(row);
        }
        return commit;
    }

    @Transactional
    public ElementEntity saveElement(Long branchId, String op, String uid, String type, JsonNode payload, AuthenticatedUser actor) {
        if ("DELETE".equals(op)) {
            ElementEntity current = current(branchId, uid);
            if (current == null || current.getTombstone() == 1) {
                throw new MopException(ErrorCode.NOT_FOUND);
            }
            commit(branchId, "删除 " + current.getElementType(), List.of(new ChangeInput("DELETE", uid, current.getElementType(), current.getApiName(), null)), actor);
            return null;
        }
        return saveElement(branchId, op, uid, type, payload, actor, "表单" + op + " " + type);
    }

    @Override
    public Adopted add(long branchId, String type, JsonNode payload, AuthenticatedUser actor, String message) {
        ElementEntity saved = saveElement(branchId, "ADD", null, type, payload, actor, message);
        return new Adopted(saved.getUid());
    }

    private ElementEntity saveElement(Long branchId, String op, String uid, String type, JsonNode payload, AuthenticatedUser actor, String message) {
        if ("DELETE".equals(op)) {
            ElementEntity current = current(branchId, uid);
            if (current == null || current.getTombstone() == 1) {
                throw new MopException(ErrorCode.NOT_FOUND);
            }
            commit(branchId, message, List.of(new ChangeInput("DELETE", uid, current.getElementType(), current.getApiName(), null)), actor);
            return null;
        }
        String apiName = text(payload, "apiName");
        CommitEntity saved = commit(branchId, message, List.of(new ChangeInput(op, uid, type, apiName, payload)), actor);
        ChangeEntity change = changeMapper.selectOne(new LambdaQueryWrapper<ChangeEntity>()
                .eq(ChangeEntity::getCommitId, saved.getId())
                .last("LIMIT 1"));
        return current(branchId, change.getUid());
    }

    private Applied apply(ProjectEntity project, BranchEntity branch, long rev, ChangeInput change) {
        String op = change.op();
        if ("DELETE".equals(op)) {
            guardReferences(branch.getId(), change.uid());
            ElementEntity visible = reader.find(branch.getId(), change.uid());
            if (visible == null) {
                throw new MopException(ErrorCode.VALIDATION, "要删除的元素不存在", null);
            }
            ElementEntity local = localCurrent(branch.getId(), visible.getUid());
            if (local != null && (local.getTombstone() == null || local.getTombstone() == 0)) {
                close(local, rev);
                closeRefs(branch.getId(), local.getUid(), rev);
            }
            insertRow(project, branch, rev, visible.getUid(), visible.getElementType(), visible.getApiName(), visible.getOwnerUid(), visible.getPayload(), 1);
            return new Applied(visible.getUid(), visible.getElementType(), op, visible.getPayload(), null, fields(visible.getPayload(), null));
        }
        schemaValidator.validate(change.type(), change.payload());
        enforcePayload(project, branch, change);
        String payload = change.payload().toString();
        String uid = change.uid() == null || change.uid().isBlank() ? Ulids.newUlid() : change.uid();
        String owner = text(change.payload(), "ownerUid");
        String before = null;
        if ("MODIFY".equals(op)) {
            ElementEntity visible = reader.find(branch.getId(), uid);
            if (visible == null) {
                throw new MopException(ErrorCode.VALIDATION, "要修改的元素不存在", null);
            }
            if (ElementRules.apiNameChanged(visible.getApiName(), change.apiName())) {
                throw new MopException(ErrorCode.VALIDATION, "API 名创建后不能修改", null);
            }
            before = visible.getPayload();
            ElementEntity local = localCurrent(branch.getId(), uid);
            if (local != null) {
                close(local, rev);
                if (local.getTombstone() == null || local.getTombstone() == 0) {
                    closeRefs(branch.getId(), uid, rev);
                }
            }
        } else {
            ElementEntity local = localCurrent(branch.getId(), uid);
            if (local != null && (local.getTombstone() == null || local.getTombstone() == 0)) {
                throw new MopException(ErrorCode.VALIDATION, "元素已存在", null);
            }
            if (local != null) {
                close(local, rev);
            }
        }
        requireNoCycle(branch.getId(), uid, change.payload().get("parentUids"));
        uniqueApi(branch.getId(), change.type(), change.apiName(), uid, owner);
        insertRow(project, branch, rev, uid, change.type(), change.apiName(), owner, payload, 0);
        insertRefs(project, branch, rev, uid, change.payload());
        if ("OBJECT_TYPE".equals(change.type())) {
            requireNoPropertyConflict(branch.getId(), uid);
        }
        if ("PROPERTY".equals(change.type()) && owner != null) {
            requireNoPropertyConflict(branch.getId(), owner);
        }
        return new Applied(uid, change.type(), op, before, payload, fields(before, payload));
    }

    public Report effective(Long branchId, String uid) {
        requiredBranch(branchId);
        if (reader.find(branchId, uid) == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return report(branchId, uid);
    }

    private void requireNoPropertyConflict(Long branchId, String typeUid) {
        List<TypeNode> types = typeNodes(branchId);
        List<PropNode> properties = propertyNodes(branchId);
        for (String affected : EffectiveProperties.affected(typeUid, types)) {
            Report report = EffectiveProperties.of(affected, types, properties);
            if (!report.conflicts().isEmpty()) {
                NameConflict conflict = report.conflicts().get(0);
                throw new MopException(ErrorCode.PROP_CONFLICT, "同名属性冲突：" + conflict.apiName(),
                        java.util.Map.of("apiName", conflict.apiName(), "sources", conflict.sourceTypeApiNames()));
            }
        }
    }

    private Report report(Long branchId, String typeUid) {
        return EffectiveProperties.of(typeUid, typeNodes(branchId), propertyNodes(branchId));
    }

    private List<TypeNode> typeNodes(Long branchId) {
        List<TypeNode> types = new ArrayList<>();
        for (ElementEntity row : reader.view(branchId)) {
            if (!"OBJECT_TYPE".equals(row.getElementType())) {
                continue;
            }
            JsonNode payload = readJson(row.getPayload());
            List<String> parents = new ArrayList<>();
            JsonNode values = payload == null ? null : payload.get("parentUids");
            if (values != null && values.isArray()) {
                for (JsonNode value : values) {
                    if (value.isTextual()) {
                        parents.add(value.asText());
                    }
                }
            }
            types.add(new TypeNode(row.getUid(), row.getApiName(), text(payload, "displayName"), parents));
        }
        return types;
    }

    private List<PropNode> propertyNodes(Long branchId) {
        List<PropNode> properties = new ArrayList<>();
        for (ElementEntity row : reader.view(branchId)) {
            if (!"PROPERTY".equals(row.getElementType())) {
                continue;
            }
            JsonNode payload = readJson(row.getPayload());
            boolean override = payload != null && payload.path("override").asBoolean(false);
            properties.add(new PropNode(row.getUid(), row.getApiName(), text(payload, "displayName"), row.getOwnerUid(), override));
        }
        return properties;
    }

    private void guardReferences(Long branchId, String uid) {
        List<ElementRefEntity> refs = reader.refsTo(branchId, uid);
        if (!refs.isEmpty()) {
            throw new MopException(ErrorCode.REFERENCED, ErrorCode.REFERENCED.defaultMessage(),
                    java.util.Map.of("references", refs.stream().map(ref -> ref.getFromUid() + ":" + ref.getRefType()).toList()));
        }
    }

    private ElementEntity current(Long branchId, String uid) {
        return reader.find(branchId, uid);
    }

    private ElementEntity localCurrent(Long branchId, String uid) {
        return elementMapper.selectOne(new LambdaQueryWrapper<ElementEntity>()
                .eq(ElementEntity::getBranchId, branchId)
                .eq(ElementEntity::getUid, uid)
                .isNull(ElementEntity::getRevEnd)
                .last("LIMIT 1"));
    }

    private BranchEntity requiredBranch(Long branchId) {
        BranchEntity branch = branchMapper.selectById(branchId);
        if (branch == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return branch;
    }

    private void requireNoCycle(Long branchId, String uid, JsonNode parentUids) {
        if (uid == null || parentUids == null || !parentUids.isArray()) {
            return;
        }
        ArrayDeque<String> stack = new ArrayDeque<>();
        for (JsonNode parent : parentUids) {
            if (parent.isTextual()) {
                stack.push(parent.asText());
            }
        }
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            String next = stack.pop();
            if (uid.equals(next)) {
                throw new MopException(ErrorCode.INHERITANCE_CYCLE);
            }
            if (!seen.add(next)) {
                continue;
            }
            ElementEntity parent = reader.find(branchId, next);
            if (parent == null) {
                continue;
            }
            JsonNode payload = readJson(parent.getPayload());
            JsonNode grandparents = payload == null ? null : payload.get("parentUids");
            if (grandparents != null && grandparents.isArray()) {
                for (JsonNode item : grandparents) {
                    if (item.isTextual()) {
                        stack.push(item.asText());
                    }
                }
            }
        }
    }

    private String fields(String before, String after) {
        List<FieldChange> changes = JsonLeaves.diff(readJson(before), readJson(after));
        if (changes.isEmpty()) {
            return null;
        }
        return objectMapper.valueToTree(changes).toString();
    }

    private JsonNode readJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, ElementEntity> index(List<ElementEntity> rows) {
        Map<String, ElementEntity> out = new LinkedHashMap<>();
        for (ElementEntity row : rows) {
            out.put(row.getUid(), row);
        }
        return out;
    }

    private void close(ElementEntity current, long rev) {
        current.setRevEnd(rev);
        elementMapper.updateById(current);
    }

    private void closeRefs(Long branchId, String fromUid, long rev) {
        List<ElementRefEntity> refs = elementRefMapper.selectList(new LambdaQueryWrapper<ElementRefEntity>()
                .eq(ElementRefEntity::getBranchId, branchId)
                .eq(ElementRefEntity::getFromUid, fromUid)
                .isNull(ElementRefEntity::getRevEnd));
        for (ElementRefEntity ref : refs) {
            ref.setRevEnd(rev);
            elementRefMapper.updateById(ref);
        }
    }

    private void insertRow(ProjectEntity project, BranchEntity branch, long rev, String uid, String type, String apiName, String owner, String payload, int tombstone) {
        ElementEntity row = new ElementEntity();
        row.setTenantId(TenantContext.get());
        row.setProjectId(project.getId());
        row.setBranchId(branch.getId());
        row.setUid(uid);
        row.setElementType(type);
        row.setApiName(apiName);
        row.setOwnerUid(owner);
        row.setRevStart(rev);
        row.setTombstone(tombstone);
        row.setPayload(payload);
        row.setContentHash(sha256(payload));
        elementMapper.insert(row);
    }

    private void enforcePayload(ProjectEntity project, BranchEntity branch, ChangeInput change) {
        JsonNode payload = change.payload();
        String type = change.type();
        boolean cqRequired = ElementRules.requiresCq(type) && (project.getRequireCqLink() == null || project.getRequireCqLink() != 0);
        if (cqRequired && !ElementRules.hasCq(payload)) {
            throw new MopException(ErrorCode.CQ_REQUIRED);
        }
        String cardinality = ElementRules.cardinalityProblem(payload);
        if (cardinality != null) {
            throw new MopException(ErrorCode.VALIDATION, cardinality, null);
        }
        String inverse = ElementRules.inverseProblem(payload);
        if (inverse != null) {
            throw new MopException(ErrorCode.VALIDATION, inverse, null);
        }
        if ("PROPERTY".equals(type)) {
            requireLive(branch.getId(), text(payload, "ownerUid"), "OBJECT_TYPE");
            requireValueType(branch.getId(), payload.get("valueType"));
        }
        if ("ENUM_VALUE".equals(type)) {
            requireLive(branch.getId(), text(payload, "ownerUid"), "ENUM_TYPE");
        }
        if ("LINK_TYPE".equals(type)) {
            requireLive(branch.getId(), text(payload, "sourceUid"), "OBJECT_TYPE");
            requireLive(branch.getId(), text(payload, "targetUid"), "OBJECT_TYPE");
        }
        requireEach(branch.getId(), payload.get("cqUids"), "CQ");
        requireEach(branch.getId(), payload.get("parentUids"), "OBJECT_TYPE");
        if ("DATA_RULE".equals(type)) {
            requireLive(branch.getId(), text(payload, "targetObjectUid"), "OBJECT_TYPE");
            String propertyUid = text(payload, "targetPropertyUid");
            if (propertyUid != null) {
                requireLive(branch.getId(), propertyUid, "PROPERTY");
            }
        }
        if ("AXIOM".equals(type)) {
            String subject = text(payload, "subjectUid");
            if (subject != null) {
                requireLive(branch.getId(), subject, "OBJECT_TYPE");
            }
            String objectUid = text(payload, "objectUid");
            if (objectUid != null) {
                requireLive(branch.getId(), objectUid, "OBJECT_TYPE");
            }
            requireEach(branch.getId(), payload.get("classUids"), "OBJECT_TYPE");
        }
    }

    private void requireValueType(Long branchId, JsonNode valueType) {
        if (valueType == null || valueType.isNull()) {
            return;
        }
        String kind = text(valueType, "kind");
        if ("DATA_TYPE".equals(kind)) {
            requireLive(branchId, text(valueType, "dataTypeUid"), "DATA_TYPE");
        }
        if ("ENUM".equals(kind)) {
            requireLive(branchId, text(valueType, "enumUid"), "ENUM_TYPE");
        }
    }

    private void requireEach(Long branchId, JsonNode values, String expectedType) {
        if (values == null || !values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            if (value.isTextual()) {
                requireLive(branchId, value.asText(), expectedType);
            }
        }
    }

    private void requireLive(Long branchId, String uid, String expectedType) {
        if (uid == null || uid.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "引用的元素不存在", null);
        }
        ElementEntity row = current(branchId, uid);
        if (row == null || row.getTombstone() == 1 || !expectedType.equals(row.getElementType())) {
            throw new MopException(ErrorCode.VALIDATION, "引用的元素不存在", null);
        }
    }

    private void uniqueApi(Long branchId, String type, String apiName, String uid, String ownerUid) {
        if (apiName == null || apiName.isBlank()) {
            return;
        }
        for (ElementEntity row : reader.view(branchId)) {
            if (!type.equals(row.getElementType()) || !apiName.equals(row.getApiName()) || row.getUid().equals(uid)) {
                continue;
            }
            if (("PROPERTY".equals(type) || "ENUM_VALUE".equals(type)) && !Objects.equals(ownerUid, row.getOwnerUid())) {
                continue;
            }
            throw new MopException(ErrorCode.VALIDATION, "API 名已存在", null);
        }
    }

    private void insertRefs(ProjectEntity project, BranchEntity branch, long rev, String fromUid, JsonNode payload) {
        Set<String> seen = new HashSet<>();
        link(project, branch, rev, fromUid, text(payload, "ownerUid"), "PROP_OWNER", seen);
        link(project, branch, rev, fromUid, text(payload, "sourceUid"), "LINK_SOURCE", seen);
        link(project, branch, rev, fromUid, text(payload, "targetUid"), "LINK_TARGET", seen);
        link(project, branch, rev, fromUid, text(payload, "subjectUid"), "AXIOM_CLASS", seen);
        link(project, branch, rev, fromUid, text(payload, "objectUid"), "AXIOM_CLASS", seen);
        link(project, branch, rev, fromUid, text(payload, "targetObjectUid"), "RULE_TARGET", seen);
        link(project, branch, rev, fromUid, text(payload, "targetPropertyUid"), "RULE_TARGET", seen);
        linkAll(project, branch, rev, fromUid, payload.get("cqUids"), "CQ_LINK", seen);
        linkAll(project, branch, rev, fromUid, payload.get("parentUids"), "EXTENDS", seen);
        linkAll(project, branch, rev, fromUid, payload.get("termUids"), "TERM_LINK", seen);
        linkAll(project, branch, rev, fromUid, payload.get("classUids"), "AXIOM_CLASS", seen);
        JsonNode valueType = payload.get("valueType");
        if (valueType != null && valueType.isObject()) {
            link(project, branch, rev, fromUid, text(valueType, "dataTypeUid"), "PROP_TYPE", seen);
            link(project, branch, rev, fromUid, text(valueType, "enumUid"), "ENUM_OF", seen);
        }
    }

    private void linkAll(ProjectEntity project, BranchEntity branch, long rev, String fromUid, JsonNode values, String type, Set<String> seen) {
        if (values == null || !values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            if (value.isTextual()) {
                link(project, branch, rev, fromUid, value.asText(), type, seen);
            }
        }
    }

    private void link(ProjectEntity project, BranchEntity branch, long rev, String fromUid, String toUid, String type, Set<String> seen) {
        if (toUid == null || toUid.isBlank() || !seen.add(type + ":" + toUid)) {
            return;
        }
        addRef(project, branch, rev, fromUid, toUid, type);
    }

    private void addRef(ProjectEntity project, BranchEntity branch, long rev, String fromUid, String toUid, String type) {
        if (toUid == null || toUid.isBlank()) {
            return;
        }
        ElementRefEntity ref = new ElementRefEntity();
        ref.setTenantId(TenantContext.get());
        ref.setProjectId(project.getId());
        ref.setBranchId(branch.getId());
        ref.setFromUid(fromUid);
        ref.setToUid(toUid);
        ref.setRefType(type);
        ref.setRevStart(rev);
        elementRefMapper.insert(ref);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void addMember(ProjectEntity project, Long userId, String role) {
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setTenantId(project.getTenantId());
        member.setProjectId(project.getId());
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
    }

    private static void requireNamespace(String namespace) {
        if (!isNamespace(namespace)) {
            throw new MopException(ErrorCode.VALIDATION, "命名空间必须是以 # 或 / 结尾的绝对 URI", null);
        }
    }

    private static void requireText(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new MopException(ErrorCode.VALIDATION, label + "不能为空，且最长 " + max, null);
        }
    }

    private static void requireOptional(String value, int max, String label) {
        if (value != null && value.trim().length() > max) {
            throw new MopException(ErrorCode.VALIDATION, label + "最长 " + max, null);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isNamespace(String namespace) {
        if (namespace == null || !(namespace.endsWith("#") || namespace.endsWith("/"))) {
            return false;
        }
        try {
            return URI.create(namespace).isAbsolute();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String sha256(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record ChangeInput(String op, String uid, String type, String apiName, JsonNode payload) {}

    public record MemberInput(Long userId, String role) {}

    private record Applied(String uid, String type, String op, String before, String after, String fields) {}
}
