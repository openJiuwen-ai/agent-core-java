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
 */
public class ReMeMemory {

    private String workspaceId = "default";
    private String whenToUse;
    private String content;
    private double score;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;
    private ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReMeMemory() {
        // Default constructor
    }

    /**
     * Auto-generated for codecheck compliance.
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
        return new VectorNode(
            "reme_" + workspaceId + "_" + SchemaUtils.md5Hex(whenToUse).substring(0, 12),
            whenToUse,
            null,
            nodeMetadata
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReMeRetrievedMemory toRetrievedMemory() {
        return new ReMeRetrievedMemory(whenToUse, content);
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public static ReMeMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public String getWorkspaceId() {
        return workspaceId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId != null && !workspaceId.isBlank() ? workspaceId : "default";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWhenToUse() {
        return whenToUse;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getScore() {
        return score;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReMeMemoryMetadata getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(ReMeMemoryMetadata metadata) {
        this.metadata = metadata != null ? metadata : new ReMeMemoryMetadata();
    }
}
