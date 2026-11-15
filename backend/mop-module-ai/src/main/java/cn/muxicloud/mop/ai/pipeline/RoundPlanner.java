package cn.muxicloud.mop.ai.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RoundPlanner {
    private static final List<String> ROUNDS = List.of("R1", "R2", "R3", "R4");
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema;

    public RoundPlanner() {
        try (InputStream input = RoundPlanner.class.getResourceAsStream("/schema/candidates.schema.json")) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(input);
        } catch (Exception ex) {
            throw new IllegalStateException("候选 JSON Schema 没有装载", ex);
        }
    }

    public static List<String> rounds() {
        return ROUNDS;
    }

    public static List<String> windows(String text) {
        List<String> windows = new ArrayList<>();
        for (String part : text.split("(?<=。)")) {
            if (!part.isBlank()) {
                windows.add(part.trim());
            }
        }
        if (windows.isEmpty() && !text.isBlank()) {
            windows.add(text.trim());
        }
        return windows;
    }

    public String prompt(String round, String evidence, String prior) {
        return render("prompts/v1/round-" + round + ".txt", round, evidence, prior);
    }

    public String repair(String round, String evidence) {
        return render("prompts/v1/repair.txt", round, evidence, "");
    }

    public List<Draft> parse(String output, String evidence) {
        JsonNode root;
        try {
            root = mapper.readTree(output);
        } catch (Exception ex) {
            throw new IllegalArgumentException("模型输出不是 JSON");
        }
        Set<ValidationMessage> problems = schema.validate(root);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(problems.iterator().next().getMessage());
        }
        List<Draft> drafts = new ArrayList<>();
        for (JsonNode node : root.path("candidates")) {
            String quote = node.path("evidence").asText("");
            if (!evidence.contains(quote)) {
                continue;
            }
            drafts.add(new Draft(
                    node.path("type").asText(),
                    node.path("apiName").asText(),
                    node.path("displayName").asText(),
                    quote,
                    node.path("confidence").asDouble(0.5),
                    node));
        }
        return drafts;
    }

    private String render(String path, String round, String evidence, String prior) {
        try (InputStream input = RoundPlanner.class.getResourceAsStream("/" + path)) {
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return template.replace("{{round}}", round).replace("{{evidence}}", evidence).replace("{{prior}}", prior == null ? "" : prior);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record Draft(String type, String apiName, String displayName, String evidence, double confidence, JsonNode source) {}
}
