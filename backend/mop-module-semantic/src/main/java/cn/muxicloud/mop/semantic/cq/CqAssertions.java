package cn.muxicloud.mop.semantic.cq;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 断言只看这次查询的结果，不看能力问题上手填的状态。 */
public final class CqAssertions {
    private CqAssertions() {
    }

    public static String check(JsonNode assertion, boolean ask, Boolean askValue, List<Map<String, String>> rows) {
        String kind = assertion == null ? "COUNT" : assertion.path("kind").asText("COUNT");
        return switch (kind) {
            case "NOT_EMPTY" -> empty(ask, askValue, rows) ? "结果为空" : null;
            case "EMPTY" -> empty(ask, askValue, rows) ? null : "结果不为空";
            case "ASK" -> ask && askValue != null && askValue == assertion.path("value").asBoolean() ? null : "ASK 结果不符";
            case "COUNT" -> count(assertion, ask, askValue, rows);
            case "RESULT_EQUALS" -> same(rows, assertion == null ? null : assertion.get("rows")) ? null : "结果集不一致";
            case "CONTAINS" -> contains(rows, assertion == null ? null : assertion.get("rows")) ? null : "结果未包含期望行";
            default -> "不认识的断言 " + kind;
        };
    }

    private static String count(JsonNode assertion, boolean ask, Boolean askValue, List<Map<String, String>> rows) {
        int actual = ask ? (Boolean.TRUE.equals(askValue) ? 1 : 0) : rows.size();
        String op = assertion == null ? ">=" : assertion.path("op").asText(">=");
        int expected = assertion == null ? 1 : assertion.path("value").asInt(1);
        return compare(actual, op, expected) ? null : "数量 " + actual + " 不满足 " + op + " " + expected;
    }

    static boolean compare(int actual, String op, int expected) {
        return switch (op) {
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case "=", "==" -> actual == expected;
            case "!=" -> actual != expected;
            default -> false;
        };
    }

    private static boolean empty(boolean ask, Boolean askValue, List<Map<String, String>> rows) {
        return ask ? !Boolean.TRUE.equals(askValue) : rows.isEmpty();
    }

    private static boolean same(List<Map<String, String>> rows, JsonNode expected) {
        return signatures(rows).equals(signatures(expected));
    }

    private static boolean contains(List<Map<String, String>> rows, JsonNode expected) {
        List<String> actual = signatures(rows);
        if (expected == null || !expected.isArray()) {
            return false;
        }
        for (JsonNode row : expected) {
            if (!actual.contains(signature(row))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> signatures(List<Map<String, String>> rows) {
        List<String> values = new ArrayList<>();
        for (Map<String, String> row : rows) {
            values.add(signature(row));
        }
        values.sort(String::compareTo);
        return values;
    }

    private static List<String> signatures(JsonNode rows) {
        List<String> values = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                values.add(signature(row));
            }
        }
        values.sort(String::compareTo);
        return values;
    }

    private static String signature(Map<String, String> row) {
        return new TreeMap<>(row).toString();
    }

    private static String signature(JsonNode row) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (row != null && row.isObject()) {
            row.fields().forEachRemaining(field -> sorted.put(field.getKey(), field.getValue().asText()));
        }
        return sorted.toString();
    }
}
