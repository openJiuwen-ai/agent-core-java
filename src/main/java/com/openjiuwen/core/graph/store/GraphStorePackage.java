/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for graph-store exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/graph/store/__init__.py}.
 * </p>
 */
public final class GraphStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/graph/store/__init__.py";
    public static final Class<PendingNode> PENDING_NODE = PendingNode.class;
    public static final Class<GraphStoreState> GRAPH_STATE = GraphStoreState.class;
    public static final Class<Serializer> SERIALIZER = Serializer.class;
    public static final Class<Store> STORE = Store.class;
    public static final Class<GraphStore> GRAPH_STORE = GraphStore.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "create_state",
            "PendingNode",
            "GraphState",
            "Serializer",
            "create_serializer",
            "Store",
            "GraphStore"
    );

    private GraphStorePackage() {
    }

    public static GraphStoreState createState(String ns,
                                              int step,
                                              Map<String, Object> channelSnapshot,
                                              List<Message> pendingBuffer,
                                              Map<String, PendingNode> pendingNode,
                                              Map<String, Integer> nodeVersion) {
        return GraphStoreState.create(
                ns,
                step,
                channelSnapshot,
                pendingBuffer,
                pendingNode,
                nodeVersion
        );
    }

    public static Serializer createSerializer(String typeName) {
        return Serializer.create(typeName);
    }
}
