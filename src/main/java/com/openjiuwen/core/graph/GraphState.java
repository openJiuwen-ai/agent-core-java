/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code GraphState} in
 * {@code openjiuwen/core/graph/graph_state.py}.
 */
public class GraphState {

    @JsonProperty("source_node_id")
    private List<String> sourceNodeId = new ArrayList<>();

    public List<String> getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(List<String> sourceNodeId) {
        this.sourceNodeId = sourceNodeId == null ? new ArrayList<>() : new ArrayList<>(sourceNodeId);
    }

    public void mergeSourceNodeId(List<String> other) {
        if (other != null) {
            sourceNodeId.addAll(other);
        }
    }
}
