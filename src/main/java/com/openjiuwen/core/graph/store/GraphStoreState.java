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
 * 
 * @since 0.1.7
 */
public class GraphStoreState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String ns;
    private final int step;
    private final transient Map<String, Object> channelValues;
    private final List<Message> pendingBuffer;
    private final Map<String, PendingNode> pendingNode;
    private final Map<String, Integer> nodeVersion;

    /**
     * GraphStoreState.
     * 
     * @param ns ns
     * @param step step
     * @param channelValues channelValues
     * @param pendingBuffer pendingBuffer
     * @param pendingNode pendingNode
     * @param nodeVersion nodeVersion
     * @since 0.1.7
     */
    public GraphStoreState(String ns, int step, Map<String, Object> channelValues, List<Message> pendingBuffer,
            Map<String, PendingNode> pendingNode, Map<String, Integer> nodeVersion) {
        this.ns = ns;
        this.step = step;
        this.channelValues = channelValues != null ? channelValues : new HashMap<>();
        this.pendingBuffer = pendingBuffer != null ? pendingBuffer : Collections.emptyList();
        this.pendingNode = pendingNode != null ? pendingNode : new HashMap<>();
        this.nodeVersion = nodeVersion != null ? nodeVersion : new HashMap<>();
    }

    /**
     * getNs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNs() {
        return ns;
    }

    /**
     * getStep.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getStep() {
        return step;
    }

    /**
     * getChannelValues.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getChannelValues() {
        return channelValues;
    }

    /**
     * getPendingBuffer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Message> getPendingBuffer() {
        return pendingBuffer;
    }

    /**
     * getPendingNode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, PendingNode> getPendingNode() {
        return pendingNode;
    }

    /**
     * getNodeVersion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Integer> getNodeVersion() {
        return nodeVersion;
    }

    /**
     * Factory method to create a new GraphStoreState.
     * 
     * @param ns ns
     * @param step step
     * @param channelSnapshot channelSnapshot
     * @param pendingBuffer pendingBuffer
     * @param pendingNode pendingNode
     * @param nodeVersion nodeVersion
     * @return the result
     * @since 0.1.7
     */
    public static GraphStoreState create(String ns, int step, Map<String, Object> channelSnapshot,
            List<Message> pendingBuffer, Map<String, PendingNode> pendingNode, Map<String, Integer> nodeVersion) {
        return new GraphStoreState(ns, step, channelSnapshot, pendingBuffer, pendingNode, nodeVersion);
    }
}
