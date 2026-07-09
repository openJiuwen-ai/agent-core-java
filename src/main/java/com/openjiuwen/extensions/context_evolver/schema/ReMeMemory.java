/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReMeMemory}.
 * 
 * @since 0.1.7
 */
public class ReMeMemory {
    private String workspaceId = "default";
    private String whenToUse;
    private String content;
    private double score;

    /**
     * Instant.now.
     * 
     * @since 0.1.7
     */
    private Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;

    /**
     * ReMeMemoryMetadata.
     * 
     * @since 0.1.7
     */
    private ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();

    /**
     * ReMeMemory.
     * 
     * @since 0.1.7
     */
    public ReMeMemory() {
        // Default constructor
    }

    /**
     * toVectorNode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorNode toVectorNode() {
        Map<String, Object> nodeMetadata = new LinkedHashMap<>();
        nodeMetadata.put("type", "reme_memory");
        nodeMetadata.put("when_to_use", whenToUse);
        nodeMetadata.put("content", content);
        nodeMetadata.put("score", score);
        nodeMetadata.put("created_at", createdAt.toString());
        nodeMetadata.put("updated_at", updatedAt.toString());
        nodeMetadata.put("workspace_id", workspaceId);
        nodeMetadata.put("metadata", metadata.toMap());
        return new VectorNode("reme_" + workspaceId + "_" + SchemaUtils.md5Hex(whenToUse).substring(0, 12), whenToUse,
                null, nodeMetadata);
    }

    /**
     * toRetrievedMemory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ReMeRetrievedMemory toRetrievedMemory() {
        return new ReMeRetrievedMemory(whenToUse, content);
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("when_to_use", whenToUse);
        result.put("content", content);
        result.put("score", score);
        result.put("created_at", createdAt.toString());
        result.put("updated_at", updatedAt.toString());
        result.put("metadata", metadata.toMap());
        result.put("workspace_id", workspaceId);
        return result;
    }

    /**
     * fromVectorNode.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    public static ReMeMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static ReMeMemory fromMap(Map<String, Object> data) {
        ReMeMemory result = new ReMeMemory();
        result.workspaceId = SchemaUtils.stringValue(data.get("workspace_id"), "default");
        result.whenToUse = SchemaUtils.stringValue(data.get("when_to_use"), "");
        result.content = SchemaUtils.stringValue(data.get("content"), "");
        result.score = SchemaUtils.doubleValue(data.get("score"), 0.0d);
        result.createdAt = SchemaUtils.instantValue(data.get("created_at"), Instant.now());
        result.updatedAt = SchemaUtils.instantValue(data.get("updated_at"), result.createdAt);
        result.metadata = ReMeMemoryMetadata.fromMap(SchemaUtils.mapValue(data.get("metadata")));
        return result;
    }

    /**
     * getWorkspaceId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWorkspaceId() {
        return workspaceId;
    }

    /**
     * setWorkspaceId.
     * 
     * @param workspaceId workspaceId
     * @since 0.1.7
     */
    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId != null && !workspaceId.isBlank() ? workspaceId : "default";
    }

    /**
     * getWhenToUse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWhenToUse() {
        return whenToUse;
    }

    /**
     * setWhenToUse.
     * 
     * @param whenToUse whenToUse
     * @since 0.1.7
     */
    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }

    /**
     * setContent.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * getScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getScore() {
        return score;
    }

    /**
     * setScore.
     * 
     * @param score score
     * @since 0.1.7
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * getCreatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * setCreatedAt.
     * 
     * @param createdAt createdAt
     * @since 0.1.7
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * getUpdatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * setUpdatedAt.
     * 
     * @param updatedAt updatedAt
     * @since 0.1.7
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ReMeMemoryMetadata getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(ReMeMemoryMetadata metadata) {
        this.metadata = metadata != null ? metadata : new ReMeMemoryMetadata();
    }
}
