package net.minecraft.util;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class Graph {
    private Graph() {
    }

    /**
     * Detects if a cycle is present in the given graph, via a depth first search, and returns {@code true} if a cycle was found.
     *
     * @param nonCyclicalNodes       Nodes that are verified to have no cycles
     *                               involving them.
     * @param pathSet                The current collection of seen nodes. When
     *                               invoked not recursively, this should be an empty
     *                               set.
     * @param onNonCyclicalNodeFound Invoked on each node as we prove that no cycles
     *                               can be reached starting from this node.
     */
    public static <T> boolean depthFirstSearch(Map<T, Set<T>> graph, Set<T> nonCyclicalNodes, Set<T> pathSet, Consumer<T> onNonCyclicalNodeFound, T currentNode) {
        if (nonCyclicalNodes.contains(currentNode)) {
            return false;
        } else if (pathSet.contains(currentNode)) {
            return true;
        } else {
            pathSet.add(currentNode);

            for (T t : graph.getOrDefault(currentNode, ImmutableSet.of())) {
                if (depthFirstSearch(graph, nonCyclicalNodes, pathSet, onNonCyclicalNodeFound, t)) {
                    return true;
                }
            }

            pathSet.remove(currentNode);
            nonCyclicalNodes.add(currentNode);
            onNonCyclicalNodeFound.accept(currentNode);
            return false;
        }
    }
}
