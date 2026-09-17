package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.llm.Llm;
import com.gsralex.rove.core.mcp.McpClient;
import com.gsralex.rove.core.skills.SkillRegistry;
import com.gsralex.rove.core.tool.ToolCall;
import com.gsralex.rove.core.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LoopContext {

    private final String id;
    private final Llm llm;
    private final List<Filter> filters;
    private final List<Listener> listeners;
    private final SkillRegistry skills;
    private final ToolRegistry tools;
    private final Loop loop;
    private final Map<String, McpClient> mcp;
    private final LoopManager loopManager;

    public LoopContext(
            Llm llm,
            List<Filter> filters,
            List<Listener> listeners,
            SkillRegistry skills,
            ToolRegistry tools,
            Loop loop,
            Map<String, McpClient> mcp) {
        this(null, llm, filters, listeners, skills, tools, loop, mcp, null);
    }

    public LoopContext(
            String id,
            Llm llm,
            List<Filter> filters,
            List<Listener> listeners,
            SkillRegistry skills,
            ToolRegistry tools,
            Loop loop,
            Map<String, McpClient> mcp) {
        this(id, llm, filters, listeners, skills, tools, loop, mcp, null);
    }

    public LoopContext(
            String id,
            Llm llm,
            List<Filter> filters,
            List<Listener> listeners,
            SkillRegistry skills,
            ToolRegistry tools,
            Loop loop,
            Map<String, McpClient> mcp,
            LoopManager loopManager) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.llm = llm;
        this.filters = filters == null ? List.of() : List.copyOf(filters);
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.skills = skills;
        this.tools = tools == null ? new ToolRegistry() : tools;
        this.loop = loop;
        this.mcp = mcp == null ? Map.of() : Map.copyOf(mcp);
        this.loopManager = loopManager == null ? LoopManager.shared() : loopManager;
    }

    public String id() {
        return id;
    }

    public Llm llm() {
        return llm;
    }

    public List<Filter> filters() {
        return filters;
    }

    public List<Listener> listeners() {
        return listeners;
    }

    public SkillRegistry skills() {
        return skills;
    }

    public ToolRegistry tools() {
        return tools;
    }

    public Loop loop() {
        return loop;
    }

    public Map<String, McpClient> mcp() {
        return mcp;
    }

    public LoopManager loopManager() {
        return loopManager;
    }

    public FilterResult beforeRequest(List<Message> messages) {
        for (Filter f : filters) {
            FilterResult r = f.beforeRequest(messages);
            if (!r.allowed()) {
                return r;
            }
        }
        return FilterResult.allow();
    }

    public FilterResult beforeTool(ToolCall call) {
        for (Filter f : filters) {
            FilterResult r = f.beforeTool(call);
            if (!r.allowed()) {
                return r;
            }
        }
        return FilterResult.allow();
    }

    public FilterResult beforeReply(String reply) {
        for (Filter f : filters) {
            FilterResult r = f.beforeReply(reply);
            if (!r.allowed()) {
                return r;
            }
        }
        return FilterResult.allow();
    }

    public void onError(Throwable error) {
        for (Listener l : listeners) {
            l.onError(error);
        }
    }

    public void onStatus(String status) {
        for (Listener l : listeners) {
            l.onStatus(status);
        }
    }

    public void onToken(String token) {
        for (Listener l : listeners) {
            l.onToken(token);
        }
    }

    public void onToolCall(ToolCall call) {
        for (Listener l : listeners) {
            l.onToolCall(call);
        }
    }

    public void onToolResult(String name, String result) {
        for (Listener l : listeners) {
            l.onToolResult(name, result);
        }
    }

    public void nodeStart(String name) {
        for (Listener l : listeners) {
            l.onNodeStart(name);
        }
    }

    public void nodeEnd(String name) {
        for (Listener l : listeners) {
            l.onNodeEnd(name);
        }
    }
}
