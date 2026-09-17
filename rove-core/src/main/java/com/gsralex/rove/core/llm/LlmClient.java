package com.gsralex.rove.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.common.Role;
import com.gsralex.rove.core.tool.Tool;
import com.gsralex.rove.core.tool.ToolCall;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LlmClient implements Llm {

    private final LlmConfig config;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public LlmClient(LlmConfig config) {
        this.config = config;
    }

    @Override
    public LlmResp chat(List<Message> messages) {
        return chat(messages, List.of());
    }

    @Override
    public LlmResp chat(List<Message> messages, List<Tool> tools) {
        try {
            HttpResponse<String> resp =
                    http.send(req(body(messages, tools, false)), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return parse(json.readTree(resp.body()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LlmResp stream(List<Message> messages, List<Tool> tools, Consumer<String> onToken) {
        try {
            HttpResponse<InputStream> resp =
                    http.send(req(body(messages, tools, true)), HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                if (resp.statusCode() != 200) {
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

    LlmResp parse(JsonNode root) {
        JsonNode choicesNode = root.path("choices");
        if (!choicesNode.isArray() || choicesNode.isEmpty()) {
            return new LlmResp(List.of());
        }
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < choicesNode.size(); i++) {
            JsonNode c = choicesNode.get(i);
            JsonNode msg = c.path("message");
            List<ToolCall> calls = new ArrayList<>();
            JsonNode tcs = msg.path("tool_calls");
            if (tcs.isArray()) {
                for (JsonNode tc : tcs) {
                    JsonNode fn = tc.path("function");
                    calls.add(new ToolCall(
                            tc.path("id").asText(""),
                            tc.path("type").asText("function"),
                            fn.path("name").asText(""),
                            fn.path("arguments").asText("{}")));
                }
            }
            String content =
                    msg.path("content").isNull() || !msg.path("content").isTextual()
                            ? null
                            : msg.path("content").asText();
            Message assistant = Message.assistant(content, calls);
            String finish = c.path("finish_reason").isMissingNode()
                    ? null
                    : c.path("finish_reason").asText();
            choices.add(new Choice(c.path("index").asInt(i), assistant, finish));
        }
        return new LlmResp(choices);
    }

    private HttpRequest req(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(config.baseUrl()) + "/chat/completions"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String body(List<Message> messages, List<Tool> tools, boolean stream) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", config.model());
        req.put("stream", stream);
        req.put("messages", messages.stream().map(this::wireMessage).toList());
        if (tools != null && !tools.isEmpty()) {
            req.put("tools", tools.stream().map(this::wireTool).toList());
        }
        return json.writeValueAsString(req);
    }

    private Map<String, Object> wireMessage(Message m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", m.role().name().toLowerCase());
        if (m.role() == Role.TOOL) {
            row.put("tool_call_id", m.toolCallId());
            row.put("content", m.content());
            return row;
        }
        if (m.role() == Role.ASSISTANT && !m.toolCalls().isEmpty()) {
            if (m.content() != null) {
                row.put("content", m.content());
            } else {
                row.put("content", null);
            }
            row.put(
                    "tool_calls",
                    m.toolCalls().stream()
                            .map(c -> {
                                Map<String, Object> fn = new LinkedHashMap<>();
                                fn.put("name", c.name());
                                fn.put("arguments", c.args() == null ? "{}" : c.args());
                                Map<String, Object> tc = new LinkedHashMap<>();
                                tc.put("id", c.id());
                                tc.put("type", c.type() == null || c.type().isBlank() ? "function" : c.type());
                                tc.put("function", fn);
                                return tc;
                            })
                            .toList());
            return row;
        }
        row.put("content", m.content());
        return row;
    }

    private Map<String, Object> wireTool(Tool tool) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", tool.name());
        fn.put("description", tool.description() == null ? "" : tool.description());
        try {
            fn.put("parameters", json.readTree(tool.inputSchema() == null ? "{}" : tool.inputSchema()));
        } catch (Exception e) {
            fn.put("parameters", Map.of("type", "object"));
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function");
        item.put("function", fn);
        return item;
    }

    LlmResp readStream(BufferedReader reader, Consumer<String> onToken) throws Exception {
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallBuilder> builders = new LinkedHashMap<>();
        String finishReason = null;
        String line;
        while ((line = reader.readLine()) != null) {
            String data = sseData(line);
            if (data == null) {
                continue;
            }
            if ("[DONE]".equals(data)) {
                break;
            }
            JsonNode root = json.readTree(data);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new RuntimeException(
                        "LLM stream error: " + error.path("message").asText(error.toString()));
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray()) {
                continue;
            }
            for (JsonNode choice : choices) {
                JsonNode delta = choice.path("delta");
                JsonNode text = delta.path("content");
                if (text.isTextual()) {
                    content.append(text.asText());
                    if (onToken != null) {
                        onToken.accept(text.asText());
                    }
                }
                JsonNode toolCalls = delta.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode call : toolCalls) {
                        int index = call.path("index").asInt(builders.size());
                        builders.computeIfAbsent(index, k -> new ToolCallBuilder())
                                .merge(call);
                    }
                }
                JsonNode finish = choice.path("finish_reason");
                if (finish.isTextual() && !finish.asText().isBlank()) {
                    finishReason = finish.asText();
                }
            }
        }
        List<ToolCall> calls =
                builders.values().stream().map(ToolCallBuilder::build).toList();
        if (finishReason == null) {
            finishReason = calls.isEmpty() ? "stop" : "tool_calls";
        }
        Message assistant = Message.assistant(content.isEmpty() ? null : content.toString(), calls);
        return LlmResp.of(new Choice(0, assistant, finishReason));
    }

    private static String sseData(String line) {
        if (line.isEmpty() || line.charAt(0) == ':' || !line.startsWith("data:")) {
            return null;
        }
        String data = line.substring(5).strip();
        return data.isEmpty() ? null : data;
    }

    private static final class ToolCallBuilder {

        private String id = "";
        private String type = "function";
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder args = new StringBuilder();

        void merge(JsonNode call) {
            JsonNode callId = call.path("id");
            if (id.isEmpty() && callId.isTextual()) {
                id = callId.asText();
            }
            JsonNode kind = call.path("type");
            if (kind.isTextual() && !kind.asText().isBlank()) {
                type = kind.asText();
            }
            JsonNode function = call.path("function");
            JsonNode fnName = function.path("name");
            if (name.isEmpty() && fnName.isTextual()) {
                name.append(fnName.asText());
            }
            JsonNode arguments = function.path("arguments");
            if (arguments.isTextual()) {
                args.append(arguments.asText());
            }
        }

        ToolCall build() {
            return new ToolCall(id, type, name.toString(), args.isEmpty() ? "{}" : args.toString());
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
