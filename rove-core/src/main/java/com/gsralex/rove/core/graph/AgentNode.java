package com.gsralex.rove.core.graph;

import com.google.common.base.Preconditions;
import com.gsralex.rove.core.agent.Agent;
import com.gsralex.rove.core.loop.LoopContext;
import java.util.Map;
import java.util.Objects;

public final class AgentNode implements Node {

    private final String name;
    private final Agent agent;
    private final String inputKey;
    private final String outputKey;

    private AgentNode(String name, Agent agent, String inputKey, String outputKey) {
        this.name = name;
        this.agent = agent;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
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
        Object raw = inputKey == null ? state.get("user") : state.get(inputKey);
        String input = raw == null ? "" : String.valueOf(raw);
        String reply = ctx.loopManager().call(() -> agent.run(input));
        if (outputKey != null) {
            state.put(outputKey, reply);
        }
        state.put(name + ".sessionId", agent.sessionId());
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
        private Agent agent;
        private String inputKey = "user";
        private String outputKey;

        private Builder(String name) {
            this.name = name;
        }

        public Builder agent(Agent agent) {
            this.agent = agent;
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

        public AgentNode build() {
            Preconditions.checkNotNull(agent, "agent is required");
            return new AgentNode(name, agent, inputKey, outputKey);
        }
    }
}
