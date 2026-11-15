package cn.muxicloud.mop.runtime.graph;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.tenant.TenantContext;
import cn.muxicloud.mop.tenant.TenantCypherGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import static org.neo4j.driver.Values.parameters;

@Component
public class GraphWriter {
    private final Driver driver;
    private final ObjectMapper json = new ObjectMapper();

    public GraphWriter(Driver driver) {
        this.driver = driver;
    }

    public WriteCount mergeObjects(List<ObjectRow> rows) {
        return mergeObjects(rows, "SYNC");
    }

    public WriteCount mergeObjects(List<ObjectRow> rows, String source) {
        if (rows.isEmpty()) {
            return new WriteCount(0, 0);
        }
        rows.forEach(row -> api(row.type()));
        try {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (ObjectRow row : rows) {
                Map<String, Object> item = new HashMap<>();
                item.put("type", row.type());
                item.put("pk", row.pk());
                item.put("rid", row.rid());
                item.put("src", source);
                item.put("propsJson", json.writeValueAsString(row.props()));
                payload.add(item);
            }
            try (Session session = driver.session()) {
                var record = session.run(TenantCypherGuard.MERGE_OBJECTS, parameters("tenant", tenant(), "rows", payload)).single();
                return new WriteCount(record.get("inserted").asInt(0), record.get("updated").asInt(0));
            }
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.SYNC_FAILED, ex.getMessage(), null);
        }
    }

    public int mergeLinks(List<LinkRow> links) {
        return mergeLinks(links, "SYNC");
    }

    public int mergeLinks(List<LinkRow> links, String source) {
        if (links.isEmpty()) {
            return 0;
        }
        links.forEach(link -> {
            api(link.linkType());
            api(link.fromType());
            api(link.toType());
        });
        List<Map<String, Object>> payload = new ArrayList<>();
        for (LinkRow link : links) {
            Map<String, Object> item = new HashMap<>();
            item.put("linkType", link.linkType());
            item.put("fromType", link.fromType());
            item.put("fromPk", link.fromPk());
            item.put("toType", link.toType());
            item.put("toPk", link.toPk());
            item.put("key", link.fromPk() + "\u001f" + link.toPk());
            item.put("src", source);
            payload.add(item);
        }
        try (Session session = driver.session()) {
            return session.run(TenantCypherGuard.MERGE_LINKS, parameters("tenant", tenant(), "links", payload)).single().get("linked").asInt(0);
        }
    }

    public Neighborhood neighbors(String type, String pk) {
        api(type);
        if (pk == null || pk.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "请提供主键", null);
        }
        try (Session session = driver.session()) {
            var records = session.run(TenantCypherGuard.READ_NEIGHBORS, parameters("tenant", tenant(), "type", type, "pk", pk)).list();
            if (records.isEmpty()) {
                throw new MopException(ErrorCode.NOT_FOUND);
            }
            GraphObject center = new GraphObject(records.get(0).get("pk").asString(), props(records.get(0).get("propsJson").asString(null)));
            List<Neighbor> neighbors = new ArrayList<>();
            for (var record : records) {
                if (record.get("neighborPk").isNull()) {
                    continue;
                }
                neighbors.add(new Neighbor(
                        record.get("link").asString(""),
                        record.get("neighborType").asString(),
                        record.get("neighborPk").asString(),
                        props(record.get("neighborProps").asString(null))));
            }
            return new Neighborhood(center, neighbors);
        }
    }

    public List<GraphObject> objects(String type) {
        api(type);
        try (Session session = driver.session()) {
            return session.run(TenantCypherGuard.READ_OBJECTS, parameters("tenant", tenant(), "type", type)).list(record ->
                    new GraphObject(record.get("pk").asString(), props(record.get("propsJson").asString(null))));
        }
    }

    private Map<String, String> props(String raw) {
        Map<String, String> props = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return props;
        }
        try {
            props.putAll(json.readValue(raw, new TypeReference<LinkedHashMap<String, String>>() {}));
        } catch (Exception ex) {
            props.put("raw", raw);
        }
        return props;
    }

    private static void api(String name) {
        try {
            TenantCypherGuard.requireApiName(name);
        } catch (IllegalArgumentException ex) {
            throw new MopException(ErrorCode.VALIDATION, "类型名不合法", null);
        }
    }

    private static long tenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new MopException(ErrorCode.VALIDATION, "缺少租户", null);
        }
        return tenantId;
    }

    public record ObjectRow(String type, String pk, String rid, Map<String, String> props) {}

    public record LinkRow(String linkType, String fromType, String fromPk, String toType, String toPk) {}

    public record WriteCount(int inserted, int updated) {}

    public record GraphObject(String pk, Map<String, String> props) {}

    public record Neighbor(String link, String type, String pk, Map<String, String> props) {}

    public record Neighborhood(GraphObject center, List<Neighbor> neighbors) {}
}
