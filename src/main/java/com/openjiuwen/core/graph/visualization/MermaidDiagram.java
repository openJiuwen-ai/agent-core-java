/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates Mermaid flowchart syntax from a {@link DrawableGraph}.
 *
 * <p>Replaces Python's dependency on the {@code mermaid-py} library by generating
 * the Mermaid flowchart text syntax directly. Supports node shapes, link styles
 * (normal, dotted, thick), subgraph expansion, and link animation properties.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable._MermaidDiagram}.</p>
 */
class MermaidDiagram {

    private final AtomicInteger nodeIdCounter = new AtomicInteger(0);
    private final AtomicInteger linkIdCounter = new AtomicInteger(0);

    /**
     * Convert a DrawableGraph to Mermaid flowchart syntax.
     *
     * @param graph           the drawable graph
     * @param title           the diagram title (appears as comment if non-empty)
     * @param expandSubgraph  depth of subgraph expansion (0 = no expansion, negative = full)
     * @param enableAnimation whether to add animation properties to streaming links
     * @return the Mermaid flowchart syntax string
     */
    String toMermaid(DrawableGraph graph, String title, int expandSubgraph, boolean enableAnimation) {
        if (title == null || !(title instanceof String)) {
            throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    "reason", "'title' type is not str");
        }

        Map<String, MermaidNode> mermaidNodes = new LinkedHashMap<>();
        Map<String, SubGraphNode> subgraphMermaidNodes = new LinkedHashMap<>();

        for (DrawableNode node : graph.getNodes().values()) {
            if (expandSubgraph != 0 && node instanceof DrawableSubgraphNode subNode) {
                int nextDepth = expandSubgraph < 0 ? expandSubgraph : expandSubgraph - 1;
                subgraphMermaidNodes.put(node.getId(),
                        genMermaidSubgraphNode(nextDepth, subNode, enableAnimation));
            } else {
                String shape = "normal";
                if (graph.getStartNodes().contains(node) || graph.getEndNodes().contains(node)) {
                    shape = "round-edge";
                }
                mermaidNodes.put(node.getId(), new MermaidNode(nextNodeId(), node.getId(), shape));
            }
        }

        List<String> links = genMermaidLinks(graph, mermaidNodes, subgraphMermaidNodes, enableAnimation);

