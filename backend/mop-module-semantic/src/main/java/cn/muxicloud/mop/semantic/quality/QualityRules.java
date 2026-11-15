package cn.muxicloud.mop.semantic.quality;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.semantic.quality.QualityCheck.Finding;
import cn.muxicloud.mop.semantic.reason.ElkClassifier.NamedClass;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QualityRules {
    private static final ObjectMapper JSON = new ObjectMapper();

    private QualityRules() {
    }

    public static List<QualityCheck> checks() {
        return List.of(
                check("STR_CYCLE", "STRUCTURE", QualityRules::cycles),
                check("STR_DANGLING", "STRUCTURE", QualityRules::dangling),
                check("STR_DUP_APINAME", "STRUCTURE", QualityRules::duplicates),
                check("STR_ORPHAN_CLASS", "STRUCTURE", QualityRules::orphans),
                check("STR_NO_CQ", "STRUCTURE", QualityRules::missingCq),
                check("STR_NAMING", "STRUCTURE", QualityRules::naming),
                check("STR_NO_DESC", "STRUCTURE", QualityRules::missingDescription),
                check("SEM_UNSAT", "SEMANTIC", QualityRules::unsatisfiable),
                check("SEM_INCONSISTENT", "SEMANTIC", QualityRules::inconsistent),
                check("CON_SHACL_COMPILE", "CONSTRAINT", QualityRules::shapeErrors),
                check("CON_VIOLATION_RATE", "CONSTRAINT", QualityRules::violationRate),
                check("GOAL_CQ_PASS", "GOAL", QualityRules::cqPass),
                check("GOAL_ELEMENT_COVERAGE", "GOAL", QualityRules::elementCoverage));
    }

    private static QualityCheck check(String code, String dimension, java.util.function.BiFunction<QualityContext, String, List<Finding>> body) {
        return new QualityCheck() {
            public String code() {
                return code;
            }

            public String dimension() {
                return dimension;
            }

            public List<Finding> run(QualityContext context) {
                return body.apply(context, dimension);
            }
        };
    }

    private static List<Finding> cycles(QualityContext context, String dimension) {
        Map<String, List<String>> parents = new HashMap<>();
        for (CatalogElement type : context.ofType("OBJECT_TYPE")) {
            parents.put(type.uid(), texts(read(type.payload()).get("parentUids")));
        }
        List<Finding> findings = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (String start : parents.keySet()) {
            if (cycle(start, parents, new HashSet<>(), new HashSet<>()) && reported.add(start)) {
                findings.add(finding("STR_CYCLE", dimension, "Violation", start, "继承关系形成了环"));
            }
        }
        return findings;
    }

    private static boolean cycle(String uid, Map<String, List<String>> parents, Set<String> stack, Set<String> seen) {
        if (!stack.add(uid)) {
            return true;
        }
        if (!seen.add(uid)) {
            stack.remove(uid);
            return false;
        }
        for (String parent : parents.getOrDefault(uid, List.of())) {
            if (cycle(parent, parents, stack, seen)) {
                return true;
            }
        }
        stack.remove(uid);
        return false;
    }

    private static List<Finding> dangling(QualityContext context, String dimension) {
        Set<String> live = new HashSet<>();
        Map<String, String> types = new HashMap<>();
        for (CatalogElement element : context.snapshot().elements()) {
            live.add(element.uid());
            types.put(element.uid(), element.type());
        }
        List<Finding> findings = new ArrayList<>();
        for (CatalogElement element : context.snapshot().elements()) {
            JsonNode payload = read(element.payload());
            expect(findings, dimension, element.uid(), live, types, texts(payload.get("parentUids")), "OBJECT_TYPE");
            expect(findings, dimension, element.uid(), live, types, texts(payload.get("cqUids")), "CQ");
            expectOne(findings, dimension, element.uid(), live, types, text(payload, "ownerUid"), ownerType(element.type()));
            expectOne(findings, dimension, element.uid(), live, types, text(payload, "sourceUid"), "OBJECT_TYPE");
            expectOne(findings, dimension, element.uid(), live, types, text(payload, "targetUid"), "OBJECT_TYPE");
            expectOne(findings, dimension, element.uid(), live, types, text(payload, "fixtureUid"), "FIXTURE");
        }
        return findings;
    }

    private static String ownerType(String type) {
        return switch (type) {
            case "PROPERTY" -> "OBJECT_TYPE";
            case "ENUM_VALUE" -> "ENUM_TYPE";
            case "CQ_TEST" -> "CQ";
            default -> null;
        };
    }

    private static void expect(List<Finding> findings, String dimension, String from, Set<String> live, Map<String, String> types, List<String> uids, String expected) {
        for (String uid : uids) {
            expectOne(findings, dimension, from, live, types, uid, expected);
        }
    }

    private static void expectOne(List<Finding> findings, String dimension, String from, Set<String> live, Map<String, String> types, String uid, String expected) {
        if (uid == null || expected == null) {
            return;
        }
        if (!live.contains(uid) || !expected.equals(types.get(uid))) {
            findings.add(finding("STR_DANGLING", dimension, "Violation", from, "引用了不存在的元素"));
        }
    }

    private static List<Finding> duplicates(QualityContext context, String dimension) {
        Map<String, CatalogElement> seen = new HashMap<>();
        List<Finding> findings = new ArrayList<>();
        for (CatalogElement element : context.snapshot().elements()) {
            if (element.apiName() == null) {
                continue;
            }
            String key = element.type() + ":" + element.apiName();
            CatalogElement previous = seen.putIfAbsent(key, element);
            if (previous != null) {
                findings.add(finding("STR_DUP_APINAME", dimension, "Violation", element.uid(), "API 名重复：" + element.apiName()));
            }
        }
        return findings;
    }

    private static List<Finding> orphans(QualityContext context, String dimension) {
        Set<String> used = new HashSet<>();
        for (CatalogElement property : context.ofType("PROPERTY")) {
            String owner = text(read(property.payload()), "ownerUid");
            if (owner != null) {
                used.add(owner);
            }
        }
        for (CatalogElement link : context.ofType("LINK_TYPE")) {
            JsonNode payload = read(link.payload());
            String source = text(payload, "sourceUid");
            String target = text(payload, "targetUid");
            if (source != null) {
                used.add(source);
            }
            if (target != null) {
                used.add(target);
            }
        }
        for (CatalogElement type : context.ofType("OBJECT_TYPE")) {
            for (String parent : texts(read(type.payload()).get("parentUids"))) {
                used.add(parent);
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (CatalogElement type : context.ofType("OBJECT_TYPE")) {
            if (!used.contains(type.uid())) {
                findings.add(finding("STR_ORPHAN_CLASS", dimension, "Warning", type.uid(), "类没有属性、关系或子类"));
            }
        }
        return findings;
    }

    private static List<Finding> missingCq(QualityContext context, String dimension) {
        List<Finding> findings = new ArrayList<>();
        for (CatalogElement element : context.snapshot().elements()) {
            if (!"OBJECT_TYPE".equals(element.type()) && !"LINK_TYPE".equals(element.type())) {
                continue;
            }
            if (texts(read(element.payload()).get("cqUids")).isEmpty()) {
                findings.add(finding("STR_NO_CQ", dimension, "Violation", element.uid(), "缺少能力问题"));
            }
        }
        return findings;
    }

    private static List<Finding> naming(QualityContext context, String dimension) {
        List<Finding> findings = new ArrayList<>();
        for (CatalogElement type : context.ofType("OBJECT_TYPE")) {
            String api = type.apiName();
            if (api != null && !api.matches("[A-Z][A-Za-z0-9]*")) {
                findings.add(finding("STR_NAMING", dimension, "Warning", type.uid(), "类 API 名建议使用大写开头的驼峰"));
            }
        }
        return findings;
    }

    private static List<Finding> missingDescription(QualityContext context, String dimension) {
        List<Finding> findings = new ArrayList<>();
        for (CatalogElement type : context.ofType("OBJECT_TYPE")) {
            if (text(read(type.payload()), "description") == null) {
                findings.add(finding("STR_NO_DESC", dimension, "Warning", type.uid(), "类缺少说明"));
            }
        }
        return findings;
    }

    private static List<Finding> unsatisfiable(QualityContext context, String dimension) {
        if (context.classification() == null) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (NamedClass type : context.classification().unsatisfiable()) {
            String uid = context.ofType("OBJECT_TYPE").stream()
                    .filter(element -> type.apiName().equals(element.apiName()))
                    .map(CatalogElement::uid)
                    .findFirst()
                    .orElse(null);
            findings.add(finding("SEM_UNSAT", dimension, "Violation", uid, "类不可满足：" + type.displayName()));
        }
        return findings;
    }

    private static List<Finding> inconsistent(QualityContext context, String dimension) {
        if (context.classification() != null && !context.classification().consistent()) {
            return List.of(finding("SEM_INCONSISTENT", dimension, "Violation", null, "本体不一致"));
        }
        return List.of();
    }

    private static List<Finding> shapeErrors(QualityContext context, String dimension) {
        List<Finding> findings = new ArrayList<>();
        for (String error : context.shapeErrors()) {
            findings.add(finding("CON_SHACL_COMPILE", dimension, "Violation", null, error));
        }
        return findings;
    }

    private static List<Finding> violationRate(QualityContext context, String dimension) {
        return List.of();
    }

    private static List<Finding> cqPass(QualityContext context, String dimension) {
        if (!context.cqTestsPresent()) {
            return List.of();
        }
        if (!context.cqRunPresent()) {
            return List.of(finding("GOAL_CQ_PASS", dimension, "Violation", null, "能力问题测试尚未运行"));
        }
        if (!Boolean.TRUE.equals(context.cqGateOpen())) {
            return List.of(finding("GOAL_CQ_PASS", dimension, "Violation", null, "能力问题测试没有全部通过"));
        }
        return List.of();
    }

    private static List<Finding> elementCoverage(QualityContext context, String dimension) {
        if (!context.cqRunPresent() || context.elementCoverage() == null || context.elementCoverage() >= 1) {
            return List.of();
        }
        return List.of(finding("GOAL_ELEMENT_COVERAGE", dimension, "Warning", null, "元素覆盖率不足"));
    }

    private static Finding finding(String code, String dimension, String severity, String uid, String message) {
        return new Finding(code, dimension, severity, uid, message);
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
                if (item.isTextual() && !item.asText().isBlank()) {
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

    static Set<String> reachable(String start, Map<String, List<String>> parents) {
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            String next = stack.pop();
            if (!seen.add(next)) {
                continue;
            }
            stack.addAll(parents.getOrDefault(next, List.of()));
        }
        return seen;
    }
}
