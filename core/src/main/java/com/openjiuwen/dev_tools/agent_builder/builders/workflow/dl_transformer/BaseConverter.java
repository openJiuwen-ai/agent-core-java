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
 *
 * <p>Mirrors Python's {@code BaseConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/base.py}.</p>
 */
public abstract class BaseConverter {

    protected final Map<String, Object> nodeData;
    protected final Map<String, Object> nodesDict;
    protected final Map<String, Object> resource;
    protected final Position position;
    protected final Node node;
    protected final List<Edge> edges = new ArrayList<>();
    protected int variableIndex = 0;

    public BaseConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        this(nodeData, nodesDict, null, new Position(0, 0));
    }

    public BaseConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        this(nodeData, nodesDict, resource, new Position(0, 0));
    }

    public BaseConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        this(nodeData, nodesDict, null, position);
    }

    public BaseConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource,
            Position position) {
        this.nodeData = nodeData;
        this.nodesDict = nodesDict;
        this.resource = resource;
        this.position = position != null ? position : new Position(0, 0);

        NodeType nodeType = NodeType.valueOf(String.valueOf(nodeData.get("type")));
        this.node = new Node(String.valueOf(nodeData.get("id")), nodeType.getDslType());
    }

    protected abstract void convertSpecificConfig();

    public void convert() {
        convertCommonConfig();
        convertSpecificConfig();
        convertEdges();
    }

    public void convertCommonConfig() {
        node.setId(String.valueOf(nodeData.get("id")));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("position", Map.of("x", position.getX(), "y", position.getY()));
        node.setMeta(meta);
        Object description = nodeData.get("description");
        node.getData().setTitle(description != null ? String.valueOf(description) : "");
    }

    public void convertEdges() {
        Object next = nodeData.get("next");
        if (next != null && !String.valueOf(next).isEmpty()) {
            edges.add(new Edge(String.valueOf(nodeData.get("id")), String.valueOf(next)));
        }
    }

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
                result.put(
                        name,
                        new InputVariable(
                                String.valueOf(refVariable.get("type")),
                                refVariable.get("content"),
                                Map.of("index", variableIndex)
                        )
                );
            } else {
                result.put(
                        name,
                        new InputVariable(
                                SourceType.constant.getValue(),
                                value,
                                Map.of("index", variableIndex),
                                Map.of("type", item.getOrDefault("type", "string"))
                        )
                );
            }
            variableIndex++;
        }

        return result;
    }

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
            result.addProperty(
                    new OutputPropertySpec(
                            variableNames,
                            item.get("description") != null ? String.valueOf(item.get("description")) : null,
                            variableIndex,
                            item.get("type") != null ? String.valueOf(item.get("type")) : null,
                            castMap(item.get("items")),
                            castMap(item.get("properties")),
                            castStringList(item.get("required"))
                    )
            );
            variableIndex++;
        }

        return result;
    }

    public Node getNode() {
        return node;
    }

    public Map<String, Object> getNodeData() {
        return nodeData;
    }

    public Map<String, Object> getNodesDict() {
        return nodesDict;
    }

    public Map<String, Object> getResource() {
        return resource;
    }

    public Position getPosition() {
        return position;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    protected String generateVariableName(String baseName) {
        return baseName + "_" + (++variableIndex);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object value) {
        return value instanceof List<?> ? (List<String>) value : null;
    }
}
