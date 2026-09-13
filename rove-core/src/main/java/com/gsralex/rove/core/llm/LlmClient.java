package com.gsralex.rove.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsralex.rove.core.common.Message;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LlmClient {

    private final LlmConfig config;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public LlmClient(LlmConfig config) {
        this.config = config;
    }

    public LlmResp chat(List<Message> messages) {
        try {
            HttpResponse<String> resp = http.send(req(body(messages, false)), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode content = json.readTree(resp.body())
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");
            return new LlmResp(content.isTextual() ? content.asText() : "");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public LlmResp stream(List<Message> messages, Consumer<String> onToken) {
        try {
            HttpResponse<InputStream> resp =
                    http.send(req(body(messages, true)), HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                if (resp.statusCode() / 100 != 2) {
                    throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": " + reader.readLine());
                }
                return readStream(reader, onToken);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest req(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(config.baseUrl()) + "/chat/completions"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String body(List<Message> messages, boolean stream) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", config.model());
        req.put("stream", stream);
        req.put(
                "messages",
                messages.stream()
                        .map(m -> Map.of("role", m.role().name().toLowerCase(), "content", m.content()))
                        .toList());
        return json.writeValueAsString(req);
    }

    private LlmResp readStream(BufferedReader reader, Consumer<String> onToken) throws Exception {
        StringBuilder buf = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) {
                break;
            }
            JsonNode delta =
                    json.readTree(data).path("choices").path(0).path("delta").path("content");
            if (delta.isTextual()) {
                String token = delta.asText();
                buf.append(token);
                if (onToken != null) {
                    onToken.accept(token);
                }
            }
        }
        return new LlmResp(buf.toString());
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
