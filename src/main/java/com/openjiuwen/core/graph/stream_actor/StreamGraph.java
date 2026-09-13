/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages stream consumers for a graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamGraph}.
 * 
 * @since 0.1.7
 */
public class StreamGraph {
    private final Map<String, StreamConsumer> streamNodes = new LinkedHashMap<>();

    /**
     * Register a stream consumer for a node.
     * 
     * @param consumer the stream consumer
     * @param nodeId the node identifier
     * @since 0.1.7
     */
    public void addStreamConsumer(StreamConsumer consumer, String nodeId) {
        streamNodes.putIfAbsent(nodeId, consumer);
    }

    /**
     * Get the stream consumer for a node.
     * 
     * @param nodeId the node identifier
     * @return the stream consumer, or null
     * @since 0.1.7
     */
    public StreamConsumer getNode(String nodeId) {
        return streamNodes.get(nodeId);
    }
}
