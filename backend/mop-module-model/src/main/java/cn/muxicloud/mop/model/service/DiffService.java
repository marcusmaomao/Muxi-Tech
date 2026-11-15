package cn.muxicloud.mop.model.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.mapper.BranchMapper;
import cn.muxicloud.mop.model.version.FieldChange;
import cn.muxicloud.mop.model.version.JsonLeaves;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DiffService {
    private final BranchMapper branchMapper;
    private final BranchReader reader;
    private final ObjectMapper json;

    public DiffService(BranchMapper branchMapper, BranchReader reader, ObjectMapper json) {
        this.branchMapper = branchMapper;
        this.reader = reader;
        this.json = json;
    }

    public DiffResult diff(long projectId, String from, String to) {
        Map<String, ElementEntity> left = index(resolve(projectId, from));
        Map<String, ElementEntity> right = index(resolve(projectId, to));
        List<ElementDiff> added = new ArrayList<>();
        List<ElementDiff> modified = new ArrayList<>();
        List<ElementDiff> deleted = new ArrayList<>();
        for (ElementEntity row : right.values()) {
            ElementEntity previous = left.get(row.getUid());
            if (previous == null) {
                added.add(elementDiff(row, null, row.getPayload()));
            } else if (!previous.getContentHash().equals(row.getContentHash())) {
                modified.add(elementDiff(row, previous.getPayload(), row.getPayload()));
            }
        }
        for (ElementEntity row : left.values()) {
            if (!right.containsKey(row.getUid())) {
                deleted.add(elementDiff(row, row.getPayload(), null));
            }
        }
        return new DiffResult(added, modified, deleted);
    }

    public List<ElementEntity> resolve(long projectId, String ref) {
        if (ref == null || !ref.startsWith("branch:")) {
            throw new MopException(ErrorCode.VALIDATION, "差异端点要写成 branch:名称 或 branch:名称@修订号", null);
        }
        String rest = ref.substring("branch:".length());
        String name = rest;
        Long rev = null;
        int at = rest.lastIndexOf('@');
        if (at > 0 && rest.substring(at + 1).matches("\\d+")) {
            name = rest.substring(0, at);
            rev = Long.parseLong(rest.substring(at + 1));
        }
        BranchEntity branch = branchMapper.selectOne(new LambdaQueryWrapper<BranchEntity>()
                .eq(BranchEntity::getProjectId, projectId)
                .eq(BranchEntity::getName, name)
                .last("LIMIT 1"));
        if (branch == null) {
            throw new MopException(ErrorCode.NOT_FOUND, "分支不存在", null);
        }
        if (rev == null) {
            return reader.view(branch.getId());
        }
        return reader.viewAt(branch.getId(), rev);
    }

    private ElementDiff elementDiff(ElementEntity row, String before, String after) {
        JsonNode left = parse(before);
        JsonNode right = parse(after);
        return new ElementDiff(row.getUid(), row.getElementType(), row.getApiName(), before, after, JsonLeaves.diff(left, right));
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return json.readTree(payload);
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

    public record ElementDiff(String uid, String elementType, String apiName, String before, String after, List<FieldChange> fields) {}

    public record DiffResult(List<ElementDiff> added, List<ElementDiff> modified, List<ElementDiff> deleted) {}
}
