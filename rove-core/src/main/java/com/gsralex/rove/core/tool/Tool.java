package com.gsralex.rove.core.tool;

import java.util.Map;

public interface Tool {

    String name();

    String description();

    String inputSchema();

    String call(Map<String, Object> args);
}
