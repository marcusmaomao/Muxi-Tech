package cn.muxicloud.mop.runtime.service;

import cn.muxicloud.mop.basic.api.SourceReader;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.runtime.entity.RtMappingEntity;
import cn.muxicloud.mop.runtime.entity.RtSyncCursorEntity;
import cn.muxicloud.mop.runtime.entity.RtSyncLogEntity;
import cn.muxicloud.mop.runtime.entity.RtSyncRunEntity;
import cn.muxicloud.mop.runtime.graph.GraphWriter;
import cn.muxicloud.mop.runtime.mapper.RtMappingMapper;
import cn.muxicloud.mop.runtime.mapper.RtSyncCursorMapper;
import cn.muxicloud.mop.runtime.mapper.RtSyncLogMapper;
import cn.muxicloud.mop.runtime.mapper.RtSyncRunMapper;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.FieldBinding;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.Incremental;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.LinkBinding;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.MappingDefinition;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.StoredField;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.StoredLink;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.StoredMapping;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.tenant.TenantCypherGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MappingService {
    private final RtMappingMapper mappingMapper;
    private final RtSyncCursorMapper cursorMapper;
    private final RtSyncRunMapper runMapper;
    private final RtSyncLogMapper logMapper;
    private final SourceReader source;
    private final BranchCatalog catalog;
    private final GraphWriter graph;
    private final ObjectMapper json = new ObjectMapper();

    public MappingService(
            RtMappingMapper mappingMapper,
            RtSyncCursorMapper cursorMapper,
            RtSyncRunMapper runMapper,
            RtSyncLogMapper logMapper,
            SourceReader source,
            BranchCatalog catalog,
            GraphWriter graph) {
        this.mappingMapper = mappingMapper;
        this.cursorMapper = cursorMapper;
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.source = source;
        this.catalog = catalog;
        this.graph = graph;
    }

    public MappingView save(long branchId, long datasourceId, MappingDefinition input, AuthenticatedUser actor) {
        StoredMapping stored = resolve(branchId, input);
        source.max(datasourceId, stored.table(), stored.pkColumn());
        if ("UPDATED_AT".equals(stored.incremental().kind())) {
            source.max(datasourceId, stored.table(), stored.incremental().column());
        }
        RtMappingEntity entity = new RtMappingEntity();
        entity.setTenantId(actor.tenantId());
        entity.setBranchId(branchId);
        entity.setDatasourceId(datasourceId);
        entity.setObjectUid(stored.objectUid());
        entity.setVersionNo(nextVersion(branchId, datasourceId, stored.objectUid()));
        entity.setCreatedBy(actor.userId());
        entity.setCreatedAt(LocalDateTime.now());
        try {
            entity.setDefinition(json.writeValueAsString(stored));
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "映射无法保存", null);
        }
        mappingMapper.insert(entity);
        return view(entity, stored);
    }

    public List<MappingView> list(long branchId, long datasourceId) {
        return mappingMapper.selectList(new LambdaQueryWrapper<RtMappingEntity>()
                        .eq(RtMappingEntity::getBranchId, branchId)
                        .eq(RtMappingEntity::getDatasourceId, datasourceId)
                        .orderByDesc(RtMappingEntity::getVersionNo))
                .stream().map(row -> view(row, read(row))).toList();
    }

    public Preview preview(long mappingId, int limit) {
        RtMappingEntity mapping = required(mappingId);
        StoredMapping stored = read(mapping);
        int size = Math.min(Math.max(limit, 1), 50);
        List<Map<String, String>> rows = source.read(new SourceReader.Read(
                mapping.getDatasourceId(), stored.table(), columnsOf(stored), stored.pkColumn(), null, null, size));
        List<PreviewObject> objects = new ArrayList<>();
        List<PreviewLink> links = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String pk = row.get(stored.pkColumn());
            if (pk == null || pk.isBlank()) {
                continue;
            }
            objects.add(new PreviewObject(stored.objectApiName(), pk, props(stored, row)));
            links.addAll(linkViews(mapping, stored, pk, row));
        }
        return new Preview(objects, links);
    }

    public RunView sync(long mappingId, String mode, AuthenticatedUser actor) {
        if (!"FULL".equals(mode) && !"INCREMENTAL".equals(mode)) {
            throw new MopException(ErrorCode.VALIDATION, "同步方式只能是全量或增量", null);
        }
        RtMappingEntity mapping = required(mappingId);
        StoredMapping stored = read(mapping);
        RtSyncRunEntity run = new RtSyncRunEntity();
        run.setTenantId(actor.tenantId());
        run.setBranchId(mapping.getBranchId());
        run.setMappingId(mapping.getId());
        run.setVersionNo(mapping.getVersionNo());
        run.setMode(mode);
        run.setStatus("RUNNING");
        run.setInsertedCount(0);
        run.setUpdatedCount(0);
        run.setLinkCount(0);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        log(run, "FULL".equals(mode) ? "开始全量同步" : "开始增量同步");
        try {
            String watermarkColumn = watermarkColumn(stored);
            String filterColumn = "FULL".equals(mode) ? stored.pkColumn() : watermarkColumn;
            String pageCursor = "INCREMENTAL".equals(mode) ? currentCursor(mapping) : null;
            int inserted = 0;
            int updated = 0;
            int linked = 0;
            while (true) {
                List<Map<String, String>> rows = source.read(new SourceReader.Read(
                        mapping.getDatasourceId(), stored.table(), columnsOf(stored), filterColumn, filterColumn, pageCursor, 500));
                if (rows.isEmpty()) {
                    break;
                }
                List<GraphWriter.ObjectRow> objects = new ArrayList<>();
                List<GraphWriter.LinkRow> links = new ArrayList<>();
                for (Map<String, String> row : rows) {
                    String pk = row.get(stored.pkColumn());
                    if (pk == null || pk.isBlank()) {
                        continue;
                    }
                    objects.add(new GraphWriter.ObjectRow(
                            stored.objectApiName(), pk, rid(actor, stored, pk), props(stored, row)));
                    for (PreviewLink link : linkViews(mapping, stored, pk, row)) {
                        links.add(new GraphWriter.LinkRow(link.type(), stored.objectApiName(), pk, link.targetType(), link.toPk()));
                    }
                }
                GraphWriter.WriteCount count = graph.mergeObjects(objects);
                inserted += count.inserted();
                updated += count.updated();
                linked += graph.mergeLinks(links);
                pageCursor = rows.get(rows.size() - 1).get(filterColumn);
                log(run, "本批 " + rows.size() + " 行");
                if (rows.size() < 500) {
                    break;
                }
            }
            String next = source.max(mapping.getDatasourceId(), stored.table(), watermarkColumn);
            if (next != null && !next.isBlank()) {
                saveCursor(mapping, next);
            }
            run.setStatus("SUCCEEDED");
            run.setInsertedCount(inserted);
            run.setUpdatedCount(updated);
            run.setLinkCount(linked);
            run.setMessage("新增 " + inserted + "，更新 " + updated + "，关系 " + linked);
            log(run, run.getMessage());
        } catch (RuntimeException ex) {
            run.setStatus("FAILED");
            run.setMessage(ErrorCode.SYNC_FAILED.defaultMessage() + "：" + shorten(ex.getMessage()));
            log(run, run.getMessage());
        }
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        return runView(run, false);
    }

    public List<RunView> runs(long branchId) {
        return runMapper.selectList(new LambdaQueryWrapper<RtSyncRunEntity>()
                        .eq(RtSyncRunEntity::getBranchId, branchId)
                        .orderByDesc(RtSyncRunEntity::getId))
                .stream().map(row -> runView(row, false)).toList();
    }

    public RunView run(long runId) {
        RtSyncRunEntity row = runMapper.selectById(runId);
        if (row == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return runView(row, true);
    }

    public List<GraphWriter.GraphObject> objects(String type) {
        return graph.objects(type);
    }

    private List<PreviewLink> linkViews(RtMappingEntity mapping, StoredMapping stored, String pk, Map<String, String> row) {
        List<PreviewLink> links = new ArrayList<>();
        for (StoredLink link : stored.links()) {
            if ("FOREIGN_KEY".equals(link.kind())) {
                String targetPk = row.get(link.column());
                if (targetPk != null && !targetPk.isBlank()) {
                    links.add(new PreviewLink(link.linkApiName(), link.targetApiName(), pk, targetPk));
                }
            } else if ("JUNCTION".equals(link.kind())) {
                for (String targetPk : source.junctionTargets(mapping.getDatasourceId(), link.table(), link.sourceColumn(), link.targetColumn(), pk)) {
                    links.add(new PreviewLink(link.linkApiName(), link.targetApiName(), pk, targetPk));
                }
            }
        }
        return links;
    }

    private Map<String, String> props(StoredMapping stored, Map<String, String> row) {
        Map<String, String> props = new LinkedHashMap<>();
        for (StoredField field : stored.fields()) {
            String value = row.get(field.column());
            if (value != null) {
                props.put(field.propertyApiName(), value);
            }
        }
        return props;
    }

    private List<String> columnsOf(StoredMapping stored) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.add(stored.pkColumn());
        columns.add(watermarkColumn(stored));
        for (StoredField field : stored.fields()) {
            columns.add(field.column());
        }
        for (StoredLink link : stored.links()) {
            if ("FOREIGN_KEY".equals(link.kind())) {
                columns.add(link.column());
            }
        }
        return List.copyOf(columns);
    }

    private static String watermarkColumn(StoredMapping stored) {
        if ("PK".equals(stored.incremental().kind())) {
            return stored.pkColumn();
        }
        return stored.incremental().column();
    }

    private StoredMapping resolve(long branchId, MappingDefinition input) {
        if (input == null || input.objectUid() == null || input.table() == null || input.pkColumn() == null || input.incremental() == null) {
            throw new MopException(ErrorCode.VALIDATION, "请填写对象、表、主键和水位", null);
        }
        source.check(input.table(), List.of(input.pkColumn()));
        String kind = input.incremental().kind();
        if (!"UPDATED_AT".equals(kind) && !"PK".equals(kind)) {
            throw new MopException(ErrorCode.VALIDATION, "水位只能按更新时间或主键", null);
        }
        if ("UPDATED_AT".equals(kind)) {
            if (input.incremental().column() == null || input.incremental().column().isBlank()) {
                throw new MopException(ErrorCode.VALIDATION, "请填写更新时间字段", null);
            }
            source.check(input.table(), List.of(input.incremental().column()));
        }
        if (input.fields() == null || input.fields().isEmpty()) {
            throw new MopException(ErrorCode.VALIDATION, "至少映射一个字段", null);
        }
        CatalogElement object = element(branchId, input.objectUid(), "OBJECT_TYPE");
        api(object.apiName());
        List<StoredField> fields = new ArrayList<>();
        for (FieldBinding field : input.fields()) {
            if (field == null || field.column() == null || field.propertyUid() == null) {
                throw new MopException(ErrorCode.VALIDATION, "字段映射不完整", null);
            }
            source.check(input.table(), List.of(field.column()));
            CatalogElement property = element(branchId, field.propertyUid(), "PROPERTY");
            api(property.apiName());
            if (!object.uid().equals(property.ownerUid())) {
                throw new MopException(ErrorCode.VALIDATION, "属性不属于该对象", null);
            }
            fields.add(new StoredField(property.uid(), property.apiName(), field.column()));
        }
        List<StoredLink> links = new ArrayList<>();
        if (input.links() != null) {
            for (LinkBinding link : input.links()) {
                CatalogElement linkType = element(branchId, link.linkUid(), "LINK_TYPE");
                CatalogElement target = element(branchId, link.targetObjectUid(), "OBJECT_TYPE");
                JsonNode payload = payload(linkType);
                if (!object.uid().equals(payload.path("sourceUid").asText()) || !target.uid().equals(payload.path("targetUid").asText())) {
                    throw new MopException(ErrorCode.VALIDATION, "关系的起点和终点与映射不一致", null);
                }
                api(linkType.apiName());
                api(target.apiName());
                if (link == null || link.kind() == null || link.linkUid() == null || link.targetObjectUid() == null) {
                    throw new MopException(ErrorCode.VALIDATION, "关系映射不完整", null);
                }
                if ("FOREIGN_KEY".equals(link.kind())) {
                    if (link.column() == null) {
                        throw new MopException(ErrorCode.VALIDATION, "请填写外键字段", null);
                    }
                    source.check(input.table(), List.of(link.column()));
                    links.add(new StoredLink("FOREIGN_KEY", linkType.uid(), linkType.apiName(), link.column(), target.uid(), target.apiName(), null, null, null));
                } else if ("JUNCTION".equals(link.kind())) {
                    source.check(link.table(), List.of(link.sourceColumn(), link.targetColumn()));
                    links.add(new StoredLink("JUNCTION", linkType.uid(), linkType.apiName(), null, target.uid(), target.apiName(), link.table(), link.sourceColumn(), link.targetColumn()));
                } else {
                    throw new MopException(ErrorCode.VALIDATION, "关系映射只能是外键或中间表", null);
                }
            }
        }
        return new StoredMapping(object.uid(), object.apiName(), input.table(), input.pkColumn(), new Incremental(kind, input.incremental().column()), fields, links);
    }

    private CatalogElement element(long branchId, String uid, String type) {
        if (uid == null || uid.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "请选择已建模的元素", null);
        }
        for (CatalogElement element : catalog.snapshot(branchId).elements()) {
            if (uid.equals(element.uid()) && type.equals(element.type())) {
                return element;
            }
        }
        throw new MopException(ErrorCode.VALIDATION, "映射必须指向当前分支上的" + type, null);
    }

    private JsonNode payload(CatalogElement element) {
        try {
            return json.readTree(element.payload() == null ? "{}" : element.payload());
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "元素内容无法读取", null);
        }
    }

    private int nextVersion(long branchId, long datasourceId, String objectUid) {
        RtMappingEntity latest = mappingMapper.selectOne(new LambdaQueryWrapper<RtMappingEntity>()
                .eq(RtMappingEntity::getBranchId, branchId)
                .eq(RtMappingEntity::getDatasourceId, datasourceId)
                .eq(RtMappingEntity::getObjectUid, objectUid)
                .orderByDesc(RtMappingEntity::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private String currentCursor(RtMappingEntity mapping) {
        RtSyncCursorEntity cursor = cursorMapper.selectOne(scope(mapping).last("LIMIT 1"));
        return cursor == null ? null : cursor.getCursorValue();
    }

    private void saveCursor(RtMappingEntity mapping, String value) {
        RtSyncCursorEntity cursor = cursorMapper.selectOne(scope(mapping).last("LIMIT 1"));
        if (cursor == null) {
            cursor = new RtSyncCursorEntity();
            cursor.setTenantId(mapping.getTenantId());
            cursor.setBranchId(mapping.getBranchId());
            cursor.setDatasourceId(mapping.getDatasourceId());
            cursor.setObjectUid(mapping.getObjectUid());
            cursor.setCursorValue(value);
            cursor.setUpdatedAt(LocalDateTime.now());
            cursorMapper.insert(cursor);
            return;
        }
        cursor.setCursorValue(value);
        cursor.setUpdatedAt(LocalDateTime.now());
        cursorMapper.updateById(cursor);
    }

    private LambdaQueryWrapper<RtSyncCursorEntity> scope(RtMappingEntity mapping) {
        return new LambdaQueryWrapper<RtSyncCursorEntity>()
                .eq(RtSyncCursorEntity::getBranchId, mapping.getBranchId())
                .eq(RtSyncCursorEntity::getDatasourceId, mapping.getDatasourceId())
                .eq(RtSyncCursorEntity::getObjectUid, mapping.getObjectUid());
    }

    private void log(RtSyncRunEntity run, String message) {
        RtSyncLogEntity row = new RtSyncLogEntity();
        row.setTenantId(run.getTenantId());
        row.setRunId(run.getId());
        row.setMessage(message.length() <= 500 ? message : message.substring(0, 500));
        row.setCreatedAt(LocalDateTime.now());
        logMapper.insert(row);
    }

    private RtMappingEntity required(long mappingId) {
        RtMappingEntity mapping = mappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        return mapping;
    }

    private StoredMapping read(RtMappingEntity entity) {
        try {
            return json.readValue(entity.getDefinition(), StoredMapping.class);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "映射版本无法读取", null);
        }
    }

    private MappingView view(RtMappingEntity entity, StoredMapping stored) {
        return new MappingView(entity.getId(), entity.getBranchId(), entity.getDatasourceId(), entity.getVersionNo(), entity.getCreatedAt(), stored);
    }

    private RunView runView(RtSyncRunEntity run, boolean withLogs) {
        List<String> logs = List.of();
        if (withLogs) {
            logs = logMapper.selectList(new LambdaQueryWrapper<RtSyncLogEntity>().eq(RtSyncLogEntity::getRunId, run.getId()).orderByAsc(RtSyncLogEntity::getId))
                    .stream().map(RtSyncLogEntity::getMessage).toList();
        }
        return new RunView(run.getId(), run.getMappingId(), run.getVersionNo(), run.getMode(), run.getStatus(), run.getInsertedCount(), run.getUpdatedCount(), run.getLinkCount(), run.getMessage(), run.getStartedAt(), run.getFinishedAt(), logs);
    }

    private static void api(String name) {
        try {
            TenantCypherGuard.requireApiName(name);
        } catch (IllegalArgumentException ex) {
            throw new MopException(ErrorCode.VALIDATION, "类型名不合法", null);
        }
    }

    private static String shorten(String message) {
        if (message == null || message.isBlank()) {
            return "同步失败";
        }
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 180 ? oneLine : oneLine.substring(0, 180);
    }

    private static String rid(AuthenticatedUser actor, StoredMapping stored, String pk) {
        return "ri.mop." + actor.tenantId() + ".object." + stored.objectApiName() + "." + pk;
    }

    public record MappingView(Long id, Long branchId, Long datasourceId, Integer versionNo, LocalDateTime createdAt, StoredMapping definition) {}

    public record Preview(List<PreviewObject> objects, List<PreviewLink> links) {}

    public record PreviewObject(String type, String pk, Map<String, String> props) {}

    public record PreviewLink(String type, String targetType, String fromPk, String toPk) {}

    public record RunView(Long id, Long mappingId, Integer versionNo, String mode, String status, Integer insertedCount, Integer updatedCount, Integer linkCount, String message, LocalDateTime startedAt, LocalDateTime finishedAt, List<String> logs) {}
}
