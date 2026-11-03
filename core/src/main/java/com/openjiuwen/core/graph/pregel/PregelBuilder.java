/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.graph.store.Store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builder for Pregel graphs.
 *
 * <p>Mirrors Python's {@code PregelBuilder} in
 * {@code openjiuwen/core/graph/pregel/builder.py}.</p>
 */
public class PregelBuilder {

    private final Map<String, PregelNode> nodes = new LinkedHashMap<>();
    private final List<Channel> channels = new ArrayList<>();

    public PregelBuilder() {
        addNode(PregelConstants.START, ignored -> Boolean.TRUE, List.of());
        addNode(PregelConstants.END, ignored -> Boolean.TRUE, List.of());
    }

    public PregelBuilder addNode(String name, Runnable runnable) {
        return addNode(name, ignored -> {
            runnable.run();
            return Boolean.TRUE;
        }, List.of());
    }

    public PregelBuilder addNode(String name, Function<Object, Object> function) {
        return addNode(name, function, List.of());
    }

    public PregelBuilder addNode(String name, Function<Object, Object> function, List<IRouter> routers) {
        List<IRouter> normalizedRouters = routers != null ? routers : List.of();
        nodes.put(name, new PregelNode(name, function, normalizedRouters));
        channels.add(new TriggerChannel(name));
        return this;
    }

    public PregelBuilder addEdge(String start, String end) {
        requireNode(start).getRouters().add(new StaticRouter(List.of(end)));
        return this;
    }

    public PregelBuilder addEdge(String start, Collection<String> ends) {
        requireNode(start).getRouters().add(new StaticRouter(new ArrayList<>(ends)));
        return this;
    }

    /**
     * Add an N-to-one barrier edge.
     *
     * <p>Each source item is either a single sender string or a collection of
     * OR-group senders, matching Python's CNF barrier shape.</p>
     *
     * @param starts sender strings or sender groups
     * @param end target node
     * @return this builder
     */
    public PregelBuilder addEdge(Collection<?> starts, String end) {
        List<Set<String>> expectedGroups = new ArrayList<>();
        for (Object item : starts) {
            expectedGroups.add(normalizeBarrierGroup(item));
        }

        BarrierChannel barrier = new BarrierChannel(end, expectedGroups);
        channels.add(barrier);
        for (Object item : starts) {
            for (String sender : normalizeBarrierGroup(item)) {
                requireNode(sender).getRouters().add(new BarrierRouter(List.of(barrier.getKey())));
            }
        }
        return this;
    }

    public PregelBuilder addBranch(String source, Supplier<?> selector) {
        requireNode(source).getRouters().add(new ConditionalRouter(selector));
        return this;
    }

    public PregelBuilder addBranchWithState(String source, Function<Object, ?> selector) {
        requireNode(source).getRouters().add(new ConditionalRouter(selector));
        return this;
    }

    public Pregel build() {
        return build(null, null);
    }

    public Pregel build(Store store) {
        return build(store, null);
    }

    public Pregel build(Store store, Consumer<PregelLoop> afterStepCallback) {
        return new Pregel(nodes, channels, PregelConstants.START, store, afterStepCallback);
    }

    public Map<String, PregelNode> getNodes() {
        return new LinkedHashMap<>(nodes);
    }

    public List<Channel> getChannels() {
        return new ArrayList<>(channels);
    }

    private PregelNode requireNode(String name) {
        PregelNode node = nodes.get(name);
        if (node == null) {
            throw new IllegalArgumentException("Unknown Pregel node: " + name);
        }
        return node;
    }

    private static Set<String> normalizeBarrierGroup(Object item) {
        if (item instanceof String sender) {
            return Set.of(sender);
        }
        if (item instanceof Collection<?> collection) {
            Set<String> group = new LinkedHashSet<>();
            for (Object sender : collection) {
                if (!(sender instanceof String senderName)) {
                    throw new IllegalArgumentException(
                            "Unsupported barrier source item type: " + sender.getClass().getName());
                }
                group.add(senderName);
            }
            return group;
        }
        throw new IllegalArgumentException(
                "Unsupported barrier source item type: " + item.getClass().getName());
    }
}
