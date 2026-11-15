package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.common.id.Ulids;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.ChangeEntity;
import cn.muxicloud.mop.model.entity.CommitEntity;
import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.entity.ElementRefEntity;
import cn.muxicloud.mop.model.form.ElementRules;
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
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelService {
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

    public ModelService(
            ProjectMapper projectMapper,
            ProjectMemberMapper memberMapper,
            BranchMapper branchMapper,
            ElementMapper elementMapper,
            ElementRefMapper elementRefMapper,
            CommitMapper commitMapper,
            ChangeMapper changeMapper,
            PayloadSchemaValidator schemaValidator,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.branchMapper = branchMapper;
        this.elementMapper = elementMapper;
        this.elementRefMapper = elementRefMapper;
        this.commitMapper = commitMapper;
        this.changeMapper = changeMapper;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
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
        branch.setProtectedFlag(1);
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

    public List<ElementEntity> elements(Long branchId, String type) {
        LambdaQueryWrapper<ElementEntity> query = new LambdaQueryWrapper<ElementEntity>()
                .eq(ElementEntity::getBranchId, branchId)
                .isNull(ElementEntity::getRevEnd)
                .eq(ElementEntity::getTombstone, 0);
        if (type != null && !type.isBlank()) {
            query.eq(ElementEntity::getElementType, type);
        }
        return elementMapper.selectList(query);
    }

    @Transactional
    public CommitEntity commit(Long branchId, String message, List<ChangeInput> changes, AuthenticatedUser actor) {
        BranchEntity branch = branchMapper.selectById(branchId);
        if (branch == null || !"main".equals(branch.getName())) {
            throw new MopException(ErrorCode.VALIDATION, "当前只支持向 main 分支提交", null);
        }
        projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getId, branch.getProjectId())
                .last("FOR UPDATE"));
        ProjectEntity project = projectMapper.selectById(branch.getProjectId());
        long rev = project.getHeadRev() + 1;
        List<String> applied = new ArrayList<>();
        for (ChangeInput change : changes) {
            applied.add(apply(project, branch, rev, change));
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
        commit.setMessage(message);
        commit.setChangeCount(changes.size());
        commit.setAuthorId(actor.userId());
        commit.setCreatedAt(LocalDateTime.now());
        commitMapper.insert(commit);
        for (int i = 0; i < changes.size(); i++) {
            ChangeInput change = changes.get(i);
            ChangeEntity row = new ChangeEntity();
            row.setTenantId(actor.tenantId());
            row.setProjectId(project.getId());
            row.setCommitId(commit.getId());
            row.setUid(applied.get(i));
            row.setElementType(change.type());
            row.setOp(change.op());
            row.setAfterJson(change.payload() == null ? null : change.payload().toString());
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
        String apiName = text(payload, "apiName");
        CommitEntity saved = commit(branchId, "表单" + op + " " + type, List.of(new ChangeInput(op, uid, type, apiName, payload)), actor);
        ChangeEntity change = changeMapper.selectOne(new LambdaQueryWrapper<ChangeEntity>()
                .eq(ChangeEntity::getCommitId, saved.getId())
                .last("LIMIT 1"));
        return current(branchId, change.getUid());
    }

    private String apply(ProjectEntity project, BranchEntity branch, long rev, ChangeInput change) {
        String op = change.op();
        if ("DELETE".equals(op)) {
            guardReferences(branch.getId(), change.uid());
            ElementEntity current = current(branch.getId(), change.uid());
            if (current == null || current.getTombstone() == 1) {
                throw new MopException(ErrorCode.VALIDATION, "要删除的元素不存在", null);
            }
            close(current, rev);
            closeRefs(branch.getId(), current.getUid(), rev);
            insertRow(project, branch, rev, current.getUid(), current.getElementType(), current.getApiName(), current.getOwnerUid(), current.getPayload(), 1);
            return current.getUid();
        }
        schemaValidator.validate(change.type(), change.payload());
        enforcePayload(project, branch, change);
        String payload = change.payload().toString();
        String uid = change.uid() == null || change.uid().isBlank() ? Ulids.newUlid() : change.uid();
        String owner = text(change.payload(), "ownerUid");
        if ("MODIFY".equals(op)) {
            ElementEntity current = current(branch.getId(), uid);
            if (current == null || current.getTombstone() == 1) {
                throw new MopException(ErrorCode.VALIDATION, "要修改的元素不存在", null);
            }
            if (ElementRules.apiNameChanged(current.getApiName(), change.apiName())) {
                throw new MopException(ErrorCode.VALIDATION, "API 名创建后不能修改", null);
            }
            close(current, rev);
            closeRefs(branch.getId(), uid, rev);
        }
        uniqueApi(branch.getId(), change.type(), change.apiName(), uid, owner);
        insertRow(project, branch, rev, uid, change.type(), change.apiName(), owner, payload, 0);
        insertRefs(project, branch, rev, uid, change.payload());
        return uid;
    }

    private void guardReferences(Long branchId, String uid) {
        List<ElementRefEntity> refs = elementRefMapper.selectList(new LambdaQueryWrapper<ElementRefEntity>()
                .eq(ElementRefEntity::getBranchId, branchId)
                .eq(ElementRefEntity::getToUid, uid)
                .isNull(ElementRefEntity::getRevEnd)
                .ne(ElementRefEntity::getFromUid, uid));
        if (!refs.isEmpty()) {
            throw new MopException(ErrorCode.REFERENCED, ErrorCode.REFERENCED.defaultMessage(),
                    java.util.Map.of("references", refs.stream().map(ref -> ref.getFromUid() + ":" + ref.getRefType()).toList()));
        }
    }

    private ElementEntity current(Long branchId, String uid) {
        return elementMapper.selectOne(new LambdaQueryWrapper<ElementEntity>()
                .eq(ElementEntity::getBranchId, branchId)
                .eq(ElementEntity::getUid, uid)
                .isNull(ElementEntity::getRevEnd)
                .last("LIMIT 1"));
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
        LambdaQueryWrapper<ElementEntity> query = new LambdaQueryWrapper<ElementEntity>()
                .eq(ElementEntity::getBranchId, branchId)
                .eq(ElementEntity::getElementType, type)
                .eq(ElementEntity::getApiName, apiName)
                .isNull(ElementEntity::getRevEnd)
                .eq(ElementEntity::getTombstone, 0);
        if ("PROPERTY".equals(type) || "ENUM_VALUE".equals(type)) {
            query.eq(ElementEntity::getOwnerUid, ownerUid);
        }
        for (ElementEntity row : elementMapper.selectList(query)) {
            if (!row.getUid().equals(uid)) {
                throw new MopException(ErrorCode.VALIDATION, "API 名已存在", null);
            }
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
}
