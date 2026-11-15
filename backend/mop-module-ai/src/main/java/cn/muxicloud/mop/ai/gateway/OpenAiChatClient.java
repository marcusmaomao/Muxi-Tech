package cn.muxicloud.mop.ai.gateway;

import cn.muxicloud.mop.ai.crypto.KeyCipher;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class OpenAiChatClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ChatCompletion complete(String baseUrl, String modelName, String apiKey, List<Message> messages) {
        String endpoint = trim(baseUrl) + "/chat/completions";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        ArrayNode array = body.putArray("messages");
        for (Message message : messages) {
            array.addObject().put("role", message.role()).put("content", message.content());
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MopException(ErrorCode.VALIDATION, "模型调用失败：" + response.statusCode() + " " + KeyCipher.redact(shorten(response.body()), apiKey), null);
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int prompt = root.path("usage").path("prompt_tokens").asInt(0);
            int completion = root.path("usage").path("completion_tokens").asInt(0);
            return new ChatCompletion(content, prompt, completion);
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "模型调用失败：" + KeyCipher.redact(ex.getMessage(), apiKey), null);
        }
    }

    private static String trim(String baseUrl) {
        String value = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return value.endsWith("/v1") ? value : value + "/v1";
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 180 ? oneLine : oneLine.substring(0, 180);
    }

    public record Message(String role, String content) {}

    public record ChatCompletion(String content, int promptTokens, int completionTokens) {}
}
