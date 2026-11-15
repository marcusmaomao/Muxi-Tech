package cn.muxicloud.mop.semantic.quality;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.model.api.EffectiveProperties;
import cn.muxicloud.mop.model.api.EffectiveProperties.PropNode;
import cn.muxicloud.mop.model.api.EffectiveProperties.TypeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CompiledModelBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CompiledModelBuilder() {
    }

    public static Map<String, Object> build(CatalogSnapshot snapshot, long revision) {
        List<TypeNode> types = new ArrayList<>();
        List<PropNode> properties = new ArrayList<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (CatalogElement element : snapshot.elements()) {
            if (!"OBJECT_TYPE".equals(element.type())) {
                continue;
            }
            JsonNode payload = read(element.payload());
            List<String> parents = texts(payload.get("parentUids"));
            types.add(new TypeNode(element.uid(), element.apiName(), text(payload, "displayName"), parents));
            names.put(element.uid(), element.apiName());
            for (String parent : parents) {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(element.uid());
            }
        }
        for (CatalogElement element : snapshot.elements()) {
            if (!"PROPERTY".equals(element.type())) {
                continue;
            }
            JsonNode payload = read(element.payload());
            properties.add(new PropNode(element.uid(), element.apiName(), text(payload, "displayName"), text(payload, "ownerUid"), payload.path("override").asBoolean(false)));
        }
        List<Map<String, Object>> compiledTypes = new ArrayList<>();
        for (TypeNode type : types) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", type.uid());
            row.put("apiName", type.apiName());
            row.put("displayName", type.displayName());
            row.put("parents", type.parentUids().stream().map(names::get).filter(name -> name != null).toList());
            row.put("ancestors", ancestors(type.uid(), types).stream().map(names::get).filter(name -> name != null).toList());
            row.put("descendants", descendants(type.uid(), children).stream().map(names::get).filter(name -> name != null).toList());
            EffectiveProperties.Report effective = EffectiveProperties.of(type.uid(), types, properties);
            row.put("properties", effective.properties().stream().map(property -> Map.of(
                    "apiName", property.apiName(),
                    "origin", property.origin(),
                    "sourceType", property.sourceTypeApiName() == null ? "" : property.sourceTypeApiName())).toList());
            row.put("propertyConflicts", effective.conflicts().stream().map(EffectiveProperties.NameConflict::apiName).toList());
            compiledTypes.add(row);
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (CatalogElement element : snapshot.elements()) {
            if (!"LINK_TYPE".equals(element.type())) {
                continue;
            }
            JsonNode payload = read(element.payload());
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("uid", element.uid());
            link.put("apiName", element.apiName());
            link.put("source", names.get(text(payload, "sourceUid")));
            link.put("target", names.get(text(payload, "targetUid")));
            link.put("cardinality", text(payload, "cardinality"));
            links.add(link);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("namespace", snapshot.namespace());
        model.put("revision", revision);
        model.put("types", compiledTypes);
        model.put("links", links);
        return model;
    }

    private static List<String> ancestors(String uid, List<TypeNode> types) {
        Map<String, List<String>> parents = new LinkedHashMap<>();
        for (TypeNode type : types) {
            parents.put(type.uid(), type.parentUids());
        }
        List<String> out = new ArrayList<>();
        ArrayDeque<String> queue = new ArrayDeque<>(parents.getOrDefault(uid, List.of()));
        Set<String> seen = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String next = queue.removeFirst();
            if (!seen.add(next)) {
                continue;
            }
            out.add(next);
            queue.addAll(parents.getOrDefault(next, List.of()));
        }
        return out;
    }

    private static List<String> descendants(String uid, Map<String, List<String>> children) {
        List<String> out = new ArrayList<>();
        ArrayDeque<String> queue = new ArrayDeque<>(children.getOrDefault(uid, List.of()));
        Set<String> seen = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String next = queue.removeFirst();
            if (!seen.add(next)) {
                continue;
            }
            out.add(next);
            queue.addAll(children.getOrDefault(next, List.of()));
        }
        return out;
    }

    private static JsonNode read(String payload) {
        try {
            return payload == null || payload.isBlank() ? JSON.createObjectNode() : JSON.readTree(payload);
        } catch (Exception ex) {
            return JSON.createObjectNode();
        }
    }

    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }
}
