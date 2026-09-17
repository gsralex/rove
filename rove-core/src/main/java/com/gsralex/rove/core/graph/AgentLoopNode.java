package com.gsralex.rove.core.graph;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.loop.AgentLoop;
import com.gsralex.rove.core.loop.Loop;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AgentLoopNode implements Node {

    private final String name;
    private final Loop loop;
    private final List<Tool> tools;
    private final String system;
    private final String inputKey;
    private final String outputKey;
    private final boolean inheritMessages;
    private final int maxSteps;

    private AgentLoopNode(Builder b) {
        this.name = b.name;
        this.loop = b.loop;
        this.tools = b.tools == null ? List.of() : List.copyOf(b.tools);
        this.system = b.system;
        this.inputKey = b.inputKey;
        this.outputKey = b.outputKey;
        this.inheritMessages = b.inheritMessages;
        this.maxSteps = b.maxSteps;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void execute(Map<String, Object> state, LoopContext ctx) {
        Loop used = loop != null ? loop : buildLoop(ctx);
        List<Message> local = new ArrayList<>();
        if (inheritMessages) {
            local.addAll(LlmNode.messagesOf(state));
        }
        if (system != null) {
            local.addFirst(Message.system(system));
        }
        Object input = inputKey == null ? null : state.get(inputKey);
        if (input != null) {
            local.add(Message.user(String.valueOf(input)));
        }
        String answer = used.run(local, tools);
        if (outputKey != null) {
            state.put(outputKey, answer);
        }
        state.put(name + ".messages", local);
        state.put(name + ".sessionId", used.id());
    }

    private Loop buildLoop(LoopContext ctx) {
        return new AgentLoop(ctx, maxSteps <= 0 ? 20 : maxSteps);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Node n && Objects.equals(name, n.name());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    public static final class Builder {
        private final String name;
        private Loop loop;
        private List<Tool> tools = List.of();
        private String system;
        private String inputKey;
        private String outputKey;
        private boolean inheritMessages;
        private int maxSteps = 20;

        private Builder(String name) {
            this.name = name;
        }

        public Builder loop(Loop loop) {
            this.loop = loop;
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder inputKey(String inputKey) {
            this.inputKey = inputKey;
            return this;
        }

        public Builder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder inheritMessages(boolean inherit) {
            this.inheritMessages = inherit;
            return this;
        }

        public AgentLoopNode build() {
            return new AgentLoopNode(this);
        }
    }
}
