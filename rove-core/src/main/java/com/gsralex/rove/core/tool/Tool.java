package com.gsralex.rove.core.tool;

import java.util.Map;

/**
 * Function/MCP tool: {@code name}, {@code description}, {@code inputSchema}; invoke via {@code call}.
 */
public interface Tool {

    String name();

    String description();

    String inputSchema();

    String call(Map<String, Object> args);
}
