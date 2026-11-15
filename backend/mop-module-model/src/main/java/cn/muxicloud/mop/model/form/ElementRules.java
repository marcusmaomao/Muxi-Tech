package cn.muxicloud.mop.model.form;

import com.fasterxml.jackson.databind.JsonNode;

public final class ElementRules {
    private ElementRules() {
    }

    public static boolean requiresCq(String type) {
        return "OBJECT_TYPE".equals(type) || "LINK_TYPE".equals(type);
    }

    public static boolean hasCq(JsonNode payload) {
        JsonNode node = payload == null ? null : payload.get("cqUids");
        if (node == null || !node.isArray() || node.isEmpty()) {
            return false;
        }
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    public static boolean apiNameChanged(String previous, String next) {
        return previous != null && next != null && !previous.equals(next);
    }

    public static String cardinalityProblem(JsonNode payload) {
        if (payload == null || (!payload.has("minCount") && !payload.has("maxCount"))) {
            return null;
        }
        int min = payload.path("minCount").asInt(0);
        int max = payload.has("maxCount") ? payload.path("maxCount").asInt() : min;
        if (min < 0 || max < min) {
            return "基数不合法";
        }
        if (payload.path("required").asBoolean(false) && min < 1) {
            return "必填属性的最小基数至少为 1";
        }
        return null;
    }

    public static String inverseProblem(JsonNode payload) {
        if (payload == null || !payload.path("invertible").asBoolean(false)) {
            return null;
        }
        JsonNode inverse = payload.get("inverse");
        if (inverse == null || inverse.path("apiName").asText("").isBlank() || inverse.path("displayName").asText("").isBlank()) {
            return "可逆关系必须填写反向名称和 API 名";
        }
        return null;
    }
}
