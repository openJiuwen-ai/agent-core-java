/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.memory.PersonalMemory}.
 * Personal memory about user preferences and context.
 * 
 * @since 0.1.7
 */
public class PersonalMemory {
    private String content;
    private String workspaceId = "default";
    private String target;
    private String reflectionSubject;

    /**
     * PersonalMemory.
     * 
     * @since 0.1.7
     */
    public PersonalMemory() {
    }

    /**
     * PersonalMemory.
     * 
     * @param content content
     * @param target target
     * @since 0.1.7
     */
    public PersonalMemory(String content, String target) {
        this.content = content;
        this.target = target;
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
     * getTarget.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTarget() {
        return target;
    }

    /**
     * setTarget.
     * 
     * @param target target
     * @since 0.1.7
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * getReflectionSubject.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReflectionSubject() {
        return reflectionSubject;
    }

    /**
     * setReflectionSubject.
     * 
     * @param reflectionSubject reflectionSubject
     * @since 0.1.7
     */
    public void setReflectionSubject(String reflectionSubject) {
        this.reflectionSubject = reflectionSubject;
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
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    public static PersonalMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static PersonalMemory fromMap(Map<String, Object> data) {
        PersonalMemory memory = new PersonalMemory();
        memory.content = SchemaUtils.stringValue(data.get("content"), "");
        memory.workspaceId = SchemaUtils.stringValue(data.get("workspace_id"), "default");
        memory.target = SchemaUtils.stringValue(data.get("target"), "");
        memory.reflectionSubject = SchemaUtils.stringValue(data.get("reflection_subject"), null);
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
        result.put("target", target);
        result.put("reflection_subject", reflectionSubject);
        return result;
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
        return "PersonalMemory(target='" + target + "', content='" + preview + "')";
    }
}
