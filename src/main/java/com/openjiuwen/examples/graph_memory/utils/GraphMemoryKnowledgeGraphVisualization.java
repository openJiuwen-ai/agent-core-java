/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.Relation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Knowledge graph visualization helpers for the graph memory example.
 *
 * <p>Mirrors Python's {@code examples.graph_memory.utils.visualize_kg}.</p>
 */
public final class GraphMemoryKnowledgeGraphVisualization {

    private static final Logger LOGGER = Logger.getLogger(GraphMemoryKnowledgeGraphVisualization.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Map<String, String> ENTITY_COLORS = Map.of(
            "person", "#4285f4",
            "place", "#34a853",
            "project", "#ea4335",
            "technology", "#fbbc04",
            "hobby", "#9c27b0",
            "career_field", "#ff9800"
    );

    private GraphMemoryKnowledgeGraphVisualization() {
    }

    public static VisualizationNetwork createKgVisualization(List<Entity> entities,
                                                             List<Relation> relations,
                                                             List<Episode> episodes) {
        VisualizationNetwork network = new VisualizationNetwork(true);
        Map<String, String> entityNodes = new LinkedHashMap<>();
        Map<String, String> entityUuids = new LinkedHashMap<>();

        for (Entity entity : nonNullList(entities)) {
            String entityType = entityType(entity);
            String color = ENTITY_COLORS.getOrDefault(entityType, "#666666");
            String rawName = safeString(entity.getName()).replace(" ", "_");
            String name = rawName;
            int counter = 1;
            while (entityNodes.containsKey(name)) {
                name = rawName + "-" + counter;
                counter += 1;
            }

            String attrs = attributesAsHtml(entity.getAttributes());
            String title = "<b>" + escapeHtml(name) + "</b><br>Type: " + escapeHtml(entityType)
                    + "<br>Content: " + escapeHtml(entity.getContent())
                    + "<br>----------<br>Attributes: " + attrs;
            network.addNode(new Node(name, name + "\n(" + entityType + ")", title, color, 25, "dot"));
            entityUuids.put(entity.getUuid(), name);
            entityNodes.put(name, name);
        }

        Map<String, String> episodeNodes = new LinkedHashMap<>();
        for (Episode episode : nonNullList(episodes)) {
            String nodeId = "episode_" + episode.getUuid();
            String title = "<b>" + escapeHtml(episode.getUuid()) + "</b><br>Type: "
                    + escapeHtml(episode.getObjType()) + "<br>Content: " + escapeHtml(episode.getContent());
            network.addNode(new Node(
                    nodeId,
                    episode.getUuid() + "\n(" + episode.getObjType() + ")",
                    title,
                    "#e0e0e0",
                    15,
                    "diamond"));
            episodeNodes.put(episode.getUuid(), nodeId);
        }

        for (Relation relation : nonNullList(relations)) {
            String sourceName = entityUuids.get(relation.getLhs());
            String targetName = entityUuids.get(relation.getRhs());
            String sourceId = null;
            String targetId = null;
            if (entityNodes.containsKey(sourceName) && entityNodes.containsKey(targetName)) {
                sourceId = entityNodes.get(sourceName);
                targetId = entityNodes.get(targetName);
            } else if (episodeNodes.containsKey(sourceName) && entityNodes.containsKey(targetName)) {
                sourceId = episodeNodes.get(sourceName);
                targetId = entityNodes.get(targetName);
            } else if (entityNodes.containsKey(sourceName) && episodeNodes.containsKey(targetName)) {
                sourceId = entityNodes.get(sourceName);
                targetId = episodeNodes.get(targetName);
            }
            if (sourceId != null && targetId != null) {
                List<String> titleLines = new ArrayList<>();
                titleLines.add("<b>" + escapeHtml(relation.getName()) + "</b><br>Type: "
                        + escapeHtml(relation.getObjType()) + "<br>Content: " + escapeHtml(relation.getContent()));
                if (relation.getValidSince() > 0) {
                    titleLines.add("Valid since: " + storedTimeIso(relation.getValidSince(), relation.getOffsetSince()));
                }
                if (relation.getValidUntil() > 0) {
                    titleLines.add("Valid until: " + storedTimeIso(relation.getValidUntil(), relation.getOffsetUntil()));
                }
                network.addEdge(new Edge(sourceId, targetId, String.join("<br>", titleLines),
                        "#666666", 2, "to", false));
            }
        }

        for (Episode episode : nonNullList(episodes)) {
            String episodeId = episodeNodes.get(episode.getUuid());
            if (episodeId == null) {
                continue;
            }
            for (Entity entity : nonNullList(entities)) {
                if (entity.getEpisodes().contains(episode.getUuid())) {
                    String entityId = entityUuids.get(entity.getUuid());
                    if (entityId != null) {
                        network.addEdge(new Edge(
                                episodeId,
                                entityId,
                                "Mentioned in: " + episode.getUuid(),
                                "#cccccc",
                                1,
                                "to",
                                true));
                    }
                }
            }
        }
        return network;
    }

    public static void saveVisualization(VisualizationNetwork network, String filename) {
        saveVisualization(network, Path.of(filename), true);
    }

    public static void saveVisualization(VisualizationNetwork network, Path filename, boolean enableInfoPanel) {
        try {
            Files.createDirectories(filename.toAbsolutePath().normalize().getParent());
            Files.writeString(filename, toHtml(network, enableInfoPanel), StandardCharsets.UTF_8);
            LOGGER.info("Visualization saved to " + filename);
            LOGGER.info("Open the HTML file in your web browser to view the interactive graph");
        } catch (Exception e) {
            try {
                Files.writeString(filename, "", StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                LOGGER.warning("Failed to save empty visualization fallback: " + ignored.getMessage());
            }
        }
    }

    public static VisualizationNetwork main(List<Entity> entities,
                                            List<Relation> relations,
                                            List<Episode> episodes,
                                            String name) {
        LOGGER.info("Creating Knowledge Graph visualization...");
        String normalizedName = removeHtmlSuffix(name);
        Path output = Path.of(normalizedName + ".html");
        Path parent = output.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create visualization directory: " + parent, e);
            }
        }

        VisualizationNetwork network = createKgVisualization(entities, relations, episodes);
        if (!nonNullList(entities).isEmpty() && !nonNullList(relations).isEmpty() && !nonNullList(episodes).isEmpty()) {
            saveVisualization(network, output, true);
        }
        LOGGER.info("Visualization created with:");
        LOGGER.info("- " + nonNullList(entities).size() + " entities");
        LOGGER.info("- " + nonNullList(episodes).size() + " episodes");
        LOGGER.info("- " + nonNullList(relations).size() + " relations");
        LOGGER.info("- " + network.nodes().size() + " total nodes");
        LOGGER.info("- " + network.edges().size() + " total edges");
        return network;
    }

