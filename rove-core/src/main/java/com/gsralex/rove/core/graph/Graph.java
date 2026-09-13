package com.gsralex.rove.core.graph;

import org.jgrapht.graph.DefaultDirectedGraph;

public final class Graph {

    private final DefaultDirectedGraph<Node, Edge> graph = new DefaultDirectedGraph<>(Edge.class);

    public boolean addNode(Node node) {
        return graph.addVertex(node);
    }

    public boolean addEdge(Node from, Node to) {
        addNode(from);
        addNode(to);
        return graph.addEdge(from, to) != null;
    }

    public boolean addEdge(Node from, Node to, Edge edge) {
        addNode(from);
        addNode(to);
        return graph.addEdge(from, to, edge);
    }
}
