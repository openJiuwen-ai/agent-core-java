/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph state container for tracking source node IDs.
 * <p>
 * Mirrors Python's {@code GraphState} TypedDict from
 * <code>graph/graph_state.py</code>.
 */
public class GraphState {

    private List<String> sourceNodeIds = new ArrayList<>();

    public List<String> getSourceNodeIds() {
        return sourceNodeIds;
    }

    public void addSourceNodeId(String nodeId) {
        sourceNodeIds.add(nodeId);
    }

    public void setSourceNodeIds(List<String> sourceNodeIds) {
        this.sourceNodeIds = sourceNodeIds != null ? sourceNodeIds : new ArrayList<>();
    }
}
