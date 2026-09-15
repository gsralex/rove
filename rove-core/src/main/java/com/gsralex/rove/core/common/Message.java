package com.gsralex.rove.core.common;

import com.gsralex.rove.core.tool.ToolCall;
import java.util.List;

public record Message(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public Message {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content, null, List.of());
    }

    public static Message user(String content) {
        return new Message(Role.USER, content, null, List.of());
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content, null, List.of());
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, content, null, toolCalls);
    }

    public static Message tool(String content) {
        return new Message(Role.TOOL, content, null, List.of());
    }

    public static Message tool(String toolCallId, String content) {
        return new Message(Role.TOOL, content, toolCallId, List.of());
    }
}
