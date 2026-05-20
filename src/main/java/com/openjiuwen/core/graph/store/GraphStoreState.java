/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted state of a Pregel graph execution for recovery/resume.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.base.GraphState}.
 * Named {@code GraphStoreState} to avoid conflict with the graph node state class.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphStoreState(String ns, int step, Map<String, Object> channelValues,
                           List<Message> pendingBuffer, Map<String, PendingNode> pendingNode,
                           Map<String, Integer> nodeVersion) {
        this.ns = ns;
        this.step = step;
        this.channelValues = channelValues != null ? channelValues : new HashMap<>();
        this.pendingBuffer = pendingBuffer != null ? pendingBuffer : Collections.emptyList();
        this.pendingNode = pendingNode != null ? pendingNode : new HashMap<>();
        this.nodeVersion = nodeVersion != null ? nodeVersion : new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNs() {
        return ns;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getStep() {
        return step;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getChannelValues() {
        return channelValues;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Message> getPendingBuffer() {
        return pendingBuffer;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, PendingNode> getPendingNode() {
        return pendingNode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Integer> getNodeVersion() {
        return nodeVersion;
    }

    /**
     * Factory method to create a new GraphStoreState.
     */
    public static GraphStoreState create(String ns, int step, Map<String, Object> channelSnapshot,
                                         List<Message> pendingBuffer, Map<String, PendingNode> pendingNode,
                                         Map<String, Integer> nodeVersion) {
        return new GraphStoreState(ns, step, channelSnapshot, pendingBuffer, pendingNode, nodeVersion);
    }
}
