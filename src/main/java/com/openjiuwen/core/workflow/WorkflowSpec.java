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
 * 
 * @since 0.1.7
 */
public class WorkflowSpec {
    private Map<String, List<String>> edges = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, List<String>> streamEdges = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, NodeConfig> compConfigs = new HashMap<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> startNodes = new ArrayList<>();

    /**
     * getEdges.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getEdges() {
        return edges;
    }

    /**
     * setEdges.
     * 
     * @param edges edges
     * @since 0.1.7
     */
    public void setEdges(Map<String, List<String>> edges) {
        this.edges = edges;
    }

    /**
     * getStreamEdges.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getStreamEdges() {
        return streamEdges;
    }

    /**
     * setStreamEdges.
     * 
     * @param streamEdges streamEdges
     * @since 0.1.7
     */
    public void setStreamEdges(Map<String, List<String>> streamEdges) {
        this.streamEdges = streamEdges;
    }

    /**
     * getCompConfigs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, NodeConfig> getCompConfigs() {
        return compConfigs;
    }

    /**
     * setCompConfigs.
     * 
     * @param compConfigs compConfigs
     * @since 0.1.7
     */
    public void setCompConfigs(Map<String, NodeConfig> compConfigs) {
        this.compConfigs = compConfigs;
    }

    /**
     * getStartNodes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getStartNodes() {
        return startNodes;
    }

    /**
     * setStartNodes.
     * 
     * @param startNodes startNodes
     * @since 0.1.7
     */
    public void setStartNodes(List<String> startNodes) {
        this.startNodes = startNodes;
    }
}
