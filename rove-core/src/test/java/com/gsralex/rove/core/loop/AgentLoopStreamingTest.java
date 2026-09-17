package com.gsralex.rove.core.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.llm.Choice;
import com.gsralex.rove.core.llm.Llm;
import com.gsralex.rove.core.llm.LlmResp;
import com.gsralex.rove.core.tool.Tool;
import com.gsralex.rove.core.tool.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AgentLoopStreamingTest {

    @Test
    void streamsTokensAndInvokesToolOnlyOnceArgsAreComplete() {
        RecordingLlm llm = new RecordingLlm();
        RecordingTool tool = new RecordingTool();
        List<String> tokens = new ArrayList<>();

        AgentLoop loop = new AgentLoop(llm, 4).stream(true).listener(new Listener() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }
        });

        String reply = loop.run(new ArrayList<>(List.of(Message.user("hi"))), List.of(tool));

        assertEquals("done", reply);
        assertTrue(llm.streamCalled, "streaming must be used when enabled");
        assertFalse(llm.chatCalled, "chat must not be used when streaming is enabled");
        assertEquals("workingdone", String.join("", tokens));
        assertEquals(1, tool.invocations);
        assertEquals("hi", tool.lastText);
    }

    @Test
    void fallsBackToChatWhenStreamingDisabled() {
        RecordingLlm llm = new RecordingLlm();
        RecordingTool tool = new RecordingTool();

        AgentLoop loop = new AgentLoop(llm, 4);
        String reply = loop.run(new ArrayList<>(List.of(Message.user("hi"))), List.of(tool));

        assertEquals("done", reply);
        assertTrue(llm.chatCalled);
        assertFalse(llm.streamCalled);
    }

    @Test
    void invalidArgumentJsonBecomesToolResultNotAnException() {
        Map<String, Object> broken = AgentLoop.parseArgs("{\"command\": ");
        assertTrue(broken.containsKey("_parse_error"), "fragmented json must be reported, not thrown");
    }

    private static final class RecordingLlm implements Llm {

        boolean chatCalled;
        boolean streamCalled;
        private int calls;

        @Override
        public LlmResp chat(List<Message> messages) {
            chatCalled = true;
            return next();
        }

        @Override
        public LlmResp chat(List<Message> messages, List<Tool> tools) {
            chatCalled = true;
            return next();
        }

        @Override
        public LlmResp stream(List<Message> messages, List<Tool> tools, Consumer<String> onToken) {
            streamCalled = true;
            LlmResp resp = next();
            if (onToken != null) {
                String text = resp.first().message().content();
                for (String piece : pieces(text)) {
                    onToken.accept(piece);
                }
            }
            return resp;
        }

        private static List<String> pieces(String text) {
            if (text == null || text.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (int i = 0; i < text.length(); i += 2) {
                out.add(text.substring(i, Math.min(text.length(), i + 2)));
            }
            return out;
        }

        private LlmResp next() {
            calls++;
            if (calls == 1) {
                ToolCall call = new ToolCall("c1", "function", "echo", "{\"text\":\"hi\"}");
                return LlmResp.of(new Choice(0, Message.assistant("working", List.of(call)), "tool_calls"));
            }
            return LlmResp.of(new Choice(0, Message.assistant("done"), "stop"));
        }
    }

    private static final class RecordingTool implements Tool {

        int invocations;
        String lastText;

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "echo back";
        }

        @Override
        public String inputSchema() {
            return "{\"type\":\"object\"}";
        }

        @Override
        public String call(Map<String, Object> args) {
            invocations++;
            lastText = args == null ? null : String.valueOf(args.get("text"));
            return "echo:" + lastText;
        }
    }
}
