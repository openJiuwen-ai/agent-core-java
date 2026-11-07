/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and normalizes designer graph documents.
 *
 * @since 2026-11-06
 */
public final class DesignerGraphNormalizer {

    private static final Pattern CHARACTER_ID = Pattern.compile("^n_character(_\\d+)?$");
    private static final Pattern SCENE_ID = Pattern.compile("^n_scene(_\\d+)?$");
    private static final Pattern CLIP_ID = Pattern.compile("^n_clip(_\\d+)?$");

    /**
     * Normalize one graph document.
     *
     * @param graph raw graph
     * @return normalized graph
     * @throws DesignerGraphValidationException when the document violates the contract
     */
    public DesignerGraph normalize(DesignerGraph graph) {
        if (graph == null) {
            throw new DesignerGraphValidationException("graph is required");
        }
        if (!DesignerSchema.GRAPH_SCHEMA_VERSION.equals(graph.schemaVersion())) {
            throw new DesignerGraphValidationException("unsupported graph schema_version: "
                    + graph.schemaVersion());
        }
        String graphId = storageComponent(graph.graphId(), "graph_id");
        String projectId = requireNonBlank(graph.projectId(), "project_id");
        String source = normalizeSource(graph.source());
        List<DesignerNode> nodes = normalizeNodes(graph.nodes());
        List<DesignerEdge> edges = normalizeEdges(graph.edges(), nodeIds(nodes));
        long now = System.currentTimeMillis();
        long createdAt = graph.createdAt() > 0L ? graph.createdAt() : now;
        return new DesignerGraph(
                DesignerSchema.GRAPH_SCHEMA_VERSION,
                graphId,
                projectId,
                blankToDefault(graph.title(), "Untitled"),
                graph.description() == null ? "" : graph.description(),
                source,
                nodes,
                edges,
                graph.metadata() == null ? Map.of() : graph.metadata(),
                createdAt,
                now);
    }

    /**
     * Validate that an id is a single safe storage component.
     *
     * @param value raw id
     * @param field field name for the error message
     * @return the validated id
     * @throws DesignerGraphValidationException when the id is unsafe
     */
    public static String storageComponent(String value, String field) {
        String id = requireNonBlank(value, field);
        if (".".equals(id) || "..".equals(id)) {
            throw new DesignerGraphValidationException(field + " is not a safe storage component");
        }
        if (id.indexOf('/') >= 0 || id.indexOf('\\') >= 0 || id.indexOf(':') >= 0
                || id.indexOf('\0') >= 0) {
            throw new DesignerGraphValidationException(field + " is not a safe storage component");
        }
        Path path = Path.of(id);
        if (path.getNameCount() != 1 || path.isAbsolute()) {
            throw new DesignerGraphValidationException(field + " is not a safe storage component");
        }
        return id;
    }

    private List<DesignerNode> normalizeNodes(List<DesignerNode> rawNodes) {
        List<DesignerNode> nodes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (DesignerNode rawNode : rawNodes == null ? List.<DesignerNode>of() : rawNodes) {
            if (rawNode == null) {
                throw new DesignerGraphValidationException("node must not be null");
            }
            String id = requireNonBlank(rawNode.id(), "node.id");
            if (!ids.add(id)) {
                throw new DesignerGraphValidationException("duplicate node id: " + id);
            }
            nodes.add(normalizeNode(rawNode, id));
        }
        return nodes;
    }

    private DesignerNode normalizeNode(DesignerNode rawNode, String id) {
        String type = requireNonBlank(rawNode.type(), "node.type");
        if (!DesignerSchema.NODE_TYPES.contains(type)) {
            throw new DesignerGraphValidationException("unsupported node type: " + type);
        }
        Map<String, Object> config = new LinkedHashMap<>(rawNode.config());
        normalizePipelineAndRole(config, id, type);
        normalizeInputs(config);
        normalizeDelegate(config);
        normalizeAgentTemplate(config);
        normalizeInteractionMode(config);
        DesignerAssetRef outputRef = normalizeAssetRef(rawNode.outputRef());
        String label = blankToDefault(rawNode.label(), id);
        return new DesignerNode(id, type, label, config, rawNode.layout(), outputRef);
    }

    private void normalizePipelineAndRole(Map<String, Object> config, String id, String type) {
        String role = text(config.get("role"));
        String pipeline = text(config.get("pipeline"));
        if (DesignerSchema.PIPELINES.contains(role) && pipeline.isBlank()) {
            pipeline = role;
            role = type;
        }
        if (pipeline.isBlank()) {
            pipeline = inferPipeline(id);
            if (!pipeline.isBlank() && !type.equals(DesignerSchema.PIPELINE_TO_TYPE.get(pipeline))) {
                pipeline = "";
            }
        }
        if (!pipeline.isBlank() && !DesignerSchema.PIPELINES.contains(pipeline)) {
            throw new DesignerGraphValidationException("unsupported node pipeline: " + pipeline);
        }
        if (role.isBlank()) {
            role = type;
        }
        if (!DesignerSchema.NODE_TYPES.contains(role)
                && !DesignerSchema.PIPELINES.contains(role)
                && !DesignerSchema.USER_REFERENCE_ROLE.equals(role)) {
            throw new DesignerGraphValidationException("unsupported node role: " + role);
        }
        config.put("pipeline", pipeline);
        config.put("role", role);
    }