    private static String toHtml(VisualizationNetwork network, boolean enableInfoPanel) throws JsonProcessingException {
        String nodes = OBJECT_MAPPER.writeValueAsString(network.nodes());
        String edges = OBJECT_MAPPER.writeValueAsString(network.edges());
        String patch = enableInfoPanel ? infoPanelScript() : postMessageScript();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Knowledge Graph Visualization</title>
                  <style>
                    html, body { margin: 0; height: 100%%; font-family: Arial, sans-serif; }
                    #network { width: 100%%; height: 600px; border: 1px solid #ddd; position: relative; overflow: auto; }
                    .node { display: inline-block; margin: 12px; padding: 10px; border-radius: 50%%; color: #fff; cursor: pointer; }
                    .edge { margin: 6px 12px; color: #444; cursor: pointer; }
                  </style>
                </head>
                <body>
                  <div id="network"></div>
                  <script type="text/javascript">
                    const nodes = %s;
                    const edges = %s;
                    const networkRoot = document.getElementById("network");
                    nodes.forEach((node) => {
                      const item = document.createElement("button");
                      item.className = "node";
                      item.style.backgroundColor = node.color;
                      item.textContent = node.label;
                      item.title = node.title;
                      item.addEventListener("click", () => window.__kgSelectNode(node));
                      networkRoot.appendChild(item);
                    });
                    edges.forEach((edge) => {
                      const item = document.createElement("div");
                      item.className = "edge";
                      item.textContent = edge.from + " -> " + edge.to;
                      item.title = edge.title;
                      item.addEventListener("click", () => window.__kgSelectEdge(edge));
                      networkRoot.appendChild(item);
                    });
                  </script>
                  %s
                </body>
                </html>
                """.formatted(nodes, edges, patch);
    }

    private static String infoPanelScript() {
        return """
                <style>
                #info-panel {
                    position: fixed;
                    right: 0;
                    top: 0;
                    width: 15%;
                    height: 100%;
                    background-color: #f9f9f9;
                    border-left: 1px solid #ccc;
                    padding: 15px;
                    overflow-y: auto;
                    font-family: Arial, sans-serif;
                    font-size: 14px;
                    white-space: normal;
                    word-break: break-word;
                    z-index: 9999;
                }

                #info-panel h3 {
                    margin-top: 0;
                }

                #info-content pre {
                    white-space: pre-wrap !important;
                    word-break: break-word !important;
                    overflow-wrap: anywhere !important;
                    text-overflow: visible !important;
                    overflow: visible !important;
                    display: block;
                    font-family: inherit;
                    margin: 0;
                }

