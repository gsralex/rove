package com.gsralex.rove.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsralex.rove.core.tool.ToolCall;
import org.junit.jupiter.api.Test;

class LlmClientParseTest {

    @Test
    void parsesToolCalls() throws Exception {
        String body = """
                {"choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"skill_search","arguments":"{\\"query\\":\\"w\\"}"}}]}}]}
                """;
        LlmClient client = new LlmClient(new LlmConfig("http://localhost", "k", "m"));
        LlmResp resp = client.parse(new ObjectMapper().readTree(body));
        ToolCall call = resp.first().message().toolCalls().getFirst();
        assertEquals("skill_search", call.name());
        assertTrue(call.args().contains("query"));
    }

    @Test
    void emptyChoices() throws Exception {
        LlmClient client = new LlmClient(new LlmConfig("http://localhost", "k", "m"));
        LlmResp resp = client.parse(new ObjectMapper().readTree("{\"choices\":[]}"));
        assertTrue(resp.isEmpty());
    }
}
