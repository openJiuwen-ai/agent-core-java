/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SimpleIR to Mermaid converter.
 * <p>
 * Transforms SimpleIR workflow format to Mermaid flowchart.
 * <p>
 * Mirrors Python's {@code SimpleIrToMermaid} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.simpleir_to_mermaid}.
 */
public class SimpleirToMermaid {

    /** Pattern for extracting condition from edge description.
     *  Note: Java uses (?<name>...) for named groups, not Python's (?P<name>...).
     */
    private static final Pattern CONDITION_PATTERN = Pattern.compile("^When condition met\\[(?<cond>.+?)\\]");

    /**
     * Transform edge information from node list.
     * <p>
     * Mirrors Python's {@code edge_transform} method.
     *
     * @param nodes Node list containing workflow nodes
     * @return Edge list containing source, target, and optional branch/description
     */
    public static List<Map<String, Object>> edgeTransform(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> edges = new ArrayList<>();
        
        for (Map<String, Object> node : nodes) {
            if (node.containsKey("next") && node.get("next") != null && !node.get("next").toString().isEmpty()) {
                Map<String, Object> edgeItem = new LinkedHashMap<>();
                edgeItem.put("source", node.get("id"));
                edgeItem.put("target", node.get("next"));
                edges.add(edgeItem);
            } else {
                String nodeType = (String) node.get("type");
                if (!"End".equals(nodeType)) {
                    Map<String, Object> parameters = (Map<String, Object>) node.get("parameters");
                    if (parameters != null) {
                        List<Map<String, Object>> conditions = (List<Map<String, Object>>) parameters.get("conditions");
                        if (conditions != null) {
                            for (Map<String, Object> condition : conditions) {
                                if (condition.containsKey("next") && condition.get("next") != null 
                                        && !condition.get("next").toString().isEmpty()) {
                                    String conDesc = (String) condition.get("description");
                                    Map<String, Object> edgeItem = new LinkedHashMap<>();
                                    edgeItem.put("source", node.get("id"));
                                    edgeItem.put("target", condition.get("next"));
                                    edgeItem.put("branch", condition.getOrDefault("branch", ""));
                                    edgeItem.put("description", conDesc != null ? conDesc : "");
                                    edges.add(edgeItem);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return edges;
    }

    /**
     * Transform to Mermaid format from nodes and edges data.
     * <p>
     * Mirrors Python's {@code trans_to_mermaid} method.
     *
     * @param data Dictionary containing nodes and edges
     * @return Mermaid code string
     */
    public static String transToMermaid(Map<String, Object> data) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) data.getOrDefault("nodes", new ArrayList<>());
        List<Map<String, Object>> edges = (List<Map<String, Object>>) data.getOrDefault("edges", new ArrayList<>());

        // Build id to description mapping
        Map<String, String> idToDesc = new LinkedHashMap<>();
        for (Map<String, Object> n : nodes) {
            String nodeId = (String) n.get("id");
            String desc = (String) n.getOrDefault("description", nodeId);
            idToDesc.put(nodeId, desc);
        }

        // Count edges per source
        Map<String, Integer> countEdges = new HashMap<>();
        for (Map<String, Object> e : edges) {
            String src = (String) e.get("source");
            countEdges.put(src, countEdges.getOrDefault(src, 0) + 1);
        }

        StringBuilder lines = new StringBuilder("graph TD\n");

        // Add node definitions
        for (Map.Entry<String, String> entry : idToDesc.entrySet()) {
            String nodeId = entry.getKey();
            String desc = entry.getValue();
            String label = desc.replace('`', '\'').replace('"', '\'');
            lines.append("  ").append(nodeId).append("[Node").append(nodeId).append(": ").append(label).append("]\n");
        }

        // Add edge definitions
        for (Map<String, Object> e : edges) {
            String src = (String) e.get("source");
            String dst = (String) e.get("target");
            String edgesDesc = (String) e.getOrDefault("description", "");
            if (edgesDesc != null) {
                edgesDesc = edgesDesc.replace('`', '\'').replace('"', '\'');
            }

            Matcher match = CONDITION_PATTERN.matcher(edgesDesc != null ? edgesDesc : "");

            if (countEdges.getOrDefault(src, 0) > 1 && edgesDesc != null && !edgesDesc.isEmpty()) {
                String label = match.find() ? match.group("cond") : edgesDesc;
                lines.append("  ").append(src).append(" -- ").append(label).append(" --> ").append(dst).append("\n");
            } else {
                lines.append("  ").append(src).append(" --> ").append(dst).append("\n");
            }
        }

        return lines.toString().trim();
    }

    /**
     * Transform SimpleIR format to Mermaid.
     * <p>
     * Mirrors Python's {@code transform_to_mermaid} method.
     *
     * @param jsonData SimpleIR format node list
     * @return Mermaid code string
     */
    public static String transformToMermaid(List<Map<String, Object>> jsonData) {
        List<Map<String, Object>> edges = edgeTransform(jsonData);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", jsonData);
        data.put("edges", edges);
        return transToMermaid(data);
    }

    /**
     * Legacy method placeholder.
     * <p>
     * Generates a basic Mermaid diagram from workflow object.
     *
     * @param workflow Workflow object
     * @return Mermaid diagram string
     * @deprecated Use {@link #transformToMermaid(List)} instead
     */
    @Deprecated
    public static String toMermaid(Object workflow) {
        return "graph TD\n    A[Start] --> B[End]\n";
    }
}