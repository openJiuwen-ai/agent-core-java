/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Designer execution graph document.
 *
 * @param schemaVersion wire schema version
 * @param graphId graph identifier
 * @param projectId owning project identifier
 * @param title display title
 * @param description graph description
 * @param source graph source: prompt or manual
 * @param nodes graph nodes
 * @param edges graph edges
 * @param metadata extensible metadata; director intermediate products live here
 * @param createdAt creation time in epoch milliseconds
 * @param updatedAt last update time in epoch milliseconds
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerGraph(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("graph_id") String graphId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("source") String source,
        @JsonProperty("nodes") List<DesignerNode> nodes,
        @JsonProperty("edges") List<DesignerEdge> edges,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("created_at") long createdAt,
        @JsonProperty("updated_at") long updatedAt) {

    public DesignerGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * Return a copy with different nodes.
     *
     * @param newNodes replacement nodes
     * @return copied graph
     */
    public DesignerGraph withNodes(List<DesignerNode> newNodes) {
        return new DesignerGraph(schemaVersion, graphId, projectId, title, description, source,
                newNodes, edges, metadata, createdAt, updatedAt);
    }

    /**
     * Return a copy with different edges.
     *
     * @param newEdges replacement edges
     * @return copied graph
     */
    public DesignerGraph withEdges(List<DesignerEdge> newEdges) {
        return new DesignerGraph(schemaVersion, graphId, projectId, title, description, source,
                nodes, newEdges, metadata, createdAt, updatedAt);
    }

    /**
     * Return a copy with different metadata.
     *
     * @param newMetadata replacement metadata
     * @return copied graph
     */
    public DesignerGraph withMetadata(Map<String, Object> newMetadata) {
        return new DesignerGraph(schemaVersion, graphId, projectId, title, description, source,
                nodes, edges, newMetadata, createdAt, updatedAt);
    }

    /**
     * Return a copy with a different title.
     *
     * @param newTitle replacement title
     * @return copied graph
     */
    public DesignerGraph withTitle(String newTitle) {
        return new DesignerGraph(schemaVersion, graphId, projectId, newTitle, description, source,
                nodes, edges, metadata, createdAt, updatedAt);
    }

    /**
     * Return a copy with a different description.
     *
     * @param newDescription replacement description
     * @return copied graph
     */
    public DesignerGraph withDescription(String newDescription) {
        return new DesignerGraph(schemaVersion, graphId, projectId, title, newDescription, source,
                nodes, edges, metadata, createdAt, updatedAt);
    }

    /**
     * Return a copy with a different update timestamp.
     *
     * @param newUpdatedAt update time in epoch milliseconds
     * @return copied graph
     */
    public DesignerGraph withUpdatedAt(long newUpdatedAt) {
        return new DesignerGraph(schemaVersion, graphId, projectId, title, description, source,
                nodes, edges, metadata, createdAt, newUpdatedAt);
    }

    /**
     * Return a mutable copy of the node list.
     *
     * @return mutable node list
     */
    public List<DesignerNode> mutableNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * Return a mutable copy of the edge list.
     *
     * @return mutable edge list
     */
    public List<DesignerEdge> mutableEdges() {
        return new ArrayList<>(edges);
    }
}
