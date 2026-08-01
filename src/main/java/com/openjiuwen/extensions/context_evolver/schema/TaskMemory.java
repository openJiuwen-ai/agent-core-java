/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.memory.TaskMemory}.
 * Task memory storing when and how to use knowledge.
 * 
 * @since 0.1.7
 */
public class TaskMemory {
    private String content;
    private String workspaceId = "default";
    private String whenToUse;
    private int helpfulCount = 0;
    private int harmfulCount = 0;
    private String section = "general";

    /**
     * TaskMemory.
     * 
     * @since 0.1.7
     */
    public TaskMemory() {
    }

    /**
     * TaskMemory.
     * 
     * @param content content
     * @param whenToUse whenToUse
     * @since 0.1.7
     */
    public TaskMemory(String content, String whenToUse) {
        this.content = content;
        this.whenToUse = whenToUse;
    }

    // Getters and setters
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
        this.workspaceId = workspaceId;
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
     * getHelpfulCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getHelpfulCount() {
        return helpfulCount;
    }

    /**
     * setHelpfulCount.
     * 
     * @param helpfulCount helpfulCount
     * @since 0.1.7
     */
    public void setHelpfulCount(int helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    /**
     * getHarmfulCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getHarmfulCount() {
        return harmfulCount;
    }

    /**
     * setHarmfulCount.
     * 
     * @param harmfulCount harmfulCount
     * @since 0.1.7
     */
    public void setHarmfulCount(int harmfulCount) {
        this.harmfulCount = harmfulCount;
    }

    /**
     * getSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSection() {
        return section;
    }

    /**
     * setSection.
     * 
     * @param section section
     * @since 0.1.7
     */
    public void setSection(String section) {
        this.section = section;
    }

    /**
     * Convert to vector node for storage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorNode toVectorNode() {
        String normalizedContent = content != null ? content : "";
        String contentHash = SchemaUtils.md5Hex(normalizedContent);
        String nodeId = "task_" + workspaceId + "_" + contentHash;

        String embeddingContent = "When to use: " + whenToUse + "\n\nContent: " + normalizedContent;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "task_memory");
        metadata.put("when_to_use", whenToUse);
        metadata.put("content", normalizedContent);
        metadata.put("workspace_id", workspaceId);
        metadata.put("helpful_count", helpfulCount);
        metadata.put("harmful_count", harmfulCount);
        metadata.put("section", section);

        return new VectorNode(nodeId, embeddingContent, null, metadata);
    }

    /**
     * Convert from vector node.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    public static TaskMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static TaskMemory fromMap(Map<String, Object> data) {
        TaskMemory memory = new TaskMemory();
        memory.content = SchemaUtils.stringValue(data.get("content"), "");
        memory.workspaceId = SchemaUtils.stringValue(data.get("workspace_id"), "default");
        memory.whenToUse = SchemaUtils.stringValue(data.get("when_to_use"), "");
        memory.helpfulCount = SchemaUtils.intValue(data.get("helpful_count"), 0);
        memory.harmfulCount = SchemaUtils.intValue(data.get("harmful_count"), 0);
        memory.section = SchemaUtils.stringValue(data.get("section"), "general");
        return memory;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("workspace_id", workspaceId);
        result.put("when_to_use", whenToUse);
        result.put("helpful_count", helpfulCount);
        result.put("harmful_count", harmfulCount);
        result.put("section", section);
        return result;
    }

    /**
     * Calculate relevance score based on feedback.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getScore() {
        return (double) (helpfulCount + 1) / (harmfulCount + 1);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        String preview = content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content;
        return String.format("TaskMemory(whenToUse='%s...', content='%s', score=%.2f)",
                whenToUse != null && whenToUse.length() > 30 ? whenToUse.substring(0, 30) : whenToUse, preview,
                getScore());
    }
}