                div, span, p {
                    text-overflow: visible !important;
                }
                </style>

                <div id="info-panel">
                <h3>Details</h3>
                <div id="info-content">Click a node or relation to view details</div>
                </div>
                <script type="text/javascript">
                window.__kgSelectNode = function (node) {
                    document.getElementById("info-content").innerHTML =
                        "<strong>Entity:</strong><br><pre>" + node.title + "</pre>";
                };
                window.__kgSelectEdge = function (edge) {
                    document.getElementById("info-content").innerHTML =
                        "<strong>Relation:</strong><br><pre>" + edge.title + "</pre>";
                };
                </script>""";
    }

    private static String postMessageScript() {
        return """
                <script type="text/javascript">
                window.__kgSelectNode = function (node) {
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({
                            type: 'node-click',
                            data: {
                                label: node.label || node.id,
                                title: node.title,
                                id: node.id
                            }
                        }, '*');
                    }
                };
                window.__kgSelectEdge = function (edge) {
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({
                            type: 'edge-click',
                            data: {
                                label: edge.label || edge.id,
                                title: edge.title,
                                id: edge.id,
                                from: edge.from,
                                to: edge.to
                            }
                        }, '*');
                    }
                };
                </script>""";
    }

    private static String attributesAsHtml(Map<String, Object> attributes) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attributes == null ? Map.of() : attributes)
                    .replace("\n", "<br>");
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String storedTimeIso(long timestamp, int offset) {
        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(offset * 15 * 60);
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zoneOffset)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String entityType(Entity entity) {
        String entityType = safeString(entity.getEntityType());
        return entityType.isBlank() ? entity.getObjType() : entityType;
    }

    private static String removeHtmlSuffix(String name) {
        return safeString(name).endsWith(".html") ? name.substring(0, name.length() - ".html".length()) : safeString(name);
    }

    private static String safeString(String value) {
        return Objects.toString(value, "");
    }

    private static String escapeHtml(String value) {
        return safeString(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static <T> List<T> nonNullList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record VisualizationNetwork(boolean directed, List<Node> nodes, List<Edge> edges) {
        public VisualizationNetwork(boolean directed) {
            this(directed, new ArrayList<>(), new ArrayList<>());
        }

        public void addNode(Node node) {
            nodes.add(node);
        }

        public void addEdge(Edge edge) {
            edges.add(edge);
        }
    }

    public record Node(String id, String label, String title, String color, int size, String shape) {
    }

    public record Edge(String from, String to, String title, String color, int width, String arrows, boolean dashes) {
    }
}