        return buildFlowchartScript(title, mermaidNodes, subgraphMermaidNodes, links);
    }

    // ==================== Internal data classes ====================

    /**
     * Represents a Mermaid node with a generated ID and display content.
     */
    static class MermaidNode {
        final String id;
        final String content;
        final String shape; // "normal" or "round-edge"

        MermaidNode(String id, String content, String shape) {
            this.id = id;
            this.content = content;
            this.shape = shape;
        }

        /**
         * Render the node definition in Mermaid syntax.
         */
        String toMermaid() {
            String escaped = escapeContent(content);
            return switch (shape) {
                case "round-edge" -> id + "(" + escaped + ")";
                default -> id + "[" + escaped + "]";
            };
        }

        private static String escapeContent(String content) {
            // Escape quotes and special chars for Mermaid
            return "\"" + content.replace("\"", "#quot;") + "\"";
        }
    }

    /**
     * Represents a subgraph node containing inner nodes and links.
     */
    static class SubGraphNode {
        final MermaidNode node;
        final List<MermaidNode> innerNodes;
        final Map<String, SubGraphNode> innerSubgraphs;
        final List<String> subgraphLinks;
        final List<MermaidNode> subgraphStartNodes;
        final List<MermaidNode> subgraphEndNodes;
        final List<MermaidNode> subgraphBreakNodes;

        SubGraphNode(MermaidNode node, List<MermaidNode> innerNodes,
                     Map<String, SubGraphNode> innerSubgraphs,
                     List<String> subgraphLinks,
                     List<MermaidNode> subgraphStartNodes,
                     List<MermaidNode> subgraphEndNodes,
                     List<MermaidNode> subgraphBreakNodes) {
            this.node = node;
            this.innerNodes = innerNodes;
            this.innerSubgraphs = innerSubgraphs;
            this.subgraphLinks = subgraphLinks;
            this.subgraphStartNodes = subgraphStartNodes;
            this.subgraphEndNodes = subgraphEndNodes;
            this.subgraphBreakNodes = subgraphBreakNodes;
        }
    }

    // ==================== Node/Link ID generation ====================

    private String nextNodeId() {
        return "node_" + nodeIdCounter.incrementAndGet();
    }

    private String nextLinkId() {
        return "link_" + linkIdCounter.incrementAndGet();
    }

    // ==================== Subgraph node generation ====================

    private SubGraphNode genMermaidSubgraphNode(int expandSubgraph,
                                                 DrawableSubgraphNode node,
                                                 boolean enableAnimation) {
        DrawableGraph subgraph = node.getSubgraph();
        Map<String, MermaidNode> innerMermaidNodes = new LinkedHashMap<>();
        Map<String, SubGraphNode> innerSubgraphs = new LinkedHashMap<>();

        for (DrawableNode subNode : subgraph.getNodes().values()) {
            if (expandSubgraph != 0 && subNode instanceof DrawableSubgraphNode subSubNode) {
                int nextDepth = expandSubgraph < 0 ? expandSubgraph : expandSubgraph - 1;
                innerSubgraphs.put(subNode.getId(),
                        genMermaidSubgraphNode(nextDepth, subSubNode, enableAnimation));
            } else {
                String shape = "normal";
                if (subgraph.getStartNodes().contains(subNode) || subgraph.getEndNodes().contains(subNode)) {
                    shape = "round-edge";
                }
                innerMermaidNodes.put(subNode.getId(),
                        new MermaidNode(nextNodeId(), subNode.getId(), shape));
            }
        }

        List<String> links = genMermaidLinks(subgraph, innerMermaidNodes, innerSubgraphs, enableAnimation);

        // Resolve start/end/break nodes to their MermaidNode representations
        List<MermaidNode> startNodes = resolveNodes(subgraph.getStartNodes(), innerMermaidNodes, innerSubgraphs);
        List<MermaidNode> endNodes = resolveNodes(subgraph.getEndNodes(), innerMermaidNodes, innerSubgraphs);
        List<MermaidNode> breakNodes = resolveNodes(subgraph.getBreakNodes(), innerMermaidNodes, innerSubgraphs);

        // Create the subgraph container node
        MermaidNode containerNode = new MermaidNode(nextNodeId(), node.getId(), "subgraph");

        return new SubGraphNode(containerNode,
                new ArrayList<>(innerMermaidNodes.values()),
                innerSubgraphs,
                links, startNodes, endNodes, breakNodes);
    }

    /**
     * Resolve DrawableNodes to their MermaidNode representations.
     */
    private List<MermaidNode> resolveNodes(List<DrawableNode> drawableNodes,
                                           Map<String, MermaidNode> mermaidNodes,
                                           Map<String, SubGraphNode> subgraphNodes) {
        List<MermaidNode> result = new ArrayList<>();
        for (DrawableNode dn : drawableNodes) {
            if (mermaidNodes.containsKey(dn.getId())) {
                result.add(mermaidNodes.get(dn.getId()));
            } else if (subgraphNodes.containsKey(dn.getId())) {
                result.add(subgraphNodes.get(dn.getId()).node);
            }
        }
        return result;
    }

    // ==================== Link generation ====================

    private List<String> genMermaidLinks(DrawableGraph graph,
                                         Map<String, MermaidNode> mermaidNodes,
                                         Map<String, SubGraphNode> subgraphNodes,
                                         boolean enableAnimation) {
        List<String> links = new ArrayList<>();

        for (DrawableEdge edge : graph.getEdges()) {
            String arrow = " --> ";       // normal
            String linkId = null;
            Map<String, String> properties = null;

            if (edge.isConditional()) {
                arrow = " -.-> ";         // dotted
            }
            if (edge.isStreaming()) {
                arrow = " ==> ";          // thick
                if (enableAnimation) {
                    linkId = nextLinkId();
                    properties = Map.of("animate", "true");
                }
            }

            String message = "";
            if (edge.isConditional() && edge.getData() != null) {
                message = "|\"" + edge.getData().toString() + "\"|";
            }

            // Build link strings based on source/target types
            List<String[]> sourceTargetPairs = resolveSourceTargetPairs(
                    edge.getSource(), edge.getTarget(), mermaidNodes, subgraphNodes);

            for (String[] pair : sourceTargetPairs) {
                String linkStr = buildLinkString(pair[0], pair[1], arrow, message, linkId, properties);
                links.add(linkStr);
            }
        }

        // Add inner subgraph links
        for (SubGraphNode sgNode : subgraphNodes.values()) {
            links.addAll(sgNode.subgraphLinks);
        }

        return links;
    }

    /**
     * Resolve source and target node IDs to Mermaid node ID pairs,
     * handling subgraph expansion (routing to start/end/break nodes).
     */
    private List<String[]> resolveSourceTargetPairs(String sourceId, String targetId,
                                                     Map<String, MermaidNode> mermaidNodes,
                                                     Map<String, SubGraphNode> subgraphNodes) {
        List<String[]> pairs = new ArrayList<>();
        boolean sourceIsNode = mermaidNodes.containsKey(sourceId);
        boolean sourceIsSub = subgraphNodes.containsKey(sourceId);
        boolean targetIsNode = targetId != null && mermaidNodes.containsKey(targetId);
        boolean targetIsSub = targetId != null && subgraphNodes.containsKey(targetId);

        if (sourceIsNode && targetIsNode) {
            pairs.add(new String[]{mermaidNodes.get(sourceId).id, mermaidNodes.get(targetId).id});
        } else if (sourceIsNode && targetIsSub) {
            // Connect to subgraph's start nodes
            for (MermaidNode startNode : subgraphNodes.get(targetId).subgraphStartNodes) {
                pairs.add(new String[]{mermaidNodes.get(sourceId).id, startNode.id});
            }
        } else if (sourceIsSub && targetIsNode) {
            // Connect from subgraph's end nodes + break nodes
            SubGraphNode sgNode = subgraphNodes.get(sourceId);
            for (MermaidNode endNode : sgNode.subgraphEndNodes) {
                pairs.add(new String[]{endNode.id, mermaidNodes.get(targetId).id});
            }
            for (MermaidNode breakNode : sgNode.subgraphBreakNodes) {
                pairs.add(new String[]{breakNode.id, mermaidNodes.get(targetId).id});
            }
        } else if (sourceIsSub && targetIsSub) {
            // Connect subgraph end nodes to next subgraph start nodes
            SubGraphNode srcSg = subgraphNodes.get(sourceId);
            SubGraphNode tgtSg = subgraphNodes.get(targetId);
            for (MermaidNode endNode : srcSg.subgraphEndNodes) {
                for (MermaidNode startNode : tgtSg.subgraphStartNodes) {
                    pairs.add(new String[]{endNode.id, startNode.id});
                }
            }
        }

        return pairs;
    }

    /**
     * Build a Mermaid link string with optional ID and properties.
     */
    private String buildLinkString(String sourceId, String targetId, String arrow,
                                    String message, String linkId, Map<String, String> properties) {
        StringBuilder sb = new StringBuilder();
        String tag = (linkId != null) ? linkId + "@" : "";
        sb.append(sourceId).append(" ").append(tag);

        // Strip leading space from arrow for tag attachment
        String arrowStr = arrow.strip();
        if (!message.isEmpty()) {
            // Insert message before the arrow head: e.g., "-.->|msg|"
            // Split arrow: for "-.->", message goes after it
            sb.append(arrowStr).append(message);
        } else {
            sb.append(arrowStr);
        }

        sb.append(" ").append(targetId);

        // Add properties on new line if present
        if (properties != null && !properties.isEmpty()) {
            sb.append("\n").append(tag).append("{");
            List<String> propEntries = new ArrayList<>();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                propEntries.add(entry.getKey() + ": " + entry.getValue());
            }
            sb.append(String.join(", ", propEntries)).append("}");
        }

        return sb.toString();
    }

    // ==================== Script generation ====================

    /**
     * Build the final Mermaid flowchart script text.
     */
    private String buildFlowchartScript(String title,
                                         Map<String, MermaidNode> mermaidNodes,
                                         Map<String, SubGraphNode> subgraphNodes,
                                         List<String> links) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("---\ntitle: ").append(title).append("\n---\n");
        sb.append("flowchart TD\n");

        // Regular nodes
        for (MermaidNode node : mermaidNodes.values()) {
            sb.append("    ").append(node.toMermaid()).append("\n");
        }

        // Subgraph nodes
        for (SubGraphNode sgNode : subgraphNodes.values()) {
            appendSubgraph(sb, sgNode, "    ");
        }

        // Links
        for (String link : links) {
            // Handle multi-line links (with properties)
            for (String line : link.split("\n")) {
                sb.append("    ").append(line).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Recursively append a subgraph definition to the Mermaid script.
     */
    private void appendSubgraph(StringBuilder sb, SubGraphNode sgNode, String indent) {
        sb.append(indent).append("subgraph ").append(sgNode.node.id)
                .append("[\"").append(sgNode.node.content).append("\"]\n");
        sb.append(indent).append("    direction TB\n");

        // Inner regular nodes
        for (MermaidNode innerNode : sgNode.innerNodes) {
            sb.append(indent).append("    ").append(innerNode.toMermaid()).append("\n");
        }

        // Inner subgraphs (recursive)
        for (SubGraphNode innerSg : sgNode.innerSubgraphs.values()) {
            appendSubgraph(sb, innerSg, indent + "    ");
        }

        sb.append(indent).append("end\n");
    }
}
