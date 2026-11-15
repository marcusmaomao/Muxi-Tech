package cn.muxicloud.mop.model.version;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 以公共祖先为基准的三方合并。不同字段自动合并，同一字段值不同则记为冲突。
 */
public final class MergePlanner {
    private MergePlanner() {}

    public record ElementSide(String type, String apiName, JsonNode payload) {}

    public record Conflict(
            String uid, String type, String apiName, String reason, List<String> paths,
            JsonNode base, JsonNode source, JsonNode target) {}

    public record Outcome(String uid, String type, String apiName, String op, JsonNode payload) {}

    public record Plan(List<Outcome> changes, List<Conflict> conflicts) {}

    public static Plan plan(
            Map<String, ElementSide> base,
            Map<String, ElementSide> source,
            Map<String, ElementSide> target,
            Map<String, String> choices) {
        Set<String> uids = new LinkedHashSet<>();
        uids.addAll(base.keySet());
        uids.addAll(source.keySet());
        uids.addAll(target.keySet());
        List<Outcome> changes = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        for (String uid : uids) {
            ElementSide ancestor = base.get(uid);
            ElementSide ours = source.get(uid);
            ElementSide theirs = target.get(uid);
            boolean sourceChanged = !same(ancestor, ours);
            boolean targetChanged = !same(ancestor, theirs);
            if (!sourceChanged && !targetChanged) {
                continue;
            }
            String choice = choices.get(uid);
            if ("SOURCE".equals(choice)) {
                take(changes, uid, ours, theirs);
                continue;
            }
            if ("TARGET".equals(choice)) {
                continue;
            }
            if (sourceChanged && !targetChanged) {
                take(changes, uid, ours, theirs);
                continue;
            }
            if (!sourceChanged) {
                continue;
            }
            if (ours == null || theirs == null || ancestor == null) {
                if (same(ours, theirs)) {
                    take(changes, uid, ours, theirs);
                } else {
                    conflicts.add(conflict(uid, ancestor, ours, theirs, ours == null || theirs == null ? "DELETE" : "BOTH", List.of()));
                }
                continue;
            }
            List<FieldChange> fromSource = JsonLeaves.diff(ancestor.payload(), ours.payload());
            List<FieldChange> fromTarget = JsonLeaves.diff(ancestor.payload(), theirs.payload());
            Map<String, FieldChange> sourceFields = byPath(fromSource);
            Map<String, FieldChange> targetFields = byPath(fromTarget);
            List<String> clashed = new ArrayList<>();
            for (String path : sourceFields.keySet()) {
                FieldChange other = targetFields.get(path);
                if (other != null && !sameChange(sourceFields.get(path), other)) {
                    clashed.add(path);
                }
            }
            if (!clashed.isEmpty()) {
                conflicts.add(conflict(uid, ancestor, ours, theirs, "FIELD", clashed));
                continue;
            }
            List<FieldChange> combined = new ArrayList<>(fromSource);
            for (FieldChange change : fromTarget) {
                if (!sourceFields.containsKey(change.path())) {
                    combined.add(change);
                }
            }
            JsonNode merged = JsonLeaves.apply(ancestor.payload(), combined);
            if (!JsonLeaves.same(merged, theirs.payload())) {
                changes.add(new Outcome(uid, ours.type(), ours.apiName(), theirs == null ? "ADD" : "MODIFY", merged));
            }
        }
        return new Plan(changes, conflicts);
    }

    private static void take(List<Outcome> changes, String uid, ElementSide chosen, ElementSide current) {
        if (same(chosen, current)) {
            return;
        }
        if (chosen == null) {
            changes.add(new Outcome(uid, current.type(), current.apiName(), "DELETE", null));
            return;
        }
        changes.add(new Outcome(uid, chosen.type(), chosen.apiName(), current == null ? "ADD" : "MODIFY", chosen.payload()));
    }

    private static Conflict conflict(
            String uid, ElementSide base, ElementSide source, ElementSide target, String reason, List<String> paths) {
        ElementSide known = source != null ? source : target != null ? target : base;
        return new Conflict(
                uid,
                known == null ? null : known.type(),
                known == null ? null : known.apiName(),
                reason,
                paths,
                base == null ? null : base.payload(),
                source == null ? null : source.payload(),
                target == null ? null : target.payload());
    }

    private static boolean same(ElementSide left, ElementSide right) {
        if (left == null || right == null) {
            return left == right;
        }
        return JsonLeaves.same(left.payload(), right.payload());
    }

    private static boolean sameChange(FieldChange left, FieldChange right) {
        return left.op().equals(right.op())
                && (left.after() == null ? right.after() == null : left.after().equals(right.after()));
    }

    private static Map<String, FieldChange> byPath(List<FieldChange> changes) {
        Map<String, FieldChange> out = new LinkedHashMap<>();
        for (FieldChange change : changes) {
            out.put(change.path(), change);
        }
        return out;
    }
}
