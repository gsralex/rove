package com.gsralex.rove.core.tool;

public record ToolCall(String id, String type, String name, String args) {

    public ToolCall(String id, String name, String args) {
        this(id, "function", name, args);
    }
}
