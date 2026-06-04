/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    protected Map<String, Object> nodeData;
    protected Map<String, Object> nodesDict;
    protected Map<String, Object> resource;
    protected com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position position;
    protected Node node;
    protected List<com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Edge> edges =
            new ArrayList<>();
    protected int variableIndex = 0;

    public BaseConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        this(nodeData, nodesDict, null,
                new com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position(0, 0));
    }

    public BaseConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        this(nodeData, nodesDict, resource,
                new com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position(0, 0));
    }

    public BaseConverter(Map<String, Object> nodeData,
                         Map<String, Object> nodesDict,
                         com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position position) {
        this(nodeData, nodesDict, null, position);
    }

    public BaseConverter(Map<String, Object> nodeData,
                         Map<String, Object> nodesDict,
                         Map<String, Object> resource,
                         com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position position) {
        this.nodeData = nodeData;
        this.nodesDict = nodesDict;
        this.resource = resource;
        this.position = position != null ? position
                : new com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position(0, 0);

        NodeType nodeType = NodeType.valueOf(String.valueOf(nodeData.get("type")));
        this.node = new Node(String.valueOf(nodeData.get("id")), nodeType.getDslType());
    }

    /**
     * Convert specific configuration. Subclasses must implement this method.
     */
    protected abstract void convertSpecificConfig();

    /**
     * Execute conversion.
     */
    public void convert() {
        convertCommonConfig();
        convertSpecificConfig();
        convertEdges();
    }

    /**
     * Convert common configuration.
     */
    public void convertCommonConfig() {
        this.node.setId(String.valueOf(this.nodeData.get("id")));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("position", Map.of("x", this.position.getX(), "y", this.position.getY()));
        this.node.setMeta(meta);
        Object description = this.nodeData.get("description");
        this.node.getData().setTitle(description != null ? description.toString() : "");
    }

    /**
     * Convert edges from the DL {@code next} field.
     */
    public void convertEdges() {
        Object next = nodeData.get("next");
        if (next != null && !next.toString().isEmpty()) {
            this.edges.add(new Edge(String.valueOf(nodeData.get("id")), next.toString()));
        }
    }

    /**
     * Convert input variables.
     */
    protected Map<String, InputVariable> convertInputVariables(List<Map<String, Object>> inputs) {
        Map<String, InputVariable> result = new LinkedHashMap<>();
        if (inputs == null) {
            return result;
        }
        for (Map<String, Object> item : inputs) {
            String name = String.valueOf(item.get("name"));
            String value = String.valueOf(item.get("value"));
            if (value.contains("${")) {
                Map<String, Object> refVariable = ConverterUtils.convertRefVariable(value);
                result.put(name, new InputVariable(
                        String.valueOf(refVariable.get("type")),
                        refVariable.get("content"),
                        Map.of("index", variableIndex)
                ));
            } else {
                result.put(name, new InputVariable(
                        SourceType.constant.getValue(),
                        value,
                        Map.of("index", variableIndex),
                        Map.of("type", item.getOrDefault("type", "string"))
                ));
            }
            variableIndex++;
        }
        return result;
    }

    /**
     * Convert output field definitions.
     */
    protected OutputsField convertOutputsField(List<Map<String, Object>> outputs) {
        OutputsField result = new OutputsField("object");
        if (outputs == null) {
            return result;
        }
        for (Map<String, Object> item : outputs) {
            String[] parts = String.valueOf(item.get("name")).split("_of_");
            List<String> variableNames = new ArrayList<>();
            Collections.addAll(variableNames, parts);
            Collections.reverse(variableNames);
            result.addProperty(new OutputPropertySpec(
                    variableNames,
                    String.valueOf(item.get("description")),
                    variableIndex,
                    item.get("type") != null ? item.get("type").toString() : null,
                    castMap(item.get("items")),
                    castMap(item.get("properties")),
                    castStringList(item.get("required"))
            ));
            variableIndex++;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object value) {
        return value instanceof List<?> ? (List<String>) value : null;
    }

    public Node getNode() {
        return this.node;
    }

    public Map<String, Object> getNodeData() {
        return this.nodeData;
    }

    public Map<String, Object> getNodesDict() {
        return this.nodesDict;
    }

    public Map<String, Object> getResource() {
        return this.resource;
    }

    public com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position getPosition() {
        return this.position;
    }

    public List<com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Edge> getEdges() {
        return this.edges;
    }

    protected String generateVariableName(String baseName) {
        return baseName + "_" + (++variableIndex);
    }

    /**
     * Compatibility alias for converters that still reference BaseConverter.Position.
     */
    public static class Position
            extends com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Position {

        public Position(double x, double y) {
            super(x, y);
        }
    }

    /**
     * Compatibility alias for converters that still reference BaseConverter.Edge.
     */
    public static class Edge
            extends com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.Edge {

        private final String targetHandle;

        public Edge(String source, String target) {
            this(source, target, null, null);
        }

        public Edge(String source, String target, String sourceHandle) {
            this(source, target, sourceHandle, null);
        }

        public Edge(String source, String target, String sourceHandle, String targetHandle) {
            super(source, target, sourceHandle);
            this.targetHandle = targetHandle;
        }

        public String getSource() {
            return getSourceNodeId();
        }

        public String getTarget() {
            return getTargetNodeId();
        }

        public String getSourceHandle() {
            return getSourcePortId();
        }

        public String getTargetHandle() {
            return targetHandle;
        }
    }
}
