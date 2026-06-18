/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code StreamGraph} in
 * {@code openjiuwen/core/graph/stream_actor/base.py}.
 */
public class StreamGraph {

    private final Map<String, StreamConsumer> streamNodes = new LinkedHashMap<>();

    /**
     * Adds a stream consumer only when the node id is not already present.
     *
     * @param consumer stream consumer
     * @param nodeId graph node id
     */
    public void addStreamConsumer(StreamConsumer consumer, String nodeId) {
        streamNodes.putIfAbsent(nodeId, consumer);
    }

    /**
     * Looks up a stream consumer by graph node id.
     *
     * @param nodeId graph node id
     * @return registered consumer or {@code null}
     */
    public StreamConsumer getNode(String nodeId) {
        return streamNodes.get(nodeId);
    }
}
