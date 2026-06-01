/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Graph execution related event.
 *
 * <p>Mirrors Python's {@code GraphEvent} in
 * {@code openjiuwen.core.common.logging.events}.</p>
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GraphEvent extends BaseLogEvent {
    private String graphId;
    private String nodeId;
    private String nodeName;
    private Object inputs;
    private Object outputs;
    private Object chunk;

    public GraphEvent() {
        super();
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "graph_id", graphId);
        putIfNotNull(map, "node_id", nodeId);
        putIfNotNull(map, "node_name", nodeName);
        putIfNotNull(map, "inputs", inputs);
        putIfNotNull(map, "outputs", outputs);
        putIfNotNull(map, "chunk", chunk);
    }
}
