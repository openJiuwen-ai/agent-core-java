/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema.memory;

import java.util.LinkedHashMap;
import java.util.Map;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

/**
 * Mirrors Python's {@code PersonalMemory} in
 * {@code openjiuwen/extensions/context_evolver/schema/memory.py}.
 */
public class PersonalMemory extends BaseMemory {

    private String target;
    private String reflectionSubject;

    public PersonalMemory() {
    }

    public PersonalMemory(String content, String target) {
        super(content, "default");
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getReflectionSubject() {
        return reflectionSubject;
    }

    public void setReflectionSubject(String reflectionSubject) {
        this.reflectionSubject = reflectionSubject;
    }

    @Override
    public VectorNode toVectorNode() {
        String normalizedContent = getContent() != null ? getContent() : "";
        String nodeId = "personal_" + getWorkspaceId() + "_" + SchemaUtils.md5Hex(normalizedContent);
        String embeddingContent = "About: " + target + "\n\nContent: " + normalizedContent;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "personal_memory");
        metadata.put("target", target);
        metadata.put("content", normalizedContent);
        metadata.put("workspace_id", getWorkspaceId());
        metadata.put("reflection_subject", reflectionSubject);

        return new VectorNode(nodeId, embeddingContent, null, metadata);
    }

    public static PersonalMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static PersonalMemory fromMap(Map<String, Object> data) {
        PersonalMemory memory = new PersonalMemory();
        memory.setContent(SchemaUtils.stringValue(data.get("content"), ""));
        memory.setWorkspaceId(SchemaUtils.stringValue(data.get("workspace_id"), "default"));
        memory.setTarget(SchemaUtils.stringValue(data.get("target"), ""));
        memory.setReflectionSubject(SchemaUtils.stringValue(data.get("reflection_subject"), null));
        return memory;
    }

    @Override
    public String toString() {
        String content = getContent() != null ? getContent() : "";
        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
        return "PersonalMemory(target='" + target + "', content='" + preview + "')";
    }
}
