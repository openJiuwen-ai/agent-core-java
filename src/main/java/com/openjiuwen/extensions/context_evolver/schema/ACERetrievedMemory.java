/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ACERetrievedMemory}.
 * 
 * @since 0.1.7
 */
public class ACERetrievedMemory {
    private String id;
    private String section;
    private String content;
    private int helpful;
    private int harmful;
    private int neutral;

    /**
     * ACERetrievedMemory.
     * 
     * @since 0.1.7
     */
    public ACERetrievedMemory() {
        // Default constructor
    }

    /**
     * ACERetrievedMemory.
     * 
     * @param id id
     * @param section section
     * @param content content
     * @param helpful helpful
     * @param harmful harmful
     * @param neutral neutral
     * @since 0.1.7
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
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
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
     * fromVectorNode.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    public static ACERetrievedMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static ACERetrievedMemory fromMap(Map<String, Object> data) {
        return new ACERetrievedMemory(SchemaUtils.stringValue(data.get("id"), ""),
                SchemaUtils.stringValue(data.get("section"), "general"),
                SchemaUtils.stringValue(data.get("content"), ""), SchemaUtils.intValue(data.get("helpful"), 0),
                SchemaUtils.intValue(data.get("harmful"), 0), SchemaUtils.intValue(data.get("neutral"), 0));
    }

    /**
     * getId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getId() {
        return id;
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
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }

    /**
     * getHelpful.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getHelpful() {
        return helpful;
    }

    /**
     * getHarmful.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getHarmful() {
        return harmful;
    }

    /**
     * getNeutral.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getNeutral() {
        return neutral;
    }
}
