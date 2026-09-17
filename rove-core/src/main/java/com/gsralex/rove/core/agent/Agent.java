package com.gsralex.rove.core.agent;

import com.google.common.base.Preconditions;
import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.common.Role;
import com.gsralex.rove.core.graph.Graph;
import com.gsralex.rove.core.llm.Llm;
import com.gsralex.rove.core.loop.AgentLoop;
import com.gsralex.rove.core.loop.Filter;
import com.gsralex.rove.core.loop.Listener;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.loop.LoopManager;
import com.gsralex.rove.core.mcp.McpClient;
import com.gsralex.rove.core.skills.FileSkillRegistry;
import com.gsralex.rove.core.skills.Skill;
import com.gsralex.rove.core.skills.SkillRegistry;
import com.gsralex.rove.core.tool.Tool;
import com.gsralex.rove.core.tool.ToolRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Agent {

    private final String name;
    private final Llm llm;
    private final Graph graph;
    private final AgentLoop loop;
    private final LoopContext context;
    private final LoopManager loopManager;
    private final SkillRegistry skills;
    private final ToolRegistry toolRegistry;
    private final String system;
    private final List<Message> messages = new ArrayList<>();

    private Agent(Builder b) {
        this.name = b.name;
        this.llm = b.llm;
        this.graph = b.graph;
        this.skills = b.skills;
        this.toolRegistry = b.toolRegistry;
        this.system = b.system;
        this.loopManager = b.loopManager == null ? LoopManager.shared() : b.loopManager;
        LoopContext ctx = b.loop != null ? b.loop.context() : new LoopContext(b.llm);
        ctx.filters(b.filters)
                .listeners(b.listeners)
                .skills(b.skills)
                .tools(b.toolRegistry)
                .mcp(b.mcp)
                .loopManager(loopManager);
        this.loop = b.loop != null ? b.loop : new AgentLoop(ctx, b.maxSteps);
        this.context = this.loop.context();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    public Graph graph() {
        return graph;
    }

    public SkillRegistry skills() {
        return skills;
    }

    public List<Message> messages() {
        return messages;
    }

    public String sessionId() {
        return loop.id();
    }

    public LoopManager loopManager() {
        return loopManager;
    }

    public LoopContext context() {
        return context;
    }

    public Skill installSkill(Path skillMd) {
        Preconditions.checkState(skills instanceof FileSkillRegistry, "installSkill requires FileSkillRegistry");
        return ((FileSkillRegistry) skills).install(skillMd);
    }

    public String run(String userText) {
        messages.add(Message.user(userText));
        if (graph != null) {
            Map<String, Object> result = runGraph(Map.of("messages", messages, "user", userText));
            Object reply = result.get("reply");
            if (reply != null) {
                return String.valueOf(reply);
            }
            return lastAssistant();
        }
        ensureSystem();
        return loop.run(messages, toolRegistry.list());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> run(Map<String, Object> input) {
        if (graph == null) {
            Object raw = input.get("messages");
            if (raw instanceof List<?> list) {
                messages.clear();
                for (Object o : list) {
                    if (o instanceof Message m) {
                        messages.add(m);
                    }
                }
            }
            if (input.get("user") instanceof String user) {
                messages.add(Message.user(user));
            }
            ensureSystem();
            String reply = loop.run(messages, toolRegistry.list());
            Map<String, Object> out = new HashMap<>(input);
            out.put("messages", messages);
            out.put("reply", reply);
            return out;
        }
        return runGraph(input);
    }

    private Map<String, Object> runGraph(Map<String, Object> input) {
        Map<String, Object> state = new HashMap<>(input);
        Map<String, Object> result = graph.run(state, context);
        Object ms = result.get("messages");
        if (ms instanceof List<?> list) {
            messages.clear();
            for (Object o : list) {
                if (o instanceof Message m) {
                    messages.add(m);
                }
            }
        }
        return result;
    }

    private void ensureSystem() {
        if (system == null) {
            return;
        }
        boolean has = false;
        for (Message m : messages) {
            if (m.role() == Role.SYSTEM) {
                has = true;
                break;
            }
        }
        if (!has) {
            messages.add(0, Message.system(system));
        }
    }

    private String lastAssistant() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.role() == Role.ASSISTANT && m.content() != null) {
                return m.content();
            }
        }
        return "";
    }

    public static final class Builder {
        private String name = "agent";
        private Llm llm;
        private Graph graph;
        private AgentLoop loop;
        private LoopManager loopManager;
        private SkillRegistry skills;
        private ToolRegistry toolRegistry = new ToolRegistry();
        private final Map<String, McpClient> mcp = new LinkedHashMap<>();
        private final List<Filter> filters = new ArrayList<>();
        private final List<Listener> listeners = new ArrayList<>();
        private String system;
        private int maxSteps = 20;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder llm(Llm llm) {
            this.llm = llm;
            return this;
        }

        public Builder graph(Graph graph) {
            this.graph = graph;
            return this;
        }

        public Builder loop(AgentLoop loop) {
            this.loop = loop;
            return this;
        }

        public Builder loopManager(LoopManager loopManager) {
            this.loopManager = loopManager;
            return this;
        }

        public Builder skills(SkillRegistry skills) {
            this.skills = skills;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder tool(Tool tool) {
            this.toolRegistry.register(tool);
            return this;
        }

        public Builder mcp(String name, McpClient client) {
            this.mcp.put(name, client);
            return this;
        }

        public Builder filter(Filter filter) {
            this.filters.add(filter);
            return this;
        }

        public Builder listener(Listener listener) {
            this.listeners.add(listener);
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Agent build() {
            Preconditions.checkNotNull(llm, "llm is required");
            return new Agent(this);
        }
    }
}
