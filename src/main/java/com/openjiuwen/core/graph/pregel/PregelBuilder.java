/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.graph.store.Store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builder for constructing a {@link Pregel} graph engine.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.builder.PregelBuilder}.
 */
public class PregelBuilder {

    private final Map<String, PregelNode> nodes = new LinkedHashMap<>();
    private final List<Channel> channels = new ArrayList<>();

    public PregelBuilder() {
        addNode(PregelConstants.START, (Runnable) () -> { }, new ArrayList<>());
        addNode(PregelConstants.END, (Runnable) () -> { }, new ArrayList<>());
    }

    /**
     * Add a node to the graph.
     *
     * @param name    node name
     * @param fn      node execution function
     * @param routers list of routers for the node
     * @return this builder
     */
    public PregelBuilder addNode(String name, Object fn, List<IRouter> routers) {
        if (routers == null) {
            routers = new ArrayList<>();
        }
        nodes.put(name, new PregelNode(name, fn, routers));
        channels.add(new TriggerChannel(name));
        return this;
    }

    /**
     * Add a node with no initial routers.
     *
     * @param name node name
     * @param fn   node execution function
     * @return this builder
     */
    public PregelBuilder addNode(String name, Object fn) {
        return addNode(name, fn, new ArrayList<>());
    }

    /**
     * Add an edge between nodes.
     * <p>
     * Supported patterns:
     * <ul>
     *   <li>N to 1 (barrier): multiple start nodes converge to a single end node</li>
     *   <li>1 to N (multi-static): single start fans out to multiple end nodes</li>
     *   <li>1 to 1 (static): single start to single end</li>
     * </ul>
     *
     * @param start single node name or collection of node names
     * @param end   single node name or collection of node names
     * @return this builder
     */
    public PregelBuilder addEdge(Object start, Object end) {
        if (start instanceof Collection && end instanceof String) {
            // barrier: N → 1
            Collection<String> startNodes = toStringCollection(start);
            String endNode = (String) end;
            Set<String> expected = Set.copyOf(startNodes);
            BarrierChannel barrier = new BarrierChannel(endNode, expected);
            channels.add(barrier);
            for (String s : startNodes) {
                nodes.get(s).getRouters().add(new BarrierRouter(List.of(barrier.getKey())));
            }
        } else if (start instanceof String && end instanceof Collection) {
            // multi-static: 1 → N
            String startNode = (String) start;
            Collection<String> endNodes = toStringCollection(end);
            nodes.get(startNode).getRouters().add(new StaticRouter(new ArrayList<>(endNodes)));
        } else if (start instanceof String && end instanceof String) {
            // single-static: 1 → 1
            String startNode = (String) start;
            String endNode = (String) end;
            nodes.get(startNode).getRouters().add(new StaticRouter(List.of(endNode)));
        } else {
            throw new IllegalArgumentException(
                    String.format("Unsupported edge format: %s -> %s", start, end));
        }
        return this;
    }

    /**
     * Add a conditional branch from a source node using a selector function.
     *
     * @param src      source node name
     * @param selector function that determines which node to route to
     * @return this builder
     */
    public PregelBuilder addBranch(String src, java.util.function.Function<Object, Object> selector) {
        nodes.get(src).getRouters().add(new ConditionalRouter(selector));
        return this;
    }

    /**
     * Build the Pregel engine.
     *
     * @param store             optional state store
     * @param afterStepCallback optional callback invoked after each super-step
     * @return a configured Pregel instance
     */
    public Pregel build(Store store, Consumer<PregelLoop> afterStepCallback) {
        return new Pregel(nodes, channels, store, afterStepCallback);
    }

    /**
     * Build the Pregel engine with no store or callback.
     *
     * @return a configured Pregel instance
     */
    public Pregel build() {
        return build(null, null);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> toStringCollection(Object obj) {
        if (obj instanceof Collection) {
            return (Collection<String>) obj;
        }
        throw new IllegalArgumentException("Expected Collection of strings, got: " + obj.getClass());
    }
}
