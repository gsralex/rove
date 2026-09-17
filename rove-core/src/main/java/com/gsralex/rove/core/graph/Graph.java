package com.gsralex.rove.core.graph;

import com.google.common.base.Preconditions;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.loop.LoopManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;

public final class Graph {

    private final DefaultDirectedGraph<Node, Edge> graph = new DefaultDirectedGraph<>(Edge.class);
    private final List<Node> order = new ArrayList<>();

    public boolean addNode(Node node) {
        boolean added = graph.addVertex(node);
        if (added) {
            order.add(node);
        }
        return added;
    }

    public boolean addEdge(Node from, Node to) {
        return addEdge(from, to, Edge.always());
    }

    public boolean addEdge(Node from, Node to, Edge edge) {
        addNode(from);
        addNode(to);
        return graph.addEdge(from, to, edge);
    }

    public Map<String, Object> run(Map<String, Object> input, LoopContext ctx) {
        Preconditions.checkState(!new CycleDetector<>(graph).detectCycles(), "graph contains a cycle");

        Map<String, Object> state = Collections.synchronizedMap(new HashMap<>());
        if (input != null) {
            state.putAll(input);
        }

        List<Node> sources = sources();
        if (sources.isEmpty()) {
            return state;
        }

        Set<Node> reachable = ConcurrentHashMap.newKeySet();
        Set<Node> completed = ConcurrentHashMap.newKeySet();
        reachable.addAll(sources);

        LoopManager loopManager = ctx.loopManager();

        while (true) {
            List<Node> ready = readyNodes(reachable, completed);
            if (ready.isEmpty()) {
                for (Node n : reachable) {
                    Preconditions.checkState(
                            completed.contains(n), "graph stuck: node %s is reachable but not runnable", n.name());
                }
                break;
            }

            if (ready.size() == 1) {
                runNode(ready.getFirst(), state, ctx, reachable, completed);
                continue;
            }

            List<Future<?>> futures = new ArrayList<>(ready.size());
            for (Node n : ready) {
                futures.add(loopManager.submit(() -> {
                    runNode(n, state, ctx, reachable, completed);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                await(future);
            }
        }

        return state;
    }

    private List<Node> readyNodes(Set<Node> reachable, Set<Node> completed) {
        List<Node> ready = new ArrayList<>();
        for (Node n : reachable) {
            if (completed.contains(n)) {
                continue;
            }
            if (allReachablePredsCompleted(n, reachable, completed)) {
                ready.add(n);
            }
        }
        return ready;
    }

    private boolean allReachablePredsCompleted(Node n, Set<Node> reachable, Set<Node> completed) {
        for (Edge in : graph.incomingEdgesOf(n)) {
            Node pred = graph.getEdgeSource(in);
            if (reachable.contains(pred) && !completed.contains(pred)) {
                return false;
            }
        }
        return true;
    }

    private void runNode(
            Node node, Map<String, Object> state, LoopContext ctx, Set<Node> reachable, Set<Node> completed) {
        ctx.nodeStart(node.name());
        node.execute(state, ctx);
        ctx.nodeEnd(node.name());

        Set<Edge> out = graph.outgoingEdgesOf(node);
        if (!out.isEmpty()) {
            List<Node> activated = new ArrayList<>();
            for (Edge e : out) {
                if (e.matches(state)) {
                    activated.add(graph.getEdgeTarget(e));
                }
            }
            Preconditions.checkState(
                    !activated.isEmpty(), "outgoing edges exist but none match after node %s", node.name());
            reachable.addAll(activated);
        }
        completed.add(node);
    }

    private static void await(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("graph parallel wait interrupted", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(c);
        }
    }

    private List<Node> sources() {
        List<Node> sources = new ArrayList<>();
        for (Node n : order) {
            if (graph.inDegreeOf(n) == 0) {
                sources.add(n);
            }
        }
        return sources;
    }
}
