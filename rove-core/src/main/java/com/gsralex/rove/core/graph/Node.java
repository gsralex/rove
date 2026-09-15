package com.gsralex.rove.core.graph;

import com.gsralex.rove.core.loop.LoopContext;
import java.util.Map;

public interface Node {

    String name();

    default void execute(Map<String, Object> state, LoopContext ctx) {
        throw new UnsupportedOperationException("node is not executable: " + name());
    }
}
