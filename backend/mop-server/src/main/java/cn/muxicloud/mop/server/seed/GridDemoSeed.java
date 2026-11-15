package cn.muxicloud.mop.server.seed;

import cn.muxicloud.mop.basic.service.DatasourceService;
import cn.muxicloud.mop.basic.service.FileService;
import cn.muxicloud.mop.model.api.ElementWriter;
import cn.muxicloud.mop.model.entity.BranchEntity;
import cn.muxicloud.mop.model.entity.ProjectEntity;
import cn.muxicloud.mop.model.service.ModelService;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.FieldBinding;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.Incremental;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.LinkBinding;
import cn.muxicloud.mop.runtime.mapping.MappingShapes.MappingDefinition;
import cn.muxicloud.mop.runtime.service.MappingService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.system.entity.TenantEntity;
import cn.muxicloud.mop.system.entity.UserEntity;
import cn.muxicloud.mop.system.mapper.TenantMapper;
import cn.muxicloud.mop.system.mapper.UserMapper;
import cn.muxicloud.mop.system.seed.DemoSeedProperties;
import cn.muxicloud.mop.tenant.TenantContext;
import cn.muxicloud.mop.tenant.TenantIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class GridDemoSeed implements ApplicationRunner {
    static final String PROJECT_CODE = "grid-demo";
    static final String DOCUMENT = """
            城东站是一座变电站。一号变压器安装在城东站。城东线从城东站送出。巡检记录写明一号变压器运行正常。
            """;

    private static final Logger log = LoggerFactory.getLogger(GridDemoSeed.class);

    private final DemoSeedProperties demo;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final ModelService models;
    private final ElementWriter elements;
    private final DatasourceService datasources;
    private final FileService files;
    private final MappingService mappings;
    private final ObjectMapper json = new ObjectMapper();

    public GridDemoSeed(
            DemoSeedProperties demo,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword,
            TenantMapper tenantMapper,
            UserMapper userMapper,
            ModelService models,
            ElementWriter elements,
            DatasourceService datasources,
            FileService files,
            MappingService mappings) {
        this.demo = demo;
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.models = models;
        this.elements = elements;
        this.datasources = datasources;
        this.files = files;
        this.mappings = mappings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo.isSeed() || !demo.isGrid()) {
            return;
        }
        TenantEntity tenant = tenant();
        if (tenant == null) {
            return;
        }
        TenantContext.set(tenant.getId());
        try {
            if (models.projects().stream().anyMatch(project -> PROJECT_CODE.equals(project.getCode()))) {
                return;
            }
            UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getUsername, demo.getAdminUsername()).last("LIMIT 1"));
            if (user == null) {
                return;
            }
            AuthenticatedUser actor = new AuthenticatedUser(user.getId(), user.getUid(), user.getUsername(), user.getDisplayName(), tenant.getId());
            ProjectEntity project = models.createProject(PROJECT_CODE, "电网设备", "https://example.com/ont/grid#", "电网", null, null, "沐曦自编的电网设备演示", actor);
            long branchId = models.branches(project.getId()).stream().filter(branch -> "main".equals(branch.getName())).map(BranchEntity::getId).findFirst().orElseThrow();
            String stations = add(branchId, "CQ", cq("有哪些变电站", "whichStations"), actor);
            String transformers = add(branchId, "CQ", cq("有哪些变压器", "whichTransformers"), actor);
            String lines = add(branchId, "CQ", cq("有哪些线路", "whichLines"), actor);
            String inspections = add(branchId, "CQ", cq("有哪些巡检", "whichInspections"), actor);
            String asset = add(branchId, "OBJECT_TYPE", object("设备资产", "Asset", stations), actor);
            String substation = add(branchId, "OBJECT_TYPE", object("变电站", "Substation", stations), actor);
            String transformer = add(branchId, "OBJECT_TYPE", object("变压器", "Transformer", transformers), actor);
            String line = add(branchId, "OBJECT_TYPE", object("线路", "Line", lines), actor);
            String inspection = add(branchId, "OBJECT_TYPE", object("巡检记录", "Inspection", inspections), actor);
            String stationName = add(branchId, "PROPERTY", property("站名", "stationName", substation), actor);
            String transformerName = add(branchId, "PROPERTY", property("变压器名", "transformerName", transformer), actor);
            String lineName = add(branchId, "PROPERTY", property("线路名", "lineName", line), actor);
            String note = add(branchId, "PROPERTY", property("结论", "note", inspection), actor);
            String installedAt = add(branchId, "LINK_TYPE", link("安装于", "installedAt", transformer, substation, transformers), actor);
            String leaves = add(branchId, "LINK_TYPE", link("送出", "leavesStation", line, substation, lines), actor);
            String about = add(branchId, "LINK_TYPE", link("巡检对象", "aboutTransformer", inspection, transformer, inspections), actor);
            add(branchId, "AXIOM", axiom(transformer, asset), actor);
            add(branchId, "AXIOM", axiom(line, asset), actor);
            add(branchId, "TERM", term("变压器", "transformerTerm"), actor);
            URI database = database();
            long datasourceId = datasources.create(new DatasourceService.DatasourceInput(
                    project.getId(), "电网样例库", "MYSQL", database.getHost(), database.getPort(), database.getPath().substring(1), dbUser, dbPassword), actor).id();
            files.storeBytes("电网设备说明.txt", DOCUMENT.getBytes(StandardCharsets.UTF_8), "text/plain", project.getId(), actor);
            map(branchId, datasourceId, substation, "src_station", "name", stationName, null, null, null, actor);
            map(branchId, datasourceId, transformer, "src_equipment", "name", transformerName, installedAt, "station_id", substation, actor);
            map(branchId, datasourceId, line, "src_line", "name", lineName, leaves, "from_station_id", substation, actor);
            map(branchId, datasourceId, inspection, "src_inspection", "note", note, about, "transformer_id", transformer, actor);
            log.info("电网演示数据已导入 project={}", PROJECT_CODE);
        } finally {
            TenantContext.clear();
        }
    }

    private void map(long branchId, long datasourceId, String objectUid, String table, String column, String propertyUid, String linkUid, String linkColumn, String targetUid, AuthenticatedUser actor) {
        List<LinkBinding> links = linkUid == null ? List.of() : List.of(new LinkBinding("FOREIGN_KEY", linkUid, linkColumn, targetUid, null, null, null));
        mappings.save(branchId, datasourceId, new MappingDefinition(
                objectUid, table, "id", new Incremental("UPDATED_AT", "updated_at"),
                List.of(new FieldBinding(propertyUid, column)), links), actor);
    }

    private String add(long branchId, String type, ObjectNode payload, AuthenticatedUser actor) {
        return elements.add(branchId, type, payload, actor, "演示数据 " + type).uid();
    }

    private ObjectNode cq(String name, String api) {
        return json.createObjectNode().put("displayName", name).put("apiName", api).put("priority", "P1").put("acceptance", "能从样例库列出" + name);
    }

    private ObjectNode object(String name, String api, String cqUid) {
        ObjectNode node = json.createObjectNode().put("displayName", name).put("apiName", api);
        node.putArray("cqUids").add(cqUid);
        return node;
    }

    private ObjectNode property(String name, String api, String ownerUid) {
        ObjectNode node = json.createObjectNode().put("displayName", name).put("apiName", api).put("ownerUid", ownerUid);
        node.putObject("valueType").put("kind", "BASIC").put("basic", "STRING");
        return node;
    }

    private ObjectNode link(String name, String api, String sourceUid, String targetUid, String cqUid) {
        ObjectNode node = json.createObjectNode().put("displayName", name).put("apiName", api).put("sourceUid", sourceUid).put("targetUid", targetUid).put("cardinality", "MANY_TO_ONE").put("invertible", false);
        node.putArray("cqUids").add(cqUid);
        return node;
    }

    private ObjectNode axiom(String subjectUid, String objectUid) {
        return json.createObjectNode().put("kind", "SUBCLASS").put("subjectUid", subjectUid).put("objectUid", objectUid);
    }

    private ObjectNode term(String name, String api) {
        return json.createObjectNode().put("displayName", name).put("apiName", api);
    }

    private TenantEntity tenant() {
        TenantIgnore.ignore();
        try {
            return tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>().eq(TenantEntity::getCode, demo.getTenantCode()).last("LIMIT 1"));
        } finally {
            TenantIgnore.use();
        }
    }

    private URI database() {
        String raw = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring("jdbc:".length()) : jdbcUrl;
        int query = raw.indexOf('?');
        if (query >= 0) {
            raw = raw.substring(0, query);
        }
        return URI.create(raw);
    }
}
