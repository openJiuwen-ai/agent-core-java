/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for designer graph normalization.
 *
 * @since 2026-11-06
 */
class DesignerGraphNormalizerTest {

    private final DesignerGraphNormalizer normalizer = new DesignerGraphNormalizer();

    @Test
    void normalizesDefaultGraphFields() {
        DesignerGraph graph = normalizer.normalize(graph());

        assertThat(graph.schemaVersion()).isEqualTo(DesignerSchema.GRAPH_SCHEMA_VERSION);
        assertThat(graph.title()).isEqualTo("Untitled");
        assertThat(graph.description()).isEmpty();
        assertThat(graph.source()).isEqualTo("manual");
        assertThat(graph.createdAt()).isPositive();
        assertThat(graph.nodes()).hasSize(4);
        assertThat(graph.edges()).hasSize(3);
    }

    @Test
    void convertsPipelineRoleToModalityRole() {
        DesignerGraph graph = normalizer.normalize(graph());

        DesignerNode character = node(graph, "n_character_1");
        assertThat(character.config().get("pipeline")).isEqualTo("character");
        assertThat(character.config().get("role")).isEqualTo("image");
        assertThat(character.config().get("inputs")).isEqualTo(List.of());
    }

    @Test
    void infersPipelineFromNodeId() {
        DesignerGraph graph = normalizer.normalize(graph());

        assertThat(node(graph, "n_brief").config().get("pipeline")).isEqualTo("brief");
        assertThat(node(graph, "n_clip_1").config().get("pipeline")).isEqualTo("clip");
        assertThat(node(graph, "n_compose").config().get("pipeline")).isEqualTo("compose");
    }

    @Test
    void stripsDesignerPrefixFromAgentTemplate() {
        DesignerGraph raw = graphWithNode(new DesignerNode("n_character_1", "image", "",
                Map.of("pipeline", "character", "agent_template", "designer/character"),
                DesignerLayout.DEFAULT, null));

        DesignerGraph graph = normalizer.normalize(raw);

        assertThat(node(graph, "n_character_1").config().get("agent_template")).isEqualTo("character");
        assertThat(node(graph, "n_character_1").label()).isEqualTo("n_character_1");
    }

    @Test
    void dropsSyncEdgesAndGeneratesMissingEdgeIds() {
        DesignerGraph raw = graphWithEdge(new DesignerEdge("", "n_brief", "n_clip_1", "sync", null));

        DesignerGraph graph = normalizer.normalize(raw);

        assertThat(graph.edges()).hasSize(3);
        assertThat(graph.edges()).allMatch(edge -> "data".equals(edge.kind()));
        assertThat(graph.edges().get(0).id()).startsWith("e_");
    }

    @Test
    void rejectsUnsupportedNodeType() {
        DesignerGraph raw = graphWithNode(new DesignerNode("n_bad", "character", "bad",
                Map.of(), DesignerLayout.DEFAULT, null));

        assertThatThrownBy(() -> normalizer.normalize(raw))
                .isInstanceOf(DesignerGraphValidationException.class)
                .hasMessageContaining("unsupported node type");
    }

    @Test
    void rejectsDuplicateNodeIds() {
        DesignerGraph base = graph();
        List<DesignerNode> nodes = new java.util.ArrayList<>(base.nodes());
        nodes.add(new DesignerNode("n_brief", "text", "duplicate",
                Map.of("pipeline", "brief"), DesignerLayout.DEFAULT, null));
        DesignerGraph raw = base.withNodes(nodes);

        assertThatThrownBy(() -> normalizer.normalize(raw))
                .isInstanceOf(DesignerGraphValidationException.class)
                .hasMessageContaining("duplicate node id");
    }

    @Test
    void rejectsMissingEdgeEndpoint() {
        DesignerGraph raw = graphWithEdge(new DesignerEdge("e_bad", "n_brief", "n_missing", "data", null));

        assertThatThrownBy(() -> normalizer.normalize(raw))
                .isInstanceOf(DesignerGraphValidationException.class)
                .hasMessageContaining("edge endpoint");
    }

    @Test
    void rejectsUnsafeGraphId() {
        DesignerGraph raw = new DesignerGraph(DesignerSchema.GRAPH_SCHEMA_VERSION, "../escape",
                "default", "title", "", "manual", List.of(), List.of(), Map.of(), 1L, 1L);

        assertThatThrownBy(() -> normalizer.normalize(raw))
                .isInstanceOf(DesignerGraphValidationException.class)
                .hasMessageContaining("safe storage component");
    }

    private static DesignerNode node(DesignerGraph graph, String nodeId) {
        return graph.nodes().stream().filter(node -> nodeId.equals(node.id())).findFirst().orElseThrow();
    }

    private DesignerGraph graph() {
        List<DesignerNode> nodes = List.of(
                new DesignerNode("n_brief", "text", "Brief",
                        Map.of("pipeline", "brief"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_character_1", "image", "Character",
                        Map.of("role", "character"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_clip_1", "video", "Clip",
                        Map.of("pipeline", "clip"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_compose", "video", "Compose",
                        Map.of("pipeline", "compose"), DesignerLayout.DEFAULT, null));
        List<DesignerEdge> edges = List.of(
                new DesignerEdge("", "n_brief", "n_character_1", "data", null),
                new DesignerEdge("", "n_character_1", "n_clip_1", "data", null),
                new DesignerEdge("", "n_clip_1", "n_compose", "data", null));
        return new DesignerGraph(DesignerSchema.GRAPH_SCHEMA_VERSION, "graph_1", "default",
                "", null, null, nodes, edges, Map.of(), 0L, 0L);
    }

    private DesignerGraph graphWithNode(DesignerNode replacement) {
        DesignerGraph base = graph();
        List<DesignerNode> nodes = new java.util.ArrayList<>(base.nodes());
        nodes.removeIf(node -> node.id().equals(replacement.id()));
        nodes.add(replacement);
        return base.withNodes(nodes);
    }

    private DesignerGraph graphWithEdge(DesignerEdge extra) {
        DesignerGraph base = graph();
        List<DesignerEdge> edges = new java.util.ArrayList<>(base.edges());
        edges.add(extra);
        return base.withEdges(edges);
    }
}