    private void normalizeInputs(Map<String, Object> config) {
        Object rawInputs = config.get("inputs");
        if (rawInputs == null) {
            config.put("inputs", List.of());
            return;
        }
        if (!(rawInputs instanceof List<?> list)) {
            throw new DesignerGraphValidationException("node.config.inputs must be an array");
        }
        List<String> inputs = new ArrayList<>(list.size());
        for (Object value : list) {
            String input = text(value);
            if (input.isBlank()) {
                throw new DesignerGraphValidationException("node.config.inputs must not contain blanks");
            }
            inputs.add(input);
        }
        config.put("inputs", List.copyOf(inputs));
    }

    private void normalizeDelegate(Map<String, Object> config) {
        String delegate = text(config.get("delegate"));
        if (!delegate.isBlank() && !DesignerSchema.DELEGATES.contains(delegate)) {
            throw new DesignerGraphValidationException("unsupported node delegate: " + delegate);
        }
    }

    private void normalizeAgentTemplate(Map<String, Object> config) {
        String template = text(config.get("agent_template"));
        if (template.startsWith("designer/")) {
            config.put("agent_template", template.substring("designer/".length()));
        }
    }

    private void normalizeInteractionMode(Map<String, Object> config) {
        String mode = text(config.get("interaction_mode"));
        if (!mode.isBlank() && !DesignerSchema.INTERACTION_MODES.contains(mode)) {
            throw new DesignerGraphValidationException("unsupported interaction_mode: " + mode);
        }
    }

    private List<DesignerEdge> normalizeEdges(List<DesignerEdge> rawEdges, Set<String> nodeIds) {
        List<DesignerEdge> edges = new ArrayList<>();
        for (DesignerEdge rawEdge : rawEdges == null ? List.<DesignerEdge>of() : rawEdges) {
            if (rawEdge == null) {
                throw new DesignerGraphValidationException("edge must not be null");
            }
            DesignerEdge edge = normalizeEdge(rawEdge, nodeIds);
            if (edge != null) {
                edges.add(edge);
            }
        }
        return edges;
    }

    private DesignerEdge normalizeEdge(DesignerEdge rawEdge, Set<String> nodeIds) {
        String source = requireNonBlank(rawEdge.source(), "edge.source");
        String target = requireNonBlank(rawEdge.target(), "edge.target");
        if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
            throw new DesignerGraphValidationException("edge endpoint does not exist: "
                    + source + " -> " + target);
        }
        String kind = text(rawEdge.kind());
        if (kind.isBlank()) {
            kind = "data";
        }
        if ("sync".equals(kind)) {
            return null;
        }
        if (!DesignerSchema.EDGE_KINDS.contains(kind)) {
            throw new DesignerGraphValidationException("unsupported edge kind: " + kind);
        }
        String id = blankToDefault(rawEdge.id(), "e_" + source + "_" + target);
        return new DesignerEdge(id, source, target, kind, normalizeEdgeLabel(rawEdge.label()));
    }

    private String normalizeEdgeLabel(String label) {
        String value = text(label);
        if ("对齐".equals(value) || "align".equalsIgnoreCase(value)) {
            return "Align";
        }
        return value.isBlank() ? null : value;
    }

    private DesignerAssetRef normalizeAssetRef(DesignerAssetRef ref) {
        if (ref == null) {
            return null;
        }
        String kind = requireNonBlank(ref.kind(), "output_ref.kind");
        if (!DesignerSchema.NODE_TYPES.contains(kind)) {
            throw new DesignerGraphValidationException("unsupported output_ref kind: " + kind);
        }
        return new DesignerAssetRef(kind, requireNonBlank(ref.uri(), "output_ref.uri"),
                ref.mimeType(), ref.label());
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "manual";
        }
        if (!DesignerSchema.GRAPH_SOURCES.contains(source)) {
            throw new DesignerGraphValidationException("unsupported graph source: " + source);
        }
        return source;
    }

    private static Set<String> nodeIds(List<DesignerNode> nodes) {
        Set<String> ids = new HashSet<>();
        for (DesignerNode node : nodes) {
            ids.add(node.id());
        }
        return ids;
    }

    private static String inferPipeline(String id) {
        if ("n_brief".equals(id)) {
            return "brief";
        }
        if ("n_storyboard".equals(id)) {
            return "storyboard";
        }
        if ("n_compose".equals(id) || "n_final".equals(id)) {
            return "compose";
        }
        if ("n_music".equals(id)) {
            return "music";
        }
        if ("n_speech".equals(id)) {
            return "speech";
        }
        if (CHARACTER_ID.matcher(id).matches()) {
            return "character";
        }
        if (SCENE_ID.matcher(id).matches()) {
            return "scene";
        }
        if (CLIP_ID.matcher(id).matches()) {
            return "clip";
        }
        return "";
    }

    private static String requireNonBlank(String value, String field) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new DesignerGraphValidationException(field + " is required");
        }
        return text;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
