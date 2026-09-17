package com.gsralex.rove.core.graph;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.llm.Llm;
import com.gsralex.rove.core.llm.LlmResp;
import com.gsralex.rove.core.loop.FilterResult;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LlmNode implements Node {

    private final String name;
    private final Llm llm;
    private final String system;
    private final String outputKey;
    private final List<Tool> tools;

    private LlmNode(String name, Llm llm, String system, String outputKey, List<Tool> tools) {
        this.name = name;
        this.llm = llm;
        this.system = system;
        this.outputKey = outputKey;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
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
        Llm client = llm != null ? llm : ctx.llm();
        List<Message> messages = messagesOf(state);
        if (system != null && messages.stream().noneMatch(m -> m.role().name().equals("SYSTEM"))) {
            messages.add(0, Message.system(system));
        }
        FilterResult br = ctx.beforeRequest(messages);
        if (!br.allowed()) {
            state.put("error", br.reason());
            return;
        }
        LlmResp resp = tools.isEmpty() ? client.chat(messages) : client.chat(messages, tools);
        if (resp.isEmpty() || resp.first().message() == null) {
            log.warn("llm node {} returned no choices", name);
            state.put("error", "Model returned an empty response (network issue or timeout). Try again or rephrase.");
            return;
        }
        Message assistant = resp.first().message();
        messages.add(assistant);
        state.put("messages", messages);
        String text = assistant.content() == null ? "" : assistant.content();
        if (text.isBlank() && assistant.toolCalls().isEmpty()) {
            log.warn("llm node {} returned empty content and no tool_calls", name);
            state.put("error", "Model returned an empty response (network issue or timeout). Try again or rephrase.");
            return;
        }
        FilterResult reply = ctx.beforeReply(text);
        if (!reply.allowed()) {
            state.put("error", reply.reason());
            return;
        }
        if (outputKey != null) {
            state.put(outputKey, text);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Message> messagesOf(Map<String, Object> state) {
        Object raw = state.get("messages");
        if (raw instanceof List<?> list) {
            List<Message> copy = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Message m) {
                    copy.add(m);
                }
            }
            return copy;
        }
        return new ArrayList<>();
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
        private Llm llm;
        private String system;
        private String outputKey;
        private List<Tool> tools = List.of();

        private Builder(String name) {
            this.name = name;
        }

        public Builder llm(Llm llm) {
            this.llm = llm;
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }

        public LlmNode build() {
            return new LlmNode(name, llm, system, outputKey, tools);
        }
    }
}
