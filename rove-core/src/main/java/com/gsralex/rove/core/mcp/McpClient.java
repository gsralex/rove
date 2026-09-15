package com.gsralex.rove.core.mcp;

import com.gsralex.rove.core.tool.Tool;
import java.util.List;
import java.util.Map;

public interface McpClient extends AutoCloseable {

    List<Tool> listTools();

    String callTool(String name, Map<String, Object> args);

    @Override
    void close();
}
