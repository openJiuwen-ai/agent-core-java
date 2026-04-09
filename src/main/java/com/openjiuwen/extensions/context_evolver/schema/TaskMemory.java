  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.LinkedHashMap;
import java.util.Map;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.memory.TaskMemory}.
 * 
 * Task memory storing when and how to use knowledge.
 */
public class TaskMemory {
    private String content;
    private String workspaceId = "default";
    private String whenToUse;
    private int helpfulCount = 0;
    private int harmfulCount = 0;
    private String section = "general";
    
    public TaskMemory() {}
    
    public TaskMemory(String content, String whenToUse) {
        this.content = content;
        this.whenToUse = whenToUse;
    }
    
    // Getters and setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getWhenToUse() { return whenToUse; }
    public void setWhenToUse(String whenToUse) { this.whenToUse = whenToUse; }
    public int getHelpfulCount() { return helpfulCount; }
    public void setHelpfulCount(int helpfulCount) { this.helpfulCount = helpfulCount; }
    public int getHarmfulCount() { return harmfulCount; }
    public void setHarmfulCount(int harmfulCount) { this.harmfulCount = harmfulCount; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    
    /**
     * Convert to vector node for storage.
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
     */
    public static TaskMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

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
     */
    public double getScore() {
        return (double) (helpfulCount + 1) / (harmfulCount + 1);
    }
    
    @Override
    public String toString() {
        String preview = content != null && content.length() > 50 
            ? content.substring(0, 50) + "..." 
            : content;
        return String.format("TaskMemory(whenToUse='%s...', content='%s', score=%.2f)",
            whenToUse != null && whenToUse.length() > 30 ? whenToUse.substring(0, 30) : whenToUse,
            preview, getScore());
    }
}
