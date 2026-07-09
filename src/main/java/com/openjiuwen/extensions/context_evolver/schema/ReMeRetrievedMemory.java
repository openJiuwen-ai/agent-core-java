/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReMeRetrievedMemory}.
 * 
 * @since 0.1.7
 */
public class ReMeRetrievedMemory {
    private String whenToUse;
    private String content;

    /**
     * ReMeRetrievedMemory.
     * 
     * @since 0.1.7
     */
    public ReMeRetrievedMemory() {
        // Default constructor
    }

    /**
     * ReMeRetrievedMemory.
     * 
     * @param whenToUse whenToUse
     * @param content content
     * @since 0.1.7
     */
    public ReMeRetrievedMemory(String whenToUse, String content) {
        this.whenToUse = whenToUse;
        this.content = content;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("when_to_use", whenToUse);
        result.put("content", content);
        return result;
    }

    /**
     * fromVectorNode.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    public static ReMeRetrievedMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static ReMeRetrievedMemory fromMap(Map<String, Object> data) {
        return new ReMeRetrievedMemory(SchemaUtils.stringValue(data.get("when_to_use"), ""),
                SchemaUtils.stringValue(data.get("content"), ""));
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
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }
}
