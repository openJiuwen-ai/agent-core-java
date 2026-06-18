/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.component.AdvancedLoopComponent;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.IntentDetectionComponent;
import com.openjiuwen.core.workflow.component.LoopComponent;
import com.openjiuwen.core.workflow.component.SubWorkflowComponent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds and renders a drawable graph representation of a workflow.
 *
 * <p>Mirrors Python's {@code Drawable} in
 * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
 */
public class Drawable {

    private final DrawableGraph graph;
    private final Set<String> loopNodes;

    public Drawable() {
        this.graph = new DrawableGraph(
                new LinkedHashMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
        this.loopNodes = new HashSet<>();
    }

    /**
     * Converts a workflow component into a drawable node and saves it in this drawable graph.
     *
     * <p>Mirrors Python's {@code Drawable.add_node} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param nodeId node identifier
     * @param component workflow component to render
     */
    public void addNode(String nodeId, ComponentComposable component) {
        if (component instanceof LoopComponent loopComponent) {
            addLoopSubgraphNode(nodeId, loopComponent.getLoopGroup().getDrawable().getGraph());
        } else if (component instanceof AdvancedLoopComponent advancedLoopComponent) {
            addLoopSubgraphNode(nodeId, advancedLoopComponent.getBody().getDrawable().getGraph());
        } else if (component instanceof SubWorkflowComponent subWorkflowComponent) {
            graph.getNodes().put(
                    nodeId,
                    new DrawableSubgraphNode(
                            nodeId,
                            subWorkflowComponent.getSubWorkflowInternal().getDrawable().getGraph()
                    )
            );
        } else if (component instanceof BranchComponent branchComponent) {
            graph.getNodes().put(nodeId, new DrawableNode(nodeId));
            addEdge(nodeId, null, true, false, branchComponent.router());
        } else if (component instanceof IntentDetectionComponent intentDetectionComponent) {
            graph.getNodes().put(nodeId, new DrawableNode(nodeId));
            addEdge(nodeId, null, true, false, intentDetectionComponent.router());
        } else {
            graph.getNodes().put(nodeId, new DrawableNode(nodeId));
        }
    }

    /**
     * Saves the node whose id is {@code nodeId} to this graph's start nodes.
     *
     * <p>Mirrors Python's {@code Drawable.set_start_node} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param nodeId node identifier
     */
    public void setStartNode(String nodeId) {
        if (!graph.getNodes().containsKey(nodeId)) {
            throw ErrorHelper.buildError(
                    StatusCode.DRAWABLE_GRAPH_START_NODE_INVALID,
                    "node_id", nodeId,
                    "reason", "node '" + nodeId + "' does not exist in the graph"
            );
        }
        graph.getStartNodes().add(graph.getNodes().get(nodeId));
    }

    /**
     * Saves the node whose id is {@code nodeId} to this graph's end nodes.
     *
     * <p>Mirrors Python's {@code Drawable.set_end_node} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param nodeId node identifier
     */
    public void setEndNode(String nodeId) {
        if (!graph.getNodes().containsKey(nodeId)) {
            throw ErrorHelper.buildError(
                    StatusCode.DRAWABLE_GRAPH_END_NODE_INVALID,
                    "node_id", nodeId,
                    "reason", "node '" + nodeId + "' does not exist in the graph"
            );
        }
        graph.getEndNodes().add(graph.getNodes().get(nodeId));
    }

    /**
     * Saves the node whose id is {@code nodeId} to this graph's break nodes.
     *
     * <p>Mirrors Python's {@code Drawable.set_break_node} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param nodeId node identifier
     */
    public void setBreakNode(String nodeId) {
        if (!graph.getNodes().containsKey(nodeId)) {
            throw ErrorHelper.buildError(
                    StatusCode.DRAWABLE_GRAPH_BREAK_NODE_INVALID,
                    "node_id", nodeId,
                    "reason", "node '" + nodeId + "' does not exist in the graph"
            );
        }
        graph.getBreakNodes().add(graph.getNodes().get(nodeId));
    }

    /**
     * Adds an edge without optional data.
     *
     * <p>Mirrors Python's {@code Drawable.add_edge} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param source source node id
     * @param target target node id
     */
    public void addEdge(String source, String target) {
        addEdge(source, target, false, false);
    }

    /**
     * Adds an edge without optional data.
     *
     * <p>Mirrors Python's {@code Drawable.add_edge} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param source source node id
     * @param target target node id
     * @param conditional whether this is a conditional edge
     * @param streaming whether this is a streaming edge
     */
    public void addEdge(String source, String target, boolean conditional, boolean streaming) {
        addEdge(source, target, conditional, streaming, (Stringifiable) null);
    }

    /**
     * Adds an edge with stringifiable payload data.
     *
     * <p>Mirrors Python's {@code Drawable.add_edge} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param source source node id
     * @param target target node id
     * @param conditional whether this is a conditional edge
     * @param streaming whether this is a streaming edge
     * @param data edge data compatible with Python's {@code Stringifiable} protocol
     */
    public void addEdge(
            String source,
            String target,
            boolean conditional,
            boolean streaming,
            Stringifiable data
    ) {
        if (loopNodes.contains(source)) {
            graph.getEdges().add(new DrawableEdge(source, target, null, true, false));
            return;
        }
        if (!conditional) {
            graph.getEdges().add(new DrawableEdge(source, target, data, false, streaming));
        }
    }

    /**
     * Adds a conditional edge by expanding branch-router drawable targets and data.
     *
     * <p>Mirrors Python's {@code Drawable.add_edge} branch-router path in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param source source node id
     * @param target ignored for branch-router expansion, matching Python's behavior
     * @param conditional whether this is a conditional edge
     * @param streaming whether this is a streaming edge
     * @param data branch router data
     */
    public void addEdge(
            String source,
            String target,
            boolean conditional,
            boolean streaming,
            BranchRouter data
    ) {
        if (loopNodes.contains(source)) {
            graph.getEdges().add(new DrawableEdge(source, target, null, true, false));
            return;
        }
        if (!conditional) {
            graph.getEdges().add(new DrawableEdge(source, target, null, false, streaming));
            return;
        }

        DrawableBranchRouter drawableBranchRouter = data.getDrawableBranchRouter();
        List<String> branchTargets = drawableBranchRouter.getTargets();
        List<String> branchDatas = drawableBranchRouter.getDatas();
        for (int i = 0; i < branchTargets.size(); i++) {
            graph.getEdges().add(new DrawableEdge(
                    source,
                    branchTargets.get(i),
                    stringValue(branchDatas.get(i)),
                    true,
                    streaming
            ));
        }
    }

    /**
     * Adds a conditional edge by expanding target names supplied by a Java target provider.
     *
     * <p>Mirrors Python's {@code _get_targets(data)} fallback in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param source source node id
     * @param target ignored for target-provider expansion, matching Python's behavior
     * @param conditional whether this is a conditional edge
     * @param streaming whether this is a streaming edge
     * @param data provider of target names
     */
    public void addEdge(
            String source,
            String target,
            boolean conditional,
            boolean streaming,
            TargetProvider data
    ) {
        if (loopNodes.contains(source)) {
            graph.getEdges().add(new DrawableEdge(source, target, null, true, false));
            return;
        }
        if (!conditional) {
            graph.getEdges().add(new DrawableEdge(source, target, null, false, streaming));
            return;
        }

        for (String branchTarget : data.getTargets()) {
            graph.getEdges().add(new DrawableEdge(source, branchTarget, null, true, streaming));
        }
    }

    /**
     * Converts this drawable graph to Mermaid syntax.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param title graph title
     * @param expandSubgraph non-negative expansion depth
     * @param enableAnimation whether streaming links should include animation properties
     * @return Mermaid flowchart syntax
     */
    public String toMermaid(String title, int expandSubgraph, boolean enableAnimation) {
        if (expandSubgraph < 0) {
            throw ErrorHelper.buildError(
                    StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    "reason", "'expand_subgraph' type is not bool"
            );
        }
        return new MermaidDiagram().toMermaid(graph, title, expandSubgraph, enableAnimation);
    }

    /**
     * Converts this drawable graph to Mermaid syntax.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param title graph title
     * @param expandSubgraph whether subgraphs should be fully expanded
     * @param enableAnimation whether streaming links should include animation properties
     * @return Mermaid flowchart syntax
     */
    public String toMermaid(String title, boolean expandSubgraph, boolean enableAnimation) {
        int depth = expandSubgraph ? MermaidDiagram.EXPAND_ALL : 0;
        return new MermaidDiagram().toMermaid(graph, title, depth, enableAnimation);
    }

    /**
     * Converts this drawable graph to Mermaid syntax with Python defaults.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @return Mermaid flowchart syntax
     */
    public String toMermaid() {
        return toMermaid("", 0, false);
    }

    /**
     * Converts this drawable graph to Mermaid syntax and renders it as PNG bytes.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid_png} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param title graph title
     * @param expandSubgraph non-negative expansion depth
     * @return PNG bytes
     */
    public byte[] toMermaidPng(String title, int expandSubgraph) {
        return MermaidRenderer.renderPng(toMermaid(title, expandSubgraph, false));
    }

    /**
     * Converts this drawable graph to Mermaid syntax and renders it as PNG bytes.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid_png} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param title graph title
     * @param expandSubgraph whether subgraphs should be fully expanded
     * @return PNG bytes
     */
    public byte[] toMermaidPng(String title, boolean expandSubgraph) {
        return MermaidRenderer.renderPng(toMermaid(title, expandSubgraph, false));
    }

    /**
     * Converts this drawable graph to Mermaid syntax and renders it as SVG bytes.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid_svg} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param title graph title
     * @param expandSubgraph non-negative expansion depth
     * @return SVG bytes
     */
    public byte[] toMermaidSvg(String title, int expandSubgraph) {
        return MermaidRenderer.renderSvg(toMermaid(title, expandSubgraph, true));
    }

    /**
     * Converts this drawable graph to Mermaid syntax and renders it as SVG bytes.
     *
     * <p>Mirrors Python's {@code Drawable.to_mermaid_svg} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @param title graph title
     * @param expandSubgraph whether subgraphs should be fully expanded
     * @return SVG bytes
     */
    public byte[] toMermaidSvg(String title, boolean expandSubgraph) {
        return MermaidRenderer.renderSvg(toMermaid(title, expandSubgraph, true));
    }

    /**
     * Gets the backing drawable graph.
     *
     * <p>Mirrors Python's {@code Drawable.get_graph} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     *
     * @return backing drawable graph
     */
    public DrawableGraph getGraph() {
        return graph;
    }

    private void addLoopSubgraphNode(String nodeId, DrawableGraph subgraph) {
        fillEndNodesIfEmpty(subgraph);
        graph.getNodes().put(nodeId, new DrawableSubgraphNode(nodeId, subgraph));
        loopNodes.add(nodeId);
        addEdge(nodeId, nodeId);
    }

    private void fillEndNodesIfEmpty(DrawableGraph subgraph) {
        if (!subgraph.getEndNodes().isEmpty()) {
            return;
        }
        Map<String, Integer> outDegrees = new LinkedHashMap<>();
        for (String subgraphNodeId : subgraph.getNodes().keySet()) {
            outDegrees.put(subgraphNodeId, 0);
        }
        for (DrawableEdge edge : subgraph.getEdges()) {
            outDegrees.putIfAbsent(edge.getSource(), 0);
            outDegrees.compute(edge.getSource(), (ignored, value) -> value == null ? 1 : value + 1);
        }
        for (Map.Entry<String, Integer> entry : outDegrees.entrySet()) {
            if (entry.getValue() == 0) {
                subgraph.getEndNodes().add(subgraph.getNodes().get(entry.getKey()));
            }
        }
    }

    private static Stringifiable stringValue(String value) {
        return value == null ? null : new StringValue(value);
    }

    /**
     * Supplies conditional edge targets where Python would inspect a callable return Literal.
     *
     * <p>Mirrors Python's {@code _get_targets} callable fallback in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     */
    public interface TargetProvider {

        /**
         * Gets possible target node names.
         *
         * @return target node names
         */
        List<String> getTargets();
    }

    /**
     * Wraps Java strings as edge payloads compatible with Python's Stringifiable protocol.
     *
     * <p>Mirrors Python's branch data string payloads in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     */
    private record StringValue(String value) implements Stringifiable {

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Generates Mermaid flowchart syntax from drawable graph state.
     *
     * <p>Mirrors Python's {@code _MermaidDiagram} in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     */
    private static final class MermaidDiagram {

        private static final int EXPAND_ALL = -1;

        private final NodeIdGenerator nodeIdGenerator = new NodeIdGenerator();
        private final LinkIdGenerator linkIdGenerator = new LinkIdGenerator();

        private String toMermaid(
                DrawableGraph graph,
                String title,
                int expandSubgraph,
                boolean enableAnimation
        ) {
            if (title == null) {
                throw ErrorHelper.buildError(
                        StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                        "reason", "'title' type is not str"
                );
            }

            Map<String, MermaidNode> mermaidNodes = new LinkedHashMap<>();
            Map<String, SubGraphNode> subgraphMermaidNodes = new LinkedHashMap<>();
            for (DrawableNode node : graph.getNodes().values()) {
                if (expandSubgraph != 0 && node instanceof DrawableSubgraphNode subgraphNode) {
                    subgraphMermaidNodes.put(
                            node.getId(),
                            genMermaidNode(nextExpandDepth(expandSubgraph), subgraphNode, enableAnimation)
                    );
                } else {
                    mermaidNodes.put(node.getId(), new MermaidNode(
                            nodeIdGenerator.next(),
                            node.getId(),
                            shapeOf(node, graph)
                    ));
                }
            }

            List<String> links = genMermaidLinks(graph, mermaidNodes, subgraphMermaidNodes, enableAnimation);
            return buildFlowchartScript(title, mermaidNodes, subgraphMermaidNodes, links);
        }

        private SubGraphNode genMermaidNode(
                int expandSubgraph,
                DrawableSubgraphNode node,
                boolean enableAnimation
        ) {
            DrawableGraph subgraph = node.getSubgraph();
            Map<String, MermaidNode> mermaidNodes = new LinkedHashMap<>();
            Map<String, SubGraphNode> subgraphMermaidNodes = new LinkedHashMap<>();
            for (DrawableNode subNode : subgraph.getNodes().values()) {
                if (expandSubgraph != 0 && subNode instanceof DrawableSubgraphNode subSubgraphNode) {
                    subgraphMermaidNodes.put(
                            subNode.getId(),
                            genMermaidNode(nextExpandDepth(expandSubgraph), subSubgraphNode, enableAnimation)
                    );
                } else {
                    mermaidNodes.put(subNode.getId(), new MermaidNode(
                            nodeIdGenerator.next(),
                            subNode.getId(),
                            shapeOf(subNode, subgraph)
                    ));
                }
            }

            List<String> links = genMermaidLinks(subgraph, mermaidNodes, subgraphMermaidNodes, enableAnimation);
            return new SubGraphNode(
                    new MermaidNode(nodeIdGenerator.next(), node.getId(), "subgraph"),
                    new ArrayList<>(mermaidNodes.values()),
                    subgraphMermaidNodes,
                    links,
                    resolveNodes(subgraph.getStartNodes(), mermaidNodes, subgraphMermaidNodes),
                    resolveNodes(subgraph.getEndNodes(), mermaidNodes, subgraphMermaidNodes),
                    resolveNodes(safeNodes(subgraph.getBreakNodes()), mermaidNodes, subgraphMermaidNodes)
            );
        }

        private List<String> genMermaidLinks(
                DrawableGraph graph,
                Map<String, MermaidNode> mermaidNodes,
                Map<String, SubGraphNode> subgraphMermaidNodes,
                boolean enableAnimation
        ) {
            List<String> links = new ArrayList<>();
            for (DrawableEdge edge : graph.getEdges()) {
                String shape = edge.isConditional() ? "-.->" : "-->";
                String linkId = "";
                Map<String, String> properties = Map.of();
                if (edge.isStreaming()) {
                    shape = "==>";
                    if (enableAnimation) {
                        linkId = linkIdGenerator.next();
                        properties = Map.of("animate", "true");
                    }
                }

                String message = "";
                if (edge.isConditional() && edge.getData() != null) {
                    message = "|\"" + edge.getData().toString() + "\"|";
                }

                for (String[] pair : resolveSourceTargetPairs(
                        edge.getSource(),
                        edge.getTarget(),
                        mermaidNodes,
                        subgraphMermaidNodes
                )) {
                    links.add(buildLink(pair[0], pair[1], shape, message, linkId, properties));
                }
            }
            for (SubGraphNode node : subgraphMermaidNodes.values()) {
                links.addAll(node.subgraphLinks());
            }
            return links;
        }

        private List<String[]> resolveSourceTargetPairs(
                String sourceId,
                String targetId,
                Map<String, MermaidNode> mermaidNodes,
                Map<String, SubGraphNode> subgraphMermaidNodes
        ) {
            List<String[]> pairs = new ArrayList<>();
            boolean sourceIsNode = mermaidNodes.containsKey(sourceId);
            boolean sourceIsSubgraph = subgraphMermaidNodes.containsKey(sourceId);
            boolean targetIsNode = targetId != null && mermaidNodes.containsKey(targetId);
            boolean targetIsSubgraph = targetId != null && subgraphMermaidNodes.containsKey(targetId);

            if (sourceIsNode && targetIsNode) {
                pairs.add(new String[] {mermaidNodes.get(sourceId).id(), mermaidNodes.get(targetId).id()});
            } else if (sourceIsNode && targetIsSubgraph) {
                for (MermaidNode startNode : subgraphMermaidNodes.get(targetId).subgraphStartNodes()) {
                    pairs.add(new String[] {mermaidNodes.get(sourceId).id(), startNode.id()});
                }
            } else if (sourceIsSubgraph && targetIsNode) {
                SubGraphNode subGraphNode = subgraphMermaidNodes.get(sourceId);
                for (MermaidNode endNode : subGraphNode.subgraphEndNodes()) {
                    pairs.add(new String[] {endNode.id(), mermaidNodes.get(targetId).id()});
                }
                for (MermaidNode breakNode : subGraphNode.subgraphBreakNodes()) {
                    pairs.add(new String[] {breakNode.id(), mermaidNodes.get(targetId).id()});
                }
            } else if (sourceIsSubgraph && targetIsSubgraph) {
                SubGraphNode source = subgraphMermaidNodes.get(sourceId);
                SubGraphNode target = subgraphMermaidNodes.get(targetId);
                for (MermaidNode sourceEndNode : source.subgraphEndNodes()) {
                    for (MermaidNode targetStartNode : target.subgraphStartNodes()) {
                        pairs.add(new String[] {sourceEndNode.id(), targetStartNode.id()});
                    }
                }
            }
            return pairs;
        }

        private static List<MermaidNode> resolveNodes(
                List<DrawableNode> drawableNodes,
                Map<String, MermaidNode> mermaidNodes,
                Map<String, SubGraphNode> subgraphMermaidNodes
        ) {
            List<MermaidNode> resolved = new ArrayList<>();
            for (DrawableNode drawableNode : safeNodes(drawableNodes)) {
                if (mermaidNodes.containsKey(drawableNode.getId())) {
                    resolved.add(mermaidNodes.get(drawableNode.getId()));
                } else if (subgraphMermaidNodes.containsKey(drawableNode.getId())) {
                    resolved.add(subgraphMermaidNodes.get(drawableNode.getId()).node());
                }
            }
            return resolved;
        }

        private static List<DrawableNode> safeNodes(List<DrawableNode> nodes) {
            return nodes == null ? List.of() : nodes;
        }

        private static String shapeOf(DrawableNode node, DrawableGraph graph) {
            return graph.getStartNodes().contains(node) || graph.getEndNodes().contains(node)
                    ? "round-edge"
                    : "normal";
        }

        private static int nextExpandDepth(int expandSubgraph) {
            return expandSubgraph == EXPAND_ALL ? EXPAND_ALL : expandSubgraph - 1;
        }

        private static String buildLink(
                String sourceId,
                String targetId,
                String shape,
                String message,
                String linkId,
                Map<String, String> properties
        ) {
            String tag = linkId.isEmpty() ? "" : linkId + "@";
            StringBuilder builder = new StringBuilder();
            builder.append(sourceId).append(' ').append(tag).append(shape).append(message).append(' ').append(targetId);
            if (!properties.isEmpty()) {
                builder.append('\n')
                        .append(tag)
                        .append('{');
                List<String> propertyValues = new ArrayList<>();
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    propertyValues.add(entry.getKey() + ": " + entry.getValue());
                }
                builder.append(String.join(", ", propertyValues)).append('}');
            }
            return builder.toString();
        }

        private static String buildFlowchartScript(
                String title,
                Map<String, MermaidNode> mermaidNodes,
                Map<String, SubGraphNode> subgraphMermaidNodes,
                List<String> links
        ) {
            StringBuilder builder = new StringBuilder();
            builder.append("---\n")
                    .append("title: ")
                    .append(title)
                    .append("\n---\n")
                    .append("flowchart TB\n");
            for (MermaidNode node : mermaidNodes.values()) {
                builder.append('\t').append(node.toMermaid()).append('\n');
            }
            for (SubGraphNode node : subgraphMermaidNodes.values()) {
                appendSubgraph(builder, node, "\t");
            }
            for (String link : links) {
                for (String line : link.split("\n")) {
                    builder.append('\t').append(line).append('\n');
                }
            }
            return builder.toString();
        }

        private static void appendSubgraph(StringBuilder builder, SubGraphNode subGraphNode, String indent) {
            builder.append(indent)
                    .append("subgraph ")
                    .append(subGraphNode.node().id())
                    .append(" [\"")
                    .append(escapeText(subGraphNode.node().content()))
                    .append("\"]\n")
                    .append(indent)
                    .append("direction TB\n");
            for (MermaidNode innerNode : subGraphNode.innerNodes()) {
                builder.append(indent).append(innerNode.toMermaid()).append('\n');
            }
            for (SubGraphNode innerSubgraph : subGraphNode.innerSubgraphs().values()) {
                appendSubgraph(builder, innerSubgraph, indent);
            }
            builder.append(indent).append("end\n");
        }

        private static String escapeText(String text) {
            return text.replace("\"", "#quot;");
        }

        /**
         * Generates Mermaid node ids.
         *
         * <p>Mirrors Python's {@code _MermaidDiagram._NodeIdGenerator} in
         * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
         */
        private static final class NodeIdGenerator {

            private final AtomicInteger nextId = new AtomicInteger();

            private String next() {
                return "node_" + nextId.incrementAndGet();
            }
        }

        /**
         * Generates Mermaid link ids.
         *
         * <p>Mirrors Python's {@code _MermaidDiagram._LinkIdGenerator} in
         * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
         */
        private static final class LinkIdGenerator {

            private final AtomicInteger nextId = new AtomicInteger();

            private String next() {
                return "link_" + nextId.incrementAndGet();
            }
        }

        /**
         * Represents a generated Mermaid node.
         *
         * <p>Mirrors Python's Mermaid {@code Node} usage in
         * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
         */
        private record MermaidNode(String id, String content, String shape) {

            private String toMermaid() {
                String escaped = "\"" + escapeText(content) + "\"";
                return "round-edge".equals(shape) ? id + "(" + escaped + ")" : id + "[" + escaped + "]";
            }
        }

        /**
         * Represents a Mermaid subgraph and its resolved boundary nodes.
         *
         * <p>Mirrors Python's {@code _MermaidDiagram._SubGraphNode} in
         * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
         */
        private record SubGraphNode(
                MermaidNode node,
                List<MermaidNode> innerNodes,
                Map<String, SubGraphNode> innerSubgraphs,
                List<String> subgraphLinks,
                List<MermaidNode> subgraphStartNodes,
                List<MermaidNode> subgraphEndNodes,
                List<MermaidNode> subgraphBreakNodes
        ) {
        }
    }

    /**
     * Renders Mermaid syntax to image bytes.
     *
     * <p>Mirrors Python's {@code Mermaid(...).img_response/svg_response} calls in
     * {@code openjiuwen/core/graph/visualization/drawable.py}.</p>
     */
    private static final class MermaidRenderer {

        private static final Logger LOGGER = Logger.getLogger(MermaidRenderer.class.getName());
        private static final String MERMAID_INK_BASE = "https://mermaid.ink";
        private static final Duration TIMEOUT = Duration.ofSeconds(30);
        private static final int MAX_ATTEMPTS = 3;

        private MermaidRenderer() {
        }

        private static byte[] renderPng(String mermaidSyntax) {
            return render(mermaidSyntax, "/img/");
        }

        private static byte[] renderSvg(String mermaidSyntax) {
            return render(mermaidSyntax, "/svg/");
        }

        private static byte[] render(String mermaidSyntax, String pathPrefix) {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            String encoded = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mermaidSyntax.getBytes(StandardCharsets.UTF_8));
            URI uri = URI.create(MERMAID_INK_BASE + pathPrefix + encoded);

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(uri)
                            .timeout(TIMEOUT)
                            .GET()
                            .build();
                    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == 200) {
                        return response.body();
                    }
                    LOGGER.log(
                            Level.WARNING,
                            "Mermaid rendering returned status {0} on attempt {1}",
                            new Object[] {response.statusCode(), attempt}
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return fallbackBytes(mermaidSyntax, pathPrefix);
                } catch (Exception e) {
                    if (attempt >= MAX_ATTEMPTS) {
                        LOGGER.log(Level.WARNING, "Failed to render Mermaid diagram", e);
                        return fallbackBytes(mermaidSyntax, pathPrefix);
                    }
                    LOGGER.log(Level.WARNING, "Retry Mermaid rendering after failure on attempt {0}", attempt);
                }
            }
            return fallbackBytes(mermaidSyntax, pathPrefix);
        }

        private static byte[] fallbackBytes(String mermaidSyntax, String pathPrefix) {
            if ("/svg/".equals(pathPrefix)) {
                String escaped = mermaidSyntax
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
                String svg = """
                        <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="80">
                          <rect width="100%" height="100%" fill="white"/>
                          <text x="12" y="24" font-family="monospace" font-size="12">Mermaid render fallback</text>
                          <text x="12" y="44" font-family="monospace" font-size="10">%s</text>
                        </svg>
                        """.formatted(escaped);
                return svg.getBytes(StandardCharsets.UTF_8);
            }
            return new byte[0];
        }
    }
}
