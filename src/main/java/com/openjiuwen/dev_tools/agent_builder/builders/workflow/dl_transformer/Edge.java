/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

/**
 * Workflow edge model.
 * <p>
 * Mirrors Python's {@code Edge} dataclass.
 */
public class Edge {
    private final String sourceNodeId;
    private final String targetNodeId;
    private final String sourcePortId;

    public Edge(String sourceNodeId, String targetNodeId) {
        this(sourceNodeId, targetNodeId, null);
    }

    public Edge(String sourceNodeId, String targetNodeId, String sourcePortId) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.sourcePortId = sourcePortId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getSourcePortId() {
        return sourcePortId;
    }
}
