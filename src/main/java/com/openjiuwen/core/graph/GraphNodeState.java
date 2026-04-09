  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph-level state containing the list of source node IDs that have been visited.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.graph_state.GraphState} (the graph-level one).
 * Uses list concatenation semantics (operator.add in Python's Annotated type).
 */
public class GraphNodeState {

    private List<String> sourceNodeId;

    public GraphNodeState() {
        this.sourceNodeId = new ArrayList<>();
    }

    public GraphNodeState(List<String> sourceNodeId) {
        this.sourceNodeId = sourceNodeId != null ? new ArrayList<>(sourceNodeId) : new ArrayList<>();
    }

    public List<String> getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(List<String> sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    /**
     * Merge another state into this one by concatenating source node IDs.
     * This mirrors the Python {@code Annotated[list, operator.add]} semantics.
     *
     * @param other the other state to merge
     */
    public void merge(GraphNodeState other) {
        if (other != null && other.sourceNodeId != null) {
            this.sourceNodeId.addAll(other.sourceNodeId);
        }
    }
}
