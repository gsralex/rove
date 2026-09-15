package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.tool.ToolCall;

public interface Listener {

    default void onToken(String token) {}

    default void onStatus(String status) {}

    default void onNodeStart(String nodeName) {}

    default void onNodeEnd(String nodeName) {}

    default void onToolCall(ToolCall call) {}

    default void onToolResult(String toolName, String result) {}

    default void onError(Throwable error) {}
}
