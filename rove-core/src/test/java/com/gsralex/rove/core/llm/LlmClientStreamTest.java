package com.gsralex.rove.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.tool.ToolCall;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class LlmClientStreamTest {

    private final LlmClient client = new LlmClient(new LlmConfig("http://localhost", "k", "m"));

    @Test
    void accumulatesContentAndToolCallFragments() throws Exception {
        String sse = sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"let me \"},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"search\"},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"skill_search\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"query\\\":\"}}]},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"w\\\"}\"}}]},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");

        StringBuilder streamed = new StringBuilder();
        LlmResp resp = read(sse, streamed::append);

        assertEquals("let me search", streamed.toString());
        assertEquals("tool_calls", resp.first().finishReason());
        ToolCall call = resp.first().message().toolCalls().getFirst();
        assertEquals("call_1", call.id());
        assertEquals("function", call.type());
        assertEquals("skill_search", call.name());
        assertEquals("{\"query\":\"w\"}", call.args());
    }

    @Test
    void keepsParallelToolCallsSeparate() throws Exception {
        String sse = sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"function\":{\"name\":\"read_\"}},{\"index\":1,\"id\":\"b\",\"function\":{\"name\":\"list_\"}}]},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"p\\\":1}\"}},{\"index\":1,\"function\":{\"arguments\":\"{}\"}}]},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");

        LlmResp resp = read(sse, null);

        assertEquals(2, resp.first().message().toolCalls().size());
        assertEquals("{\"p\":1}", resp.first().message().toolCalls().get(0).args());
        assertEquals("{}", resp.first().message().toolCalls().get(1).args());
    }

    @Test
    void infersFinishReasonAndLeavesContentNullForToolOnlyStream() throws Exception {
        String sse = sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"f\",\"arguments\":\"{}\"}}]},\"finish_reason\":null}]}");

        LlmResp resp = read(sse, null);

        assertNull(resp.first().message().content());
        assertEquals("tool_calls", resp.first().finishReason());
    }

    @Test
    void plainTextStreamFinishesWithStop() throws Exception {
        String sse = sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hi\"},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");

        LlmResp resp = read(sse, null);

        assertEquals("hi", resp.first().message().content());
        assertEquals("stop", resp.first().finishReason());
        assertTrue(resp.first().message().toolCalls().isEmpty());
    }

    @Test
    void ignoresKeepAliveAndUsageOnlyChunks() throws Exception {
        String sse = ": keep-alive\n\n"
                + "data:\n\n"
                + "data: \n\n"
                + "data: {\"object\":\"chat.completion.chunk\",\"choices\":[],\"usage\":{\"total_tokens\":5}}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";

        LlmResp resp = read(sse, null);

        assertEquals("ok", resp.first().message().content());
    }

    @Test
    void surfacesStreamErrorChunk() {
        String sse = "data: {\"error\":{\"message\":\"rate limit exceeded\",\"type\":\"rate_limit\"}}\n\n";

        RuntimeException e = assertThrows(RuntimeException.class, () -> read(sse, null));
        assertTrue(e.getMessage().contains("rate limit exceeded"), e.getMessage());
    }

    @Test
    void keepsFragmentedArgumentsUntouchedInsteadOfParsingThem() throws Exception {
        // 每个片段单独看都不是合法 JSON；流在参数中途结束（没有 finish_reason、没有 [DONE]）。
        String sse = "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c9\","
                + "\"function\":{\"name\":\"bash\",\"arguments\":\"{\\\"command\\\": \"}}]},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"\\\"ls -la\\\"}\"}}]},\"finish_reason\":null}]}\n\n";

        LlmResp resp = read(sse, null);

        // 未解析、未报错，只是把原始碎片原样攒起来交给调用方。
        ToolCall call = resp.first().message().toolCalls().getFirst();
        assertEquals("bash", call.name());
        assertEquals("{\"command\": \"ls -la\"}", call.args());
        assertEquals("tool_calls", resp.first().finishReason());
    }

    @Test
    void bareToolCallWithoutArgumentsFallsBackToEmptyObject() throws Exception {
        String sse = sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"list\"}}]},\"finish_reason\":null}]}");

        LlmResp resp = read(sse, null);

        assertEquals("{}", resp.first().message().toolCalls().getFirst().args());
    }

    @Test
    void interfaceDefaultStubDegradesToChatAndEmitsWholeText() {
        Llm stub = new Llm() {
            @Override
            public LlmResp chat(java.util.List<com.gsralex.rove.core.common.Message> messages) {
                return LlmResp.of(new Choice(0, Message.assistant("whole"), "stop"));
            }

            @Override
            public LlmResp chat(
                    java.util.List<com.gsralex.rove.core.common.Message> messages,
                    java.util.List<com.gsralex.rove.core.tool.Tool> tools) {
                return chat(messages);
            }
        };
        List<String> tokens = new ArrayList<>();

        LlmResp resp = stub.stream(java.util.List.of(), null, tokens::add);

        assertEquals("whole", resp.first().message().content());
        assertEquals(List.of("whole"), tokens, "non-streaming Llm must still honour onToken");
    }

    private LlmResp read(String sse, Consumer<String> onToken) throws Exception {
        return client.readStream(new BufferedReader(new StringReader(sse)), onToken);
    }

    private static String sse(String... payloads) {
        StringBuilder sb = new StringBuilder();
        for (String payload : payloads) {
            sb.append("data: ").append(payload).append('\n').append('\n');
        }
        return sb.append("data: [DONE]\n\n").toString();
    }
}
