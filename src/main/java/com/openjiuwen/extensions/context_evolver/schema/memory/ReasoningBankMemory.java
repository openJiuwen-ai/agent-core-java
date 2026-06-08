/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema.memory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

/**
 * Mirrors Python's {@code ReasoningBankMemory} in
 * {@code openjiuwen/extensions/context_evolver/schema/memory.py}.
 */
public class ReasoningBankMemory extends BaseMemory {

    private String title;
    private String description;
    private String sourceType = "success";
    private int helpfulCount;
    private int harmfulCount;

    public ReasoningBankMemory() {
    }

    public ReasoningBankMemory(String content, String title, String description) {
        super(content, "default");
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType != null ? sourceType : "success";
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(int helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    public int getHarmfulCount() {
        return harmfulCount;
    }

    public void setHarmfulCount(int harmfulCount) {
        this.harmfulCount = harmfulCount;
    }

    @Override
    public VectorNode toVectorNode() {
        String normalizedTitle = title != null ? title : "";
        String normalizedDescription = description != null ? description : "";
        String normalizedContent = getContent() != null ? getContent() : "";
        String combined = normalizedTitle + "|" + normalizedContent;
        String nodeId = "reasoning_bank_" + getWorkspaceId() + "_" + SchemaUtils.md5Hex(combined);
        String embeddingContent = "Title: " + normalizedTitle
            + "\n\nDescription: " + normalizedDescription
            + "\n\nContent: " + normalizedContent;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reasoning_bank_memory");
        metadata.put("title", normalizedTitle);
        metadata.put("description", normalizedDescription);
        metadata.put("content", normalizedContent);
        metadata.put("workspace_id", getWorkspaceId());
        metadata.put("source_type", sourceType);
        metadata.put("helpful_count", helpfulCount);
        metadata.put("harmful_count", harmfulCount);

        return new VectorNode(nodeId, embeddingContent, null, metadata);
    }

    public static ReasoningBankMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static ReasoningBankMemory fromMap(Map<String, Object> data) {
        ReasoningBankMemory memory = new ReasoningBankMemory();
        memory.setContent(SchemaUtils.stringValue(data.get("content"), ""));
        memory.setWorkspaceId(SchemaUtils.stringValue(data.get("workspace_id"), "default"));
        memory.setTitle(SchemaUtils.stringValue(data.get("title"), ""));
        memory.setDescription(SchemaUtils.stringValue(data.get("description"), ""));
        memory.setSourceType(SchemaUtils.stringValue(data.get("source_type"), "success"));
        memory.setHelpfulCount(SchemaUtils.intValue(data.get("helpful_count"), 0));
        memory.setHarmfulCount(SchemaUtils.intValue(data.get("harmful_count"), 0));
        return memory;
    }

    public double getScore() {
        return (double) (helpfulCount + 1) / (harmfulCount + 1);
    }

    @Override
    public String toString() {
        return String.format(
            Locale.ROOT,
            "ReasoningBankMemory(title='%s', source='%s', score=%.2f)",
            title,
            sourceType,
            getScore()
        );
    }
}
