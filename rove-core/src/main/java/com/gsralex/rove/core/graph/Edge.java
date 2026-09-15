package com.gsralex.rove.core.graph;

import java.util.Map;
import java.util.function.Predicate;

public final class Edge {

    private final Predicate<Map<String, Object>> condition;

    public Edge() {
        this(null);
    }

    private Edge(Predicate<Map<String, Object>> condition) {
        this.condition = condition;
    }

    public static Edge always() {
        return new Edge(null);
    }

    public static Edge when(Predicate<Map<String, Object>> condition) {
        return new Edge(condition);
    }

    public boolean matches(Map<String, Object> state) {
        return condition == null || condition.test(state);
    }
}
