package com.gsralex.rove.core.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public void register(Tool tool) {
        byName.put(tool.name(), tool);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<Tool> list() {
        return List.copyOf(byName.values());
    }
}
