/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.workflow.component.NodeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete specification of a workflow structure.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.workflow_config.WorkflowSpec}.
 */
public class WorkflowSpec {

    private Map<String, List<String>> edges = new HashMap<>();
    private Map<String, List<String>> streamEdges = new HashMap<>();
    private Map<String, NodeConfig> compConfigs = new HashMap<>();
    private List<String> startNodes = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, List<String>> getEdges() {
        return edges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEdges(Map<String, List<String>> edges) {
        this.edges = edges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, List<String>> getStreamEdges() {
        return streamEdges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStreamEdges(Map<String, List<String>> streamEdges) {
        this.streamEdges = streamEdges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, NodeConfig> getCompConfigs() {
        return compConfigs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCompConfigs(Map<String, NodeConfig> compConfigs) {
        this.compConfigs = compConfigs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getStartNodes() {
        return startNodes;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStartNodes(List<String> startNodes) {
        this.startNodes = startNodes;
    }
}
