/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ACERetrievedMemory}.
 */
public class ACERetrievedMemory {

    private String id;
    private String section;
    private String content;
    private int helpful;
    private int harmful;
    private int neutral;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ACERetrievedMemory() {
        // Default constructor
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ACERetrievedMemory(String id, String section, String content, int helpful, int harmful, int neutral) {
        this.id = id;
        this.section = section;
        this.content = content;
        this.helpful = helpful;
        this.harmful = harmful;
        this.neutral = neutral;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("section", section);
        result.put("content", content);
        result.put("helpful", helpful);
        result.put("harmful", harmful);
        result.put("neutral", neutral);
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ACERetrievedMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ACERetrievedMemory fromMap(Map<String, Object> data) {
        return new ACERetrievedMemory(
            SchemaUtils.stringValue(data.get("id"), ""),
            SchemaUtils.stringValue(data.get("section"), "general"),
            SchemaUtils.stringValue(data.get("content"), ""),
            SchemaUtils.intValue(data.get("helpful"), 0),
            SchemaUtils.intValue(data.get("harmful"), 0),
            SchemaUtils.intValue(data.get("neutral"), 0)
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getId() {
        return id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSection() {
        return section;
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
    public int getHelpful() {
        return helpful;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getHarmful() {
        return harmful;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getNeutral() {
        return neutral;
    }
}
