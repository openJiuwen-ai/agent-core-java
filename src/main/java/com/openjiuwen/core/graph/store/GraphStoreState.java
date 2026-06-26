/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted graph execution state for recovery and resume.
 * <p>
 * Mirrors Python's {@code GraphState} in
 * {@code openjiuwen/core/graph/store/base.py}.
 * Named {@code GraphStoreState} to avoid conflicting with
 * {@code com.openjiuwen.core.graph.GraphState}.
 */
public class GraphStoreState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String ns;
    private final int step;
    private final Map<String, Object> channelValues;
    private final List<Message> pendingBuffer;
    private final Map<String, PendingNode> pendingNode;
    private final Map<String, Integer> nodeVersion;

    public GraphStoreState(
            String ns,
            int step,
            Map<String, Object> channelValues,
            List<Message> pendingBuffer,
            Map<String, PendingNode> pendingNode,
            Map<String, Integer> nodeVersion
    ) {
        this.ns = ns;
        this.step = step;
        this.channelValues = channelValues != null ? new LinkedHashMap<>(channelValues) : new LinkedHashMap<>();
        this.pendingBuffer = pendingBuffer != null ? new ArrayList<>(pendingBuffer) : new ArrayList<>();
        this.pendingNode = pendingNode != null ? new LinkedHashMap<>(pendingNode) : new LinkedHashMap<>();
        this.nodeVersion = nodeVersion != null ? new LinkedHashMap<>(nodeVersion) : new LinkedHashMap<>();
    }

    public static GraphStoreState create(
            String ns,
            int step,
            Map<String, Object> channelSnapshot,
            List<Message> pendingBuffer,
            Map<String, PendingNode> pendingNode,
            Map<String, Integer> nodeVersion
    ) {
        return new GraphStoreState(ns, step, channelSnapshot, pendingBuffer, pendingNode, nodeVersion);
    }

    public String getNs() {
        return ns;
    }

    public int getStep() {
        return step;
    }

    public Map<String, Object> getChannelValues() {
        return new LinkedHashMap<>(channelValues);
    }

    public List<Message> getPendingBuffer() {
        return new ArrayList<>(pendingBuffer);
    }

    public Map<String, PendingNode> getPendingNode() {
        return new LinkedHashMap<>(pendingNode);
    }

    public Map<String, Integer> getNodeVersion() {
        return new LinkedHashMap<>(nodeVersion);
    }
}
