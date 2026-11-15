package cn.muxicloud.mop.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.muxicloud.mop.ai.crypto.KeyCipher;
import cn.muxicloud.mop.common.error.MopException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {
    @Test
    void keyRoundTripAndRedaction() {
        KeyCipher cipher = new KeyCipher("ai-master-secret");
        String stored = cipher.encrypt("sk-live-secret");
        assertThat(stored).doesNotContain("sk-live-secret");
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-live-secret");
        assertThat(KeyCipher.redact("model call failed sk-live-secret", "sk-live-secret")).doesNotContain("sk-live-secret");
    }

    @Test
    void memoryLimiterIsPerModel() {
        MemoryModelRateLimiter limiter = new MemoryModelRateLimiter();
        assertThat(limiter.allow(1, 7, 1)).isTrue();
        assertThat(limiter.allow(1, 7, 1)).isFalse();
        assertThat(limiter.allow(1, 8, 1)).isTrue();
        assertThat(limiter.allow(2, 7, 1)).isTrue();
    }

    @Test
    void openAiClientTalksToAnyCompatibleEndpoint() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = hits.incrementAndGet() == 1
                    ? "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"note\\\":\\\"llm-mock\\\"}\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}".getBytes(StandardCharsets.UTF_8)
                    : "denied sk-live-secret".getBytes(StandardCharsets.UTF_8);
            if (hits.get() == 1) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(hits.get() == 1 ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiChatClient client = new OpenAiChatClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            OpenAiChatClient.ChatCompletion completion = client.complete(base, "local-model", "sk-live-secret", List.of(new OpenAiChatClient.Message("user", "ping")));
            assertThat(path.get()).isEqualTo("/v1/chat/completions");
            assertThat(authorization.get()).isEqualTo("Bearer sk-live-secret");
            assertThat(completion.content()).contains("llm-mock");
            assertThat(completion.promptTokens()).isEqualTo(3);
            assertThatThrownBy(() -> client.complete(base, "local-model", "sk-live-secret", List.of(new OpenAiChatClient.Message("user", "ping"))))
                    .isInstanceOf(MopException.class)
                    .hasMessageNotContaining("sk-live-secret");
        } finally {
            server.stop(0);
        }
    }
}
