/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base converter defining interface and common logic for DL to DSL transformation.
 * <p>
 * Subclasses must implement {@code convertSpecificConfig()} method.
 * <p>
 * Mirrors Python's {@code BaseConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters.base}.
 */
public abstract class BaseConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseConverter.class);

    protected Map<String, Object> nodeData;
    protected Map<String, Object> nodesDict;
    protected Map<String, Object> resource;
    protected Position position;
    protected Node node;
    protected List<Edge> edges = new ArrayList<>();
    protected int variableIndex = 0;

    public BaseConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource,
            Position position) {
        this.nodeData = nodeData;
        this.nodesDict = nodesDict;
        this.resource = resource;
        this.position = position != null ? position : new Position(0, 0);

        String nodeTypeStr = (String) nodeData.get("type");
        NodeType nodeType = NodeType.fromDlType(nodeTypeStr);
        this.node = new Node(
                (String) nodeData.get("id"),
                nodeType.getDslType()
        );
    }

    /**
     * Convert specific configuration. Subclasses must implement this method.
     */
    protected abstract void convertSpecificConfig();

    /**
     * Execute conversion.
     * <p>
     * Steps:
     * 1. Convert common configuration
     * 2. Convert specific configuration
     * 3. Convert edges
     */
    public void convert() {
        convertCommonConfig();
        convertSpecificConfig();
        convertEdges();
    }

    /**
     * Convert common configuration.
     */
    protected void convertCommonConfig() {
        this.node.setId(this.nodeData.get("id").toString());
        this.node.setPosition(this.position);
    }

    /**
     * Convert edges.
     */
    protected void convertEdges() {
        List<Map<String, Object>> edgeList = (List<Map<String, Object>>) nodeData.get("edges");
        if (edgeList == null || edgeList.isEmpty()) {
            return;
        }

        for (Map<String, Object> edgeData : edgeList) {
            Edge edge = convertEdge(edgeData);
            if (edge != null) {
                this.edges.add(edge);
            }
        }
    }

    /**
     * Convert a single edge.
     */
    protected Edge convertEdge(Map<String, Object> edgeData) {
        String source = (String) edgeData.get("source");
        String target = (String) edgeData.get("target");
        String sourceHandle = (String) edgeData.get("sourceHandle");
        String targetHandle = (String) edgeData.get("targetHandle");

        return new Edge(source, target, sourceHandle, targetHandle);
    }

    /**
     * Get the converted node.
     */
    public Node getNode() {
        return this.node;
    }

    /**
     * Get the converted edges.
     */
    public List<Edge> getEdges() {
        return this.edges;
    }

    /**
     * Generate a unique variable name.
     */
    protected String generateVariableName(String baseName) {
        return baseName + "_" + (++variableIndex);
    }

    /**
     * Position data class.
     */
    public static class Position {
        private final double x;
        private final double y;

        public Position(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() { return x; }
        public double getY() { return y; }
    }

    /**
     * Node data class.
     */
    public static class Node {
        private String id;
        private String type;
        private Position position;
        private Map<String, Object> data = new HashMap<>();

        public Node(String id, String type) {
            this.id = id;
            this.type = type;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }

    /**
     * Edge data class.
     */
    public static class Edge {
        private final String source;
        private final String target;
        private final String sourceHandle;
        private final String targetHandle;

        public Edge(String source, String target, String sourceHandle, String targetHandle) {
            this.source = source;
            this.target = target;
            this.sourceHandle = sourceHandle;
            this.targetHandle = targetHandle;
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public String getSourceHandle() { return sourceHandle; }
        public String getTargetHandle() { return targetHandle; }
    }
}