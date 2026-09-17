package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.llm.Llm;
import com.gsralex.rove.core.mcp.McpClient;
import com.gsralex.rove.core.skills.SkillRegistry;
import com.gsralex.rove.core.tool.ToolCall;
import com.gsralex.rove.core.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LoopContext {

    private final String id;
    private final Llm llm;
    private final List<Filter> filters = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<String, McpClient> mcp = new LinkedHashMap<>();
    private SkillRegistry skills;
    private ToolRegistry tools = new ToolRegistry();
    private LoopManager loopManager = LoopManager.shared();
    private boolean stream;

    public LoopContext(Llm llm) {
        this(null, llm);
    }

    public LoopContext(String id, Llm llm) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.llm = llm;
    }

    public String id() {
        return id;
    }

    public Llm llm() {
        return llm;
    }

    public List<Filter> filters() {
        return List.copyOf(filters);
    }

    public List<Listener> listeners() {
        return List.copyOf(listeners);
    }

    public SkillRegistry skills() {
        return skills;
    }

    public ToolRegistry tools() {
        return tools;
    }

    public Map<String, McpClient> mcp() {
        return Map.copyOf(mcp);
    }

    public LoopManager loopManager() {
        return loopManager;
    }

    public boolean stream() {
        return stream;
    }

    public LoopContext filter(Filter filter) {
        if (filter != null) {
            filters.add(filter);
        }
        return this;
    }

    public LoopContext filters(List<Filter> more) {
        if (more != null) {
            filters.addAll(more);
        }
        return this;
    }

    public LoopContext listener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    public LoopContext listeners(List<Listener> more) {
        if (more != null) {
            listeners.addAll(more);
        }
        return this;
    }

    public LoopContext skills(SkillRegistry skills) {
        this.skills = skills;
        return this;
    }

    public LoopContext tools(ToolRegistry tools) {
        this.tools = tools == null ? new ToolRegistry() : tools;
        return this;
    }

    public LoopContext mcp(String name, McpClient client) {
        if (name != null && client != null) {
            mcp.put(name, client);
        }
        return this;
    }

    public LoopContext mcp(Map<String, McpClient> more) {
        if (more != null) {
            mcp.putAll(more);
        }
        return this;
    }

    public LoopContext loopManager(LoopManager loopManager) {
        this.loopManager = loopManager == null ? LoopManager.shared() : loopManager;
        return this;
    }

    public LoopContext stream(boolean stream) {
        this.stream = stream;
        return this;
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
