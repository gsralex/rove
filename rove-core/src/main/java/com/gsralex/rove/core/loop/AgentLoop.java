package com.gsralex.rove.core.loop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.common.Role;
import com.gsralex.rove.core.llm.Choice;
import com.gsralex.rove.core.llm.LlmResp;
import com.gsralex.rove.core.mcp.McpClient;
import com.gsralex.rove.core.skills.Skill;
import com.gsralex.rove.core.tool.Tool;
import com.gsralex.rove.core.tool.ToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AgentLoop implements Loop {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LoopContext context;
    private final int maxSteps;
    final List<Tool> mounted = new ArrayList<>();

    public AgentLoop(LoopContext context) {
        this(context, 50);
    }

    public AgentLoop(LoopContext context, int maxSteps) {
        this.context = Objects.requireNonNull(context, "context is required");
        this.maxSteps = maxSteps;
    }

    @Override
    public String id() {
        return context.id();
    }

    public LoopContext context() {
        return context;
    }

    void mount(Tool tool) {
        if (tool == null) {
            return;
        }
        mounted.removeIf(t -> t.name().equals(tool.name()));
        mounted.add(tool);
        context.tools().register(tool);
    }

    Tool findMounted(String name) {
        for (Tool t : mounted) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public String run(List<Message> messages, List<Tool> tools) {
        mounted.clear();
        if (tools != null) {
            for (Tool t : tools) {
                mount(t);
            }
        }
        for (Tool meta : metaTools()) {
            if (findMounted(meta.name()) == null) {
                mounted.add(0, meta);
            }
        }
        if (messages.stream().noneMatch(m -> m.role() == Role.USER)) {
            String msg = "Need a user message to start.";
            context.onError(new IllegalStateException(msg));
            context.onStatus(msg);
            return msg;
        }
        int step = 0;
        while (true) {
            step++;
            if (step > maxSteps) {
                log.warn("agent loop stopped: step {} exceeded maxSteps {}", step, maxSteps);
                String msg = "Step limit reached; stopped automatically. Send another message to continue.";
                context.onError(new IllegalStateException(msg));
                context.onStatus(msg);
                return msg;
            }
            FilterResult br = context.beforeRequest(messages);
            if (!br.allowed()) {
                return stopUser(br.reason());
            }
            LlmResp resp;
            try {
                context.onStatus("calling model");
                if (context.stream()) {
                    resp = context.llm().stream(messages, List.copyOf(mounted), context::onToken);
                } else {
                    resp = context.llm().chat(messages, List.copyOf(mounted));
                }
            } catch (RuntimeException e) {
                log.error("llm call failed", e);
                context.onError(e);
                return userError(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            if (resp.isEmpty()) {
                log.warn("llm returned no choices");
                String msg = "Model returned an empty response (network issue or timeout). Try again or rephrase.";
                context.onError(new IllegalStateException(msg));
                return msg;
            }
            Choice choice = resp.first();
            Message assistant = choice.message();
            messages.add(assistant);
            List<ToolCall> calls = assistant.toolCalls();
            if (calls == null || calls.isEmpty()) {
                String text = assistant.content();
                if (text == null || text.isBlank()) {
                    log.warn("llm returned empty content and no tool_calls");
                    String msg = "Model returned an empty response (network issue or timeout). Try again or rephrase.";
                    context.onError(new IllegalStateException(msg));
                    return msg;
                }
                FilterResult reply = context.beforeReply(text);
                if (!reply.allowed()) {
                    return stopUser(reply.reason());
                }
                return text;
            }
            for (ToolCall call : calls) {
                FilterResult bt = context.beforeTool(call);
                if (!bt.allowed()) {
                    messages.add(Message.tool(call.id(), "Error: " + bt.reason()));
                    return stopUser(bt.reason());
                }
                context.onToolCall(call);
                context.onStatus("tool " + call.name());
                String result;
                try {
                    result = invoke(call);
                } catch (RuntimeException e) {
                    log.error("tool {} failed", call.name(), e);
                    context.onError(e);
                    return userError(e.getMessage() == null ? e.toString() : e.getMessage());
                }
                context.onToolResult(call.name(), result);
                messages.add(Message.tool(call.id(), result));
            }
        }
    }

    private String invoke(ToolCall call) {
        Map<String, Object> args = parseArgs(call.args());
        if (args.containsKey("_parse_error")) {
            return String.valueOf(args.get("_parse_error"));
        }
        Tool tool = findMounted(call.name());
        if (tool == null) {
            log.warn("tool not mounted: {}", call.name());
            return "Error: unknown tool " + call.name() + " (not mounted)";
        }
        return tool.call(args);
    }

    private List<Tool> metaTools() {
        return List.of(skillSearch(), loadSkill(), mountMcp());
    }

    private Tool skillSearch() {
        return new Tool() {
            @Override
            public String name() {
                return "skill_search";
            }

            @Override
            public String description() {
                return "Search installed skills by keyword. Returns top-k name+description.";
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"k\":{\"type\":\"integer\"}},\"required\":[\"query\"]}";
            }

            @Override
            public String call(Map<String, Object> args) {
                Map<String, Object> a = args == null ? Map.of() : args;
                String query = a.get("query") == null ? "" : String.valueOf(a.get("query"));
                int k = 5;
                Object rawK = a.get("k");
                if (rawK instanceof Number n) {
                    k = n.intValue();
                }
                List<Skill> hits = shortlistSkills(query, k);
                if (hits.isEmpty()) {
                    return "No skills matched.";
                }
                StringBuilder sb = new StringBuilder();
                for (Skill s : hits) {
                    sb.append("- ")
                            .append(s.name())
                            .append(": ")
                            .append(s.description())
                            .append('\n');
                }
                return sb.toString().trim();
            }
        };
    }

    private List<Skill> shortlistSkills(String query, int k) {
        if (context.skills() == null || query == null || query.isBlank() || k <= 0) {
            return List.of();
        }
        String q = query.toLowerCase(Locale.ROOT);
        return context.skills().list().stream()
                .filter(s -> containsIgnoreCase(s.name(), q) || containsIgnoreCase(s.description(), q))
                .limit(k)
                .toList();
    }

    private static boolean containsIgnoreCase(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private Tool loadSkill() {
        return new Tool() {
            @Override
            public String name() {
                return "load_skill";
            }

            @Override
            public String description() {
                return "Load a skill's SKILL.md body into context and mount tools listed in requires.";
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}";
            }

            @Override
            public String call(Map<String, Object> args) {
                Object raw = args == null ? null : args.get("name");
                String name = raw == null ? "" : String.valueOf(raw);
                Skill skill = context.skills() == null
                        ? null
                        : context.skills().find(name).orElse(null);
                if (skill == null) {
                    return "Error: unknown skill " + name;
                }
                List<String> ok = new ArrayList<>();
                List<String> missing = new ArrayList<>();
                for (String req : skill.requires()) {
                    context.tools()
                            .get(req)
                            .ifPresentOrElse(
                                    t -> {
                                        mount(t);
                                        ok.add(req);
                                    },
                                    () -> missing.add(req));
                }
                StringBuilder sb = new StringBuilder();
                sb.append("# Skill: ").append(skill.name()).append("\n\n");
                sb.append(skill.body() == null ? "" : skill.body());
                if (!ok.isEmpty()) {
                    sb.append("\n\nMounted tools: ").append(String.join(", ", ok));
                }
                if (!missing.isEmpty()) {
                    sb.append("\nMissing required tools (not registered): ").append(String.join(", ", missing));
                }
                return sb.toString();
            }
        };
    }

    private Tool mountMcp() {
        return new Tool() {
            @Override
            public String name() {
                return "mount_mcp";
            }

            @Override
            public String description() {
                return "Mount tools from an MCP server so their names/schemas are available to the model.";
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}";
            }

            @Override
            public String call(Map<String, Object> args) {
                Object raw = args == null ? null : args.get("name");
                String name = raw == null ? "" : String.valueOf(raw);
                McpClient client = context.mcp().get(name);
                if (client == null) {
                    return "Error: unknown MCP server " + name;
                }
                List<String> added = new ArrayList<>();
                for (Tool t : client.listTools()) {
                    mount(t);
                    added.add(t.name());
                }
                if (added.isEmpty()) {
                    return "MCP " + name + " exposed no tools.";
                }
                return "Mounted MCP tools: " + String.join(", ", added);
            }
        };
    }

    static Map<String, Object> parseArgs(String args) {
        if (args == null || args.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(args, new TypeReference<>() {});
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("_parse_error", "Error: invalid args JSON: " + e.getMessage());
            return err;
        }
    }

    private String stopUser(String reason) {
        String msg = reason == null || reason.isBlank() ? "Operation blocked" : reason;
        log.warn("stopped for user: {}", msg);
        context.onError(new IllegalStateException(msg));
        context.onStatus(msg);
        return msg;
    }

    private static String userError(String detail) {
        return "Call failed: " + detail + " (network issue or timeout). Try again later.";
    }
}
