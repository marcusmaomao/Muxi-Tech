package cn.muxicloud.mop.model.inherit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计算一个类从全部父类得到的有效属性。同名但不是同一个属性时记为冲突，除非本类显式重定义。
 */
public final class EffectiveProperties {
    private EffectiveProperties() {}

    public record TypeNode(String uid, String apiName, String displayName, List<String> parentUids) {}

    public record PropNode(String uid, String apiName, String displayName, String ownerUid, boolean override) {}

    public record PropertyView(
            String uid, String apiName, String displayName, String origin,
            String sourceTypeUid, String sourceTypeApiName, String sourceTypeName) {}

    public record NameConflict(String typeUid, String typeApiName, String apiName, List<String> sourceTypeApiNames) {}

    public record Report(List<PropertyView> properties, List<NameConflict> conflicts) {}

    public static Report of(String typeUid, List<TypeNode> types, List<PropNode> properties) {
        return new Walker(types, properties).visit(typeUid);
    }

    public static List<String> affected(String typeUid, List<TypeNode> types) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (TypeNode type : types) {
            for (String parent : type.parentUids()) {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(type.uid());
            }
        }
        List<String> out = new ArrayList<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        queue.add(typeUid);
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

    private static final class Walker {
        private final Map<String, TypeNode> types = new LinkedHashMap<>();
        private final Map<String, List<PropNode>> owned = new LinkedHashMap<>();
        private final Map<String, Report> memo = new LinkedHashMap<>();
        private final Set<String> stack = new LinkedHashSet<>();

        private Walker(List<TypeNode> typeNodes, List<PropNode> properties) {
            for (TypeNode type : typeNodes) {
                types.put(type.uid(), type);
            }
            for (PropNode property : properties) {
                owned.computeIfAbsent(property.ownerUid(), key -> new ArrayList<>()).add(property);
            }
        }

        private Report visit(String typeUid) {
            Report cached = memo.get(typeUid);
            if (cached != null) {
                return cached;
            }
            if (!stack.add(typeUid)) {
                return new Report(List.of(), List.of());
            }
            TypeNode type = types.get(typeUid);
            Map<String, Map<String, PropertyView>> incoming = new LinkedHashMap<>();
            if (type != null) {
                for (String parent : type.parentUids()) {
                    if (parent == null || parent.isBlank() || parent.equals(typeUid)) {
                        continue;
                    }
                    for (PropertyView property : visit(parent).properties()) {
                        incoming.computeIfAbsent(property.apiName(), key -> new LinkedHashMap<>()).put(property.uid(), property);
                    }
                }
            }
            stack.remove(typeUid);
            List<PropertyView> properties = new ArrayList<>();
            List<NameConflict> conflicts = new ArrayList<>();
            Set<String> declared = new LinkedHashSet<>();
            for (PropNode own : owned.getOrDefault(typeUid, List.of())) {
                declared.add(own.apiName());
                Map<String, PropertyView> others = incoming.getOrDefault(own.apiName(), Map.of());
                if (!others.isEmpty() && !own.override()) {
                    conflicts.add(conflict(type, own.apiName(), others.values()));
                    continue;
                }
                properties.add(ownView(type, own));
            }
            for (Map.Entry<String, Map<String, PropertyView>> entry : incoming.entrySet()) {
                if (declared.contains(entry.getKey())) {
                    continue;
                }
                if (entry.getValue().size() == 1) {
                    properties.add(asInherited(entry.getValue().values().iterator().next()));
                } else {
                    conflicts.add(conflict(type, entry.getKey(), entry.getValue().values()));
                }
            }
            Report report = new Report(List.copyOf(properties), List.copyOf(conflicts));
            memo.put(typeUid, report);
            return report;
        }

        private static PropertyView asInherited(PropertyView source) {
            return new PropertyView(
                    source.uid(), source.apiName(), source.displayName(), "INHERITED",
                    source.sourceTypeUid(), source.sourceTypeApiName(), source.sourceTypeName());
        }

        private static PropertyView ownView(TypeNode type, PropNode own) {
            String typeUid = type == null ? own.ownerUid() : type.uid();
            String typeApi = type == null ? "" : type.apiName();
            String typeName = type == null ? typeApi : blank(type.displayName(), typeApi);
            return new PropertyView(own.uid(), own.apiName(), own.displayName(), "OWN", typeUid, typeApi, typeName);
        }

        private static NameConflict conflict(TypeNode type, String apiName, Iterable<PropertyView> sources) {
            Set<String> names = new LinkedHashSet<>();
            for (PropertyView source : sources) {
                names.add(blank(source.sourceTypeApiName(), source.apiName()));
            }
            return new NameConflict(
                    type == null ? "" : type.uid(),
                    type == null ? "" : type.apiName(),
                    apiName,
                    List.copyOf(names));
        }

        private static String blank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
