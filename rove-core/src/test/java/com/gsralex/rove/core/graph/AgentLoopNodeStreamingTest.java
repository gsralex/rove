package com.gsralex.rove.core.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.llm.Choice;
import com.gsralex.rove.core.llm.Llm;
import com.gsralex.rove.core.llm.LlmResp;
import com.gsralex.rove.core.loop.Listener;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AgentLoopNodeStreamingTest {

    @Test
    void childLoopInheritsStreamFromContext() {
        RecordingLlm llm = new RecordingLlm();
        List<String> tokens = new ArrayList<>();
        LoopContext ctx = new LoopContext(llm).stream(true).listener(new Listener() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }
        });

        Graph graph = new Graph();
        graph.addNode(AgentLoopNode.builder("agent")
                .inputKey("user")
                .outputKey("reply")
                .build());

        Map<String, Object> out = graph.run(Map.of("user", "hi"), ctx);

        assertEquals("hi there", out.get("reply"));
        assertTrue(llm.streamCalled, "child loop must inherit stream=true from the context");
        assertFalse(llm.chatCalled, "child loop must not fall back to chat()");
        assertEquals("hi there", String.join("", tokens));
    }

    @Test
    void childLoopStaysNonStreamingWhenContextIsNotStreaming() {
        RecordingLlm llm = new RecordingLlm();
        LoopContext ctx = new LoopContext(llm);

        Graph graph = new Graph();
        graph.addNode(AgentLoopNode.builder("agent")
                .inputKey("user")
                .outputKey("reply")
                .build());

        graph.run(Map.of("user", "hi"), ctx);

        assertTrue(llm.chatCalled);
        assertFalse(llm.streamCalled);
    }

    private static final class RecordingLlm implements Llm {

        private static final String TEXT = "hi there";

        boolean chatCalled;
        boolean streamCalled;

        @Override
        public LlmResp chat(List<Message> messages) {
            chatCalled = true;
            return LlmResp.of(new Choice(0, Message.assistant(TEXT), "stop"));
        }

        @Override
        public LlmResp chat(List<Message> messages, List<Tool> tools) {
            return chat(messages);
        }

        @Override
        public LlmResp stream(List<Message> messages, List<Tool> tools, Consumer<String> onToken) {
            streamCalled = true;
            for (String piece : List.of("hi ", "there")) {
                onToken.accept(piece);
            }
            return LlmResp.of(new Choice(0, Message.assistant(TEXT), "stop"));
        }
    }
}
