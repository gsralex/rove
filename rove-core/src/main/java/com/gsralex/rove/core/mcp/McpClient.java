package com.gsralex.rove.core.mcp;

import com.gsralex.rove.core.tool.Tool;
import java.util.List;
import java.util.Map;

/** MCP session: {@code tools/list} and {@code tools/call}. */
public interface McpClient extends AutoCloseable {

    List<Tool> listTools();

    String callTool(String name, Map<String, Object> arguments);

    @Override
    void close();
}
