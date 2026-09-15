package com.gsralex.rove.core.tool;

public record ToolResult(boolean success, String text) {

    public static ToolResult ok(String text) {
        return new ToolResult(true, text);
    }

    public static ToolResult fail(String text) {
        return new ToolResult(false, text);
    }
}
