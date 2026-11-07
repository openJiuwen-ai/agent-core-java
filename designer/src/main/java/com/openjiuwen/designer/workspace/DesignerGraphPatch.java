/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

import com.openjiuwen.designer.DesignerJson;
import com.openjiuwen.designer.schema.DesignerEdge;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerGraphNormalizer;
import com.openjiuwen.designer.schema.DesignerGraphValidationException;
import com.openjiuwen.designer.schema.DesignerNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the {@code designer.graph.patch} merge rules.
 *
 * @since 2026-11-06
 */
public final class DesignerGraphPatch {

    private final DesignerGraphNormalizer normalizer = new DesignerGraphNormalizer();

    /**
     * Apply one patch to a graph.
     *
     * @param graph current graph
     * @param patch patch object
     * @return normalized patched graph
     * @throws DesignerGraphValidationException when a patched node or edge is invalid
     */
    public DesignerGraph apply(DesignerGraph graph, Map<String, Object> patch) {
        Map<String, Object> safePatch = patch == null ? Map.of() : patch;
        List<DesignerNode> nodes = graph.mutableNodes();
        List<DesignerEdge> edges = graph.mutableEdges();
        upsertNodes(nodes, safePatch.get("upsert_nodes"));
        removeNodes(nodes, safePatch.get("remove_node_ids"));
        upsertEdges(edges, safePatch.get("upsert_edges"));
        removeEdges(edges, safePatch.get("remove_edge_ids"));
        pruneEdges(nodes, edges);
        String title = stringValue(safePatch.get("title"), graph.title());
        String description = safePatch.containsKey("description")
                ? stringValue(safePatch.get("description"), "")
                : graph.description();
        DesignerGraph patched = graph.withTitle(title)
                .withDescription(description)
                .withNodes(nodes)
                .withEdges(edges)
                .withUpdatedAt(System.currentTimeMillis());
        return normalizer.normalize(patched);
    }

    private void upsertNodes(List<DesignerNode> nodes, Object rawUpserts) {
        if (!(rawUpserts instanceof List<?> upserts)) {
            return;
        }
        for (Object raw : upserts) {
            if (!(raw instanceof Map<?, ?> map)) {
                throw new DesignerGraphValidationException("upsert_nodes entries must be objects");
            }
            DesignerNode incoming = convertNode(map);
            int index = indexOfNode(nodes, incoming.id());
            if (index < 0) {
                nodes.add(incoming);
            } else {
                nodes.set(index, mergeNode(nodes.get(index), incoming));
            }
        }
    }

    private void removeNodes(List<DesignerNode> nodes, Object rawIds) {
        Set<String> ids = stringSet(rawIds);
        if (!ids.isEmpty()) {
            nodes.removeIf(node -> ids.contains(node.id()));
        }
    }

    private void upsertEdges(List<DesignerEdge> edges, Object rawUpserts) {
        if (!(rawUpserts instanceof List<?> upserts)) {
            return;
        }
        for (Object raw : upserts) {
            if (!(raw instanceof Map<?, ?> map)) {
                throw new DesignerGraphValidationException("upsert_edges entries must be objects");
            }
            DesignerEdge incoming = convertEdge(map);
            int index = indexOfEdge(edges, incoming.id());
            if (index < 0) {
                edges.add(incoming);
            } else {
                edges.set(index, incoming);
            }
        }
    }

    private void removeEdges(List<DesignerEdge> edges, Object rawIds) {
        Set<String> ids = stringSet(rawIds);
        if (!ids.isEmpty()) {
            edges.removeIf(edge -> ids.contains(edge.id()));
        }
    }

    private void pruneEdges(List<DesignerNode> nodes, List<DesignerEdge> edges) {
        Set<String> nodeIds = nodes.stream().map(DesignerNode::id).collect(Collectors.toSet());
        edges.removeIf(edge -> !nodeIds.contains(edge.source()) || !nodeIds.contains(edge.target()));
    }

    private DesignerNode mergeNode(DesignerNode existing, DesignerNode incoming) {
        if (!existing.type().equals(incoming.type())) {
            return incoming;
        }
        Map<String, Object> config = mergeMaps(existing.config(), incoming.config());
        String label = incoming.label() == null || incoming.label().isBlank()
                ? existing.label()
                : incoming.label();
        return new DesignerNode(existing.id(), existing.type(), label, config,
                incoming.layout() == null ? existing.layout() : incoming.layout(),
                incoming.outputRef() == null ? existing.outputRef() : incoming.outputRef());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMaps(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            Object current = merged.get(entry.getKey());
            Object value = entry.getValue();
            if (current instanceof Map<?, ?> currentMap && value instanceof Map<?, ?> valueMap) {
                merged.put(entry.getKey(), mergeMaps((Map<String, Object>) currentMap,
                        (Map<String, Object>) valueMap));
            } else {
                merged.put(entry.getKey(), value);
            }
        }
        return merged;
    }

    private DesignerNode convertNode(Map<?, ?> raw) {
        try {
            return DesignerJson.mapper().convertValue(raw, DesignerNode.class);
        } catch (IllegalArgumentException ex) {
            throw new DesignerGraphValidationException("invalid upsert_nodes entry", ex);
        }
    }

    private DesignerEdge convertEdge(Map<?, ?> raw) {
        try {
            return DesignerJson.mapper().convertValue(raw, DesignerEdge.class);
        } catch (IllegalArgumentException ex) {
            throw new DesignerGraphValidationException("invalid upsert_edges entry", ex);
        }
    }

    private static int indexOfNode(List<DesignerNode> nodes, String id) {
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfEdge(List<DesignerEdge> edges, String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }
        for (int index = 0; index < edges.size(); index++) {
            if (id.equals(edges.get(index).id())) {
                return index;
            }
        }
        return -1;
    }

    private static Set<String> stringSet(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        List<String> values = new ArrayList<>();
        for (Object value : list) {
            if (value != null && !String.valueOf(value).isBlank()) {
                values.add(String.valueOf(value));
            }
        }
        return Set.copyOf(values);
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }
}
