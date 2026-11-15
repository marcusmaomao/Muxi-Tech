package cn.muxicloud.mop.common.cq;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只放行带租户参数的读查询。写子句在去掉字符串和注释之后按整词拒绝。
 */
public final class CqCypherPolicy {
    private static final Set<String> FORBIDDEN = Set.of(
            "CREATE", "MERGE", "SET", "DELETE", "DETACH", "REMOVE", "DROP", "CALL", "FOREACH", "LOAD",
            "INSERT", "GRANT", "DENY", "REVOKE", "ALTER", "RENAME", "TERMINATE", "START", "STOP");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private CqCypherPolicy() {
    }

    public static void check(String cypher) {
        if (cypher == null || cypher.isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "Cypher 不能为空", null);
        }
        String code = strip(cypher);
        if (!code.contains("$tenant")) {
            throw new MopException(ErrorCode.VALIDATION, "Cypher 必须使用 $tenant 参数，不能把租户写进查询文本", null);
        }
        if (!code.contains("_t")) {
            throw new MopException(ErrorCode.VALIDATION, "Cypher 必须按 _t = $tenant 限定租户", null);
        }
        boolean returning = false;
        Matcher matcher = WORD.matcher(code);
        while (matcher.find()) {
            String word = matcher.group().toUpperCase(Locale.ROOT);
            if (FORBIDDEN.contains(word)) {
                throw new MopException(ErrorCode.VALIDATION, "Cypher 只允许读查询，不能包含 " + word, null);
            }
            if ("RETURN".equals(word)) {
                returning = true;
            }
        }
        if (!returning) {
            throw new MopException(ErrorCode.VALIDATION, "Cypher 读查询必须有 RETURN", null);
        }
    }

    static String strip(String cypher) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < cypher.length()) {
            char current = cypher.charAt(index);
            if (current == '\'' || current == '"') {
                index = skipString(cypher, index, out);
                continue;
            }
            if (current == '/' && index + 1 < cypher.length() && cypher.charAt(index + 1) == '*') {
                int end = cypher.indexOf("*/", index + 2);
                index = end < 0 ? cypher.length() : end + 2;
                out.append(' ');
                continue;
            }
            if (current == '/' && index + 1 < cypher.length() && cypher.charAt(index + 1) == '/') {
                int end = cypher.indexOf('\n', index);
                index = end < 0 ? cypher.length() : end;
                out.append(' ');
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    private static int skipString(String cypher, int start, StringBuilder out) {
        char quote = cypher.charAt(start);
        int index = start + 1;
        while (index < cypher.length()) {
            char current = cypher.charAt(index);
            if (current == '\\' && index + 1 < cypher.length()) {
                index += 2;
                continue;
            }
            if (current == quote) {
                out.append(' ');
                return index + 1;
            }
            index++;
        }
        out.append(' ');
        return cypher.length();
    }
}
