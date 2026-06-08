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
 * Mirrors Python's {@code TaskMemory} in
 * {@code openjiuwen/extensions/context_evolver/schema/memory.py}.
 */
public class TaskMemory extends BaseMemory {

    private String whenToUse;
    private int helpfulCount;
    private int harmfulCount;
    private String section = "general";

    public TaskMemory() {
    }

    public TaskMemory(String content, String whenToUse) {
        super(content, "default");
        this.whenToUse = whenToUse;
    }

    public String getWhenToUse() {
        return whenToUse;
    }

    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
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

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section != null ? section : "general";
    }

    @Override
    public VectorNode toVectorNode() {
        String normalizedContent = getContent() != null ? getContent() : "";
        String normalizedWhenToUse = whenToUse != null ? whenToUse : "";
        String nodeId = "task_" + getWorkspaceId() + "_" + SchemaUtils.md5Hex(normalizedContent);
        String embeddingContent = "When to use: " + normalizedWhenToUse + "\n\nContent: " + normalizedContent;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "task_memory");
        metadata.put("when_to_use", normalizedWhenToUse);
        metadata.put("content", normalizedContent);
        metadata.put("workspace_id", getWorkspaceId());
        metadata.put("helpful_count", helpfulCount);
        metadata.put("harmful_count", harmfulCount);
        metadata.put("section", section);

        return new VectorNode(nodeId, embeddingContent, null, metadata);
    }

    public static TaskMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static TaskMemory fromMap(Map<String, Object> data) {
        TaskMemory memory = new TaskMemory();
        memory.setContent(SchemaUtils.stringValue(data.get("content"), ""));
        memory.setWorkspaceId(SchemaUtils.stringValue(data.get("workspace_id"), "default"));
        memory.setWhenToUse(SchemaUtils.stringValue(data.get("when_to_use"), ""));
        memory.setHelpfulCount(SchemaUtils.intValue(data.get("helpful_count"), 0));
        memory.setHarmfulCount(SchemaUtils.intValue(data.get("harmful_count"), 0));
        memory.setSection(SchemaUtils.stringValue(data.get("section"), "general"));
        return memory;
    }

    public double getScore() {
        return (double) (helpfulCount + 1) / (harmfulCount + 1);
    }

    @Override
    public String toString() {
        String content = getContent() != null ? getContent() : "";
        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
        String whenPreview;
        if (whenToUse == null) {
            whenPreview = "null";
        } else {
            whenPreview = whenToUse.length() > 30 ? whenToUse.substring(0, 30) : whenToUse;
        }
        return String.format(
            Locale.ROOT,
            "TaskMemory(when_to_use='%s...', content='%s', score=%.2f)",
            whenPreview,
            preview,
            getScore()
        );
    }
}
