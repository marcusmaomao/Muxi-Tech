package cn.muxicloud.mop.ai.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按当前本体生成抽取约束，并判断每条结果能不能进入审核。
 */
public final class ExtractionPipeline {
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[\\p{IsHan}A-Za-z0-9_]{1,64}");
    private final ObjectMapper mapper = new ObjectMapper();

    public String prompt(String schemaText, String evidence) {
        try (InputStream input = ExtractionPipeline.class.getResourceAsStream("/prompts/v1/extract.txt")) {
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return template.replace("{{schema}}", schemaText).replace("{{evidence}}", evidence);
        } catch (Exception ex) {
            throw new IllegalStateException("抽取提示词没有装载", ex);
        }
    }

    public String schemaText(Index index) {
        StringBuilder builder = new StringBuilder();
        for (TypeSpec type : index.types().values()) {
            builder.append("类型 ").append(type.apiName()).append(" 属性");
            if (type.properties().isEmpty()) {
                builder.append("（无）");
            } else {
                builder.append(' ').append(String.join("、", type.properties()));
            }
            builder.append('\n');
        }
        for (LinkSpec link : index.links().values()) {
            builder.append("关系 ").append(link.apiName()).append(" 从 ").append(link.sourceType()).append(" 到 ").append(link.targetType()).append('\n');
        }
        return builder.toString();
    }

    public List<Draft> interpret(String output, String evidence, Index index) {
        JsonNode root;
        try {
            root = mapper.readTree(output);
        } catch (Exception ex) {
            return List.of(draft("ENTITY", "", Map.of(), snippet(evidence), null, "QUARANTINE", "JSON_INVALID"));
        }
        if (!schema(index).validate(root).isEmpty()) {
            return List.of(draft("ENTITY", "", Map.of(), snippet(evidence), null, "QUARANTINE", "JSON_SCHEMA"));
        }
        List<Draft> entities = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonNode node : root.path("entities")) {
            Draft item = entity(node, evidence, index);
            entities.add(item);
            if ("REVIEW".equals(item.state())) {
                names.put(item.name(), item.typeName());
            }
        }
        List<Draft> all = new ArrayList<>(entities);
        for (JsonNode node : root.path("links")) {
            all.add(link(node, evidence, index, names));
        }
        return all;
    }

    private Draft entity(JsonNode node, String evidence, Index index) {
        String type = node.path("type").asText("");
        String name = node.path("name").asText("");
        String quote = node.path("evidence").asText("");
        Double confidence = node.has("confidence") ? node.path("confidence").asDouble() : null;
        Map<String, String> props = new LinkedHashMap<>();
        node.path("props").fields().forEachRemaining(entry -> props.put(entry.getKey(), entry.getValue().asText("")));
        String problem = problem(quote, evidence, name);
        if (problem == null && !index.types().containsKey(type)) {
            problem = "UNKNOWN_TYPE";
        }
        TypeSpec spec = index.types().get(type);
        if (problem == null && spec != null) {
            for (String key : props.keySet()) {
                if (!spec.properties().contains(key)) {
                    problem = "UNKNOWN_PROPERTY";
                    break;
                }
            }
        }
        return draft("ENTITY", type, props, quote, confidence, problem == null ? "REVIEW" : "QUARANTINE", problem, name, null, null);
    }

    private Draft link(JsonNode node, String evidence, Index index, Map<String, String> names) {
        String type = node.path("type").asText("");
        String from = node.path("from").asText("");
        String to = node.path("to").asText("");
        String quote = node.path("evidence").asText("");
        Double confidence = node.has("confidence") ? node.path("confidence").asDouble() : null;
        String problem = problem(quote, evidence, from);
        LinkSpec spec = index.links().get(type);
        if (problem == null && spec == null) {
            problem = "UNKNOWN_LINK";
        }
        String fromType = names.get(from);
        String toType = names.get(to);
        if (problem == null && (fromType == null || toType == null)) {
            problem = "ENDPOINT";
        }
        if (problem == null && (!spec.sourceType().equals(fromType) || !spec.targetType().equals(toType))) {
            problem = "RANGE";
        }
        return new Draft("LINK", type, from, from, to, fromType, toType, Map.<String, String>of(), quote, confidence, problem == null ? "REVIEW" : "QUARANTINE", problem);
    }

    private String problem(String quote, String evidence, String name) {
        if (quote == null || quote.isBlank() || evidence == null || !evidence.contains(quote)) {
            return "EVIDENCE";
        }
        if (name == null || !NAME.matcher(name).matches()) {
            return "NAME";
        }
        return null;
    }

    private JsonSchema schema(Index index) {
        Set<String> types = index.types().keySet();
        var root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("entities").add("links");
        var properties = root.putObject("properties");
        properties.set("entities", arraySchema(types, true));
        properties.set("links", arraySchema(index.links().keySet(), false));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(root);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode arraySchema(Set<String> names, boolean entity) {
        var array = mapper.createObjectNode();
        array.put("type", "array");
        var item = array.putObject("items");
        item.put("type", "object");
        item.put("additionalProperties", true);
        var required = item.putArray("required");
        required.add("type").add("evidence");
        if (entity) {
            required.add("name");
        } else {
            required.add("from").add("to");
        }
        var properties = item.putObject("properties");
        var type = properties.putObject("type");
        type.put("type", "string");
        var enums = type.putArray("enum");
        for (String name : names) {
            enums.add(name);
        }
        properties.putObject("evidence").put("type", "string").put("minLength", 1);
        properties.putObject("confidence").put("type", "number");
        if (entity) {
            properties.putObject("name").put("type", "string");
            properties.putObject("props").put("type", "object");
        } else {
            properties.putObject("from").put("type", "string");
            properties.putObject("to").put("type", "string");
        }
        return array;
    }

    private static Draft draft(String kind, String type, Map<String, String> props, String quote, Double confidence, String state, String reason) {
        return draft(kind, type, props, quote, confidence, state, reason, "", null, null);
    }

    private static Draft draft(String kind, String type, Map<String, String> props, String quote, Double confidence, String state, String reason, String name, String from, String to) {
        return new Draft(kind, type, name, from, to, null, null, props, quote == null ? "" : quote, confidence, state, reason);
    }

    private static String snippet(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "（空文档）";
        }
        String trimmed = evidence.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }

    public record Index(Map<String, TypeSpec> types, Map<String, LinkSpec> links) {}

    public record TypeSpec(String apiName, Set<String> properties) {}

    public record LinkSpec(String apiName, String sourceType, String targetType) {}

    public record Draft(
            String kind,
            String typeName,
            String name,
            String from,
            String to,
            String fromType,
            String toType,
            Map<String, String> props,
            String evidence,
            Double confidence,
            String state,
            String reason) {}
}
