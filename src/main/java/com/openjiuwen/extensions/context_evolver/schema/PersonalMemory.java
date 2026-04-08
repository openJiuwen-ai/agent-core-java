/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.LinkedHashMap;
import java.util.Map;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.memory.PersonalMemory}.
 * 
 * Personal memory about user preferences and context.
 */
public class PersonalMemory {
    private String content;
    private String workspaceId = "default";
    private String target;
    private String reflectionSubject;
    
    public PersonalMemory() {}
    
    public PersonalMemory(String content, String target) {
        this.content = content;
        this.target = target;
    }
    
    // Getters and setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getReflectionSubject() { return reflectionSubject; }
    public void setReflectionSubject(String reflectionSubject) { this.reflectionSubject = reflectionSubject; }
    
    /**
     * Convert to vector node for storage.
     */
    public VectorNode toVectorNode() {
        String normalizedContent = content != null ? content : "";
        String contentHash = SchemaUtils.md5Hex(normalizedContent);
        String nodeId = "personal_" + workspaceId + "_" + contentHash;
        
        String embeddingContent = "About: " + target + "\n\nContent: " + normalizedContent;
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "personal_memory");
        metadata.put("target", target);
        metadata.put("content", normalizedContent);
        metadata.put("workspace_id", workspaceId);
        metadata.put("reflection_subject", reflectionSubject);
        
        return new VectorNode(nodeId, embeddingContent, null, metadata);
    }
    
    /**
     * Convert from vector node.
     */
    public static PersonalMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static PersonalMemory fromMap(Map<String, Object> data) {
        PersonalMemory memory = new PersonalMemory();
        memory.content = SchemaUtils.stringValue(data.get("content"), "");
        memory.workspaceId = SchemaUtils.stringValue(data.get("workspace_id"), "default");
        memory.target = SchemaUtils.stringValue(data.get("target"), "");
        memory.reflectionSubject = SchemaUtils.stringValue(data.get("reflection_subject"), null);
        return memory;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("workspace_id", workspaceId);
        result.put("target", target);
        result.put("reflection_subject", reflectionSubject);
        return result;
    }

    @Override
    public String toString() {
        String preview = content != null && content.length() > 50
            ? content.substring(0, 50) + "..."
            : content;
        return "PersonalMemory(target='" + target + "', content='" + preview + "')";
    }
}
