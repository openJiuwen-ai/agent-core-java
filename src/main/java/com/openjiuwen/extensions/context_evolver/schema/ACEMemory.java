  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ACEMemory}.
 */
public class ACEMemory {

    private String workspaceId = "default";
    private String id;
    private String section;
    private String content;
    private int helpful;
    private int harmful;
    private int neutral;
    private Instant createdAt;
    private Instant updatedAt;

    public ACEMemory() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public ACEMemory(String id, String section, String content) {
        this();
        this.id = id;
        this.section = section;
        this.content = content;
    }

    public static String generateId(String section, String content) {
        String normalizedSection = section != null && !section.isBlank() ? section : "general";
        return normalizedSection + "-" + SchemaUtils.md5Hex(content).substring(0, 8);
    }

    public VectorNode toVectorNode() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "ace_memory");
        metadata.put("id", id);
        metadata.put("section", section);
        metadata.put("content", content);
        metadata.put("helpful", helpful);
        metadata.put("harmful", harmful);
        metadata.put("neutral", neutral);
        metadata.put("created_at", createdAt.toString());
        metadata.put("updated_at", updatedAt.toString());
        metadata.put("workspace_id", workspaceId);
        return new VectorNode("ace_" + workspaceId + "_" + id, content, null, metadata);
    }

    public ACERetrievedMemory toRetrievedMemory() {
        return new ACERetrievedMemory(id, section, content, helpful, harmful, neutral);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("section", section);
        result.put("content", content);
        result.put("helpful", helpful);
        result.put("harmful", harmful);
        result.put("neutral", neutral);
        result.put("created_at", createdAt.toString());
        result.put("updated_at", updatedAt.toString());
        result.put("workspace_id", workspaceId);
        return result;
    }

    public static ACEMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static ACEMemory fromMap(Map<String, Object> data) {
        ACEMemory memory = new ACEMemory();
        memory.workspaceId = SchemaUtils.stringValue(data.get("workspace_id"), "default");
        memory.section = SchemaUtils.stringValue(data.get("section"), "general");
        memory.content = SchemaUtils.stringValue(data.get("content"), "");
        memory.id = SchemaUtils.stringValue(data.get("id"), generateId(memory.section, memory.content));
        memory.helpful = SchemaUtils.intValue(data.get("helpful"), 0);
        memory.harmful = SchemaUtils.intValue(data.get("harmful"), 0);
        memory.neutral = SchemaUtils.intValue(data.get("neutral"), 0);
        memory.createdAt = SchemaUtils.instantValue(data.get("created_at"), Instant.now());
        memory.updatedAt = SchemaUtils.instantValue(data.get("updated_at"), memory.createdAt);
        return memory;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId != null && !workspaceId.isBlank() ? workspaceId : "default";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section != null && !section.isBlank() ? section : "general";
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getHelpful() {
        return helpful;
    }

    public void setHelpful(int helpful) {
        this.helpful = helpful;
    }

    public int getHarmful() {
        return harmful;
    }

    public void setHarmful(int harmful) {
        this.harmful = harmful;
    }

    public int getNeutral() {
        return neutral;
    }

    public void setNeutral(int neutral) {
        this.neutral = neutral;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
