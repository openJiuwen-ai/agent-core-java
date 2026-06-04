/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.utils;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.Relation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryKnowledgeGraphVisualizationTest {

    @TempDir
    Path tempDir;

    @Test
    void createKgVisualizationAddsEntityEpisodeAndRelationEdges() {
        Entity alice = entity("ent-1", "Alice Smith", "person", "Alice content", List.of("ep-1"));
        Entity project = entity("ent-2", "Demo Project", "project", "Project content", List.of());
        Episode episode = new Episode();
        episode.setUuid("ep-1");
        episode.setContent("Alice mentioned Demo Project.");
        Relation relation = new Relation();
        relation.setName("works_on");
        relation.setLhs(alice);
        relation.setRhs(project);
        relation.setContent("Alice works on Demo Project.");
        relation.setValidSince(1_700_000_000L);
        relation.setOffsetSince(32);

        GraphMemoryKnowledgeGraphVisualization.VisualizationNetwork network =
                GraphMemoryKnowledgeGraphVisualization.createKgVisualization(
                        List.of(alice, project), List.of(relation), List.of(episode));

        assertThat(network.nodes()).hasSize(3);
        assertThat(network.edges()).hasSize(2);
        assertThat(network.nodes().get(0).id()).isEqualTo("Alice_Smith");
        assertThat(network.nodes().get(0).color()).isEqualTo("#4285f4");
        assertThat(network.nodes().get(2).shape()).isEqualTo("diamond");
        assertThat(network.edges().get(0).from()).isEqualTo("Alice_Smith");
        assertThat(network.edges().get(0).to()).isEqualTo("Demo_Project");
        assertThat(network.edges().get(0).title()).contains("Valid since:");
        assertThat(network.edges().get(1).from()).isEqualTo("episode_ep-1");
        assertThat(network.edges().get(1).to()).isEqualTo("Alice_Smith");
        assertThat(network.edges().get(1).dashes()).isTrue();
    }

    @Test
    void createKgVisualizationDeduplicatesEntityNamesAndEscapesTitles() {
        Entity first = entity("e1", "Same Name", "unknown", "<unsafe>", List.of());
        first.setAttributes(Map.of("key", "value"));
        Entity second = entity("e2", "Same Name", "unknown", "safe", List.of());

        GraphMemoryKnowledgeGraphVisualization.VisualizationNetwork network =
                GraphMemoryKnowledgeGraphVisualization.createKgVisualization(List.of(first, second), List.of(), List.of());

        assertThat(network.nodes()).extracting(GraphMemoryKnowledgeGraphVisualization.Node::id)
                .containsExactly("Same_Name", "Same_Name-1");
        assertThat(network.nodes().get(0).color()).isEqualTo("#666666");
        assertThat(network.nodes().get(0).title()).contains("&lt;unsafe&gt;");
    }

    @Test
    void saveVisualizationWritesInfoPanelHtml() throws IOException {
        Entity entity = entity("e1", "Alice", "person", "content", List.of());
        GraphMemoryKnowledgeGraphVisualization.VisualizationNetwork network =
                GraphMemoryKnowledgeGraphVisualization.createKgVisualization(List.of(entity), List.of(), List.of());
        Path file = tempDir.resolve("kg_visualization.html");

        GraphMemoryKnowledgeGraphVisualization.saveVisualization(network, file, true);

        String html = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(html).contains("Knowledge Graph Visualization");
        assertThat(html).contains("info-panel");
        assertThat(html).contains("Alice");
        assertThat(html).contains("window.__kgSelectNode");
    }

    @Test
    void saveVisualizationCanUsePostMessageMode() throws IOException {
        GraphMemoryKnowledgeGraphVisualization.VisualizationNetwork network =
                new GraphMemoryKnowledgeGraphVisualization.VisualizationNetwork(true);
        Path file = tempDir.resolve("embedded.html");

        GraphMemoryKnowledgeGraphVisualization.saveVisualization(network, file, false);

        String html = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(html).contains("postMessage");
        assertThat(html).doesNotContain("info-panel");
    }

    @Test
    void mainRemovesHtmlSuffixCreatesParentAndReturnsNetwork() {
        Entity entity = entity("e1", "Alice", "person", "content", List.of("ep"));
        Episode episode = new Episode();
        episode.setUuid("ep");
        Relation relation = new Relation("knows", "relation", "e1", "e1");
        Path output = tempDir.resolve("nested").resolve("kg.html");

        GraphMemoryKnowledgeGraphVisualization.VisualizationNetwork network =
                GraphMemoryKnowledgeGraphVisualization.main(
                        List.of(entity), List.of(relation), List.of(episode), output.toString());

        assertThat(network.nodes()).hasSize(2);
        assertThat(Files.exists(output)).isTrue();
    }

    private static Entity entity(String uuid, String name, String entityType, String content, List<String> episodes) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setEntityType(entityType);
        entity.setContent(content);
        entity.setEpisodes(episodes);
        return entity;
    }
}
