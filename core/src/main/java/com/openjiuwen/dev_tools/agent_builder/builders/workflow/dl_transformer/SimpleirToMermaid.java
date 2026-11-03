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
 * Mirrors Python's {@code SimpleIrToMermaid} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/simpleir_to_mermaid.py}.
 */
public final class SimpleirToMermaid {
    private static final Pattern CONDITION_PATTERN = Pattern.compile("^When condition met\\[(?<cond>.+?)\\]");

    private SimpleirToMermaid() {
    }

    public static List<Map<String, Object>> edgeTransform(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Map<String, Object> node : nodes) {
            Object next = node.get("next");
            if (next != null && !String.valueOf(next).isEmpty()) {
                Map<String, Object> edgeItem = new LinkedHashMap<>();
                edgeItem.put("source", node.get("id"));
                edgeItem.put("target", next);
                edges.add(edgeItem);
            } else if (!"End".equals(node.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) node.getOrDefault("parameters", Map.of());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> conditions =
                        (List<Map<String, Object>>) parameters.getOrDefault("conditions", List.of());
                for (Map<String, Object> condition : conditions) {
                    Object conditionNext = condition.get("next");
                    if (conditionNext != null && !String.valueOf(conditionNext).isEmpty()) {
                        Map<String, Object> edgeItem = new LinkedHashMap<>();
                        edgeItem.put("source", node.get("id"));
                        edgeItem.put("target", conditionNext);
                        edgeItem.put("branch", condition.getOrDefault("branch", ""));
                        edgeItem.put("description", condition.getOrDefault("description", ""));
                        edges.add(edgeItem);
                    }
                }
            }
        }

        return edges;
    }

    public static String transToMermaid(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) data.getOrDefault("nodes", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) data.getOrDefault("edges", List.of());

        Map<String, String> idToDesc = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = String.valueOf(node.get("id"));
            String description = String.valueOf(node.getOrDefault("description", nodeId));
            idToDesc.put(nodeId, description);
        }

        Map<String, Integer> countEdges = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get("source"));
            countEdges.put(source, countEdges.getOrDefault(source, 0) + 1);
        }

        List<String> lines = new ArrayList<>();
        lines.add("graph TD");

        for (Map.Entry<String, String> entry : idToDesc.entrySet()) {
            String nodeId = entry.getKey();
            String label = entry.getValue().replace('`', '\'').replace('"', '\'');
            lines.add("  " + nodeId + "[Node" + nodeId + ": " + label + "]");
        }

        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get("source"));
            String target = String.valueOf(edge.get("target"));
            String description = String.valueOf(edge.getOrDefault("description", "")).replace('`', '\'').replace('"', '\'');
            Matcher matcher = CONDITION_PATTERN.matcher(description);

            if (countEdges.getOrDefault(source, 0) > 1 && !description.isEmpty()) {
                String label = matcher.find() ? matcher.group("cond") : description;
                lines.add("  " + source + " -- " + label + " --> " + target);
            } else {
                lines.add("  " + source + " --> " + target);
            }
        }

        return String.join("\n", lines);
    }

    public static String transformToMermaid(List<Map<String, Object>> jsonData) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", jsonData);
        data.put("edges", edgeTransform(jsonData));
        return transToMermaid(data);
    }
}
