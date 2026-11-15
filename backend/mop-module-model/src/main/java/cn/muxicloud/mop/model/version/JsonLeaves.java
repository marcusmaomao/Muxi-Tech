package cn.muxicloud.mop.model.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把对象打成叶子字段，再按字段做差异和合并。数组整体当作一个字段，避免下标错位。
 */
public final class JsonLeaves {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonLeaves() {}

    public static Map<String, JsonNode> leaves(JsonNode node) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return out;
        }
        walk("", node, out);
        return out;
    }

    public static List<FieldChange> diff(JsonNode before, JsonNode after) {
        Map<String, JsonNode> left = leaves(before);
        Map<String, JsonNode> right = leaves(after);
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(left.keySet());
        paths.addAll(right.keySet());
        List<FieldChange> changes = new ArrayList<>();
        for (String path : paths) {
            JsonNode oldValue = left.get(path);
            JsonNode newValue = right.get(path);
            if (oldValue == null) {
                changes.add(new FieldChange("add", path, null, newValue));
            } else if (newValue == null) {
                changes.add(new FieldChange("remove", path, oldValue, null));
            } else if (!oldValue.equals(newValue)) {
                changes.add(new FieldChange("replace", path, oldValue, newValue));
            }
        }
        return changes;
    }

    public static boolean same(JsonNode left, JsonNode right) {
        if (left == null || left.isNull()) {
            return right == null || right.isNull();
        }
        if (right == null || right.isNull()) {
            return false;
        }
        return leaves(left).equals(leaves(right));
    }

    public static JsonNode apply(JsonNode base, List<FieldChange> changes) {
        ObjectNode root = base != null && base.isObject() ? base.deepCopy() : JSON.createObjectNode();
        for (FieldChange change : changes) {
            if ("remove".equals(change.op())) {
                remove(root, change.path());
            } else if (change.after() != null) {
                set(root, change.path(), change.after());
            }
        }
        return root;
    }

    private static void walk(String path, JsonNode node, Map<String, JsonNode> out) {
        if (node.isObject()) {
            if (node.isEmpty()) {
                out.put(path.isEmpty() ? "/" : path, node);
                return;
            }
            node.fields().forEachRemaining(entry -> walk(path + "/" + escape(entry.getKey()), entry.getValue(), out));
            return;
        }
        out.put(path.isEmpty() ? "/" : path, node);
    }

    private static void set(ObjectNode root, String path, JsonNode value) {
        List<String> parts = parts(path);
        ObjectNode cursor = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            JsonNode child = cursor.get(parts.get(i));
            if (child == null || !child.isObject()) {
                child = cursor.putObject(parts.get(i));
            }
            cursor = (ObjectNode) child;
        }
        if (!parts.isEmpty()) {
            cursor.set(parts.get(parts.size() - 1), value.deepCopy());
        }
    }

    private static void remove(ObjectNode root, String path) {
        List<String> parts = parts(path);
        ObjectNode cursor = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            JsonNode child = cursor.get(parts.get(i));
            if (child == null || !child.isObject()) {
                return;
            }
            cursor = (ObjectNode) child;
        }
        if (!parts.isEmpty()) {
            cursor.remove(parts.get(parts.size() - 1));
        }
    }

    private static List<String> parts(String path) {
        String[] raw = path.split("/", -1);
        List<String> parts = new ArrayList<>();
        for (int i = 1; i < raw.length; i++) {
            parts.add(unescape(raw[i]));
        }
        return parts;
    }

    private static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static String unescape(String key) {
        return key.replace("~1", "/").replace("~0", "~");
    }
}
