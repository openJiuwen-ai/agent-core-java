/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.ReMeRetrievedMemory}.
 */
public class ReMeRetrievedMemory {

    private String whenToUse;
    private String content;

    public ReMeRetrievedMemory() {
        // Default constructor
    }

    public ReMeRetrievedMemory(String whenToUse, String content) {
        this.whenToUse = whenToUse;
        this.content = content;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("when_to_use", whenToUse);
        result.put("content", content);
        return result;
    }

    public static ReMeRetrievedMemory fromVectorNode(VectorNode node) {
        return fromMap(node.getMetadata());
    }

    public static ReMeRetrievedMemory fromMap(Map<String, Object> data) {
        return new ReMeRetrievedMemory(
            SchemaUtils.stringValue(data.get("when_to_use"), ""),
            SchemaUtils.stringValue(data.get("content"), "")
        );
    }

    public String getWhenToUse() {
        return whenToUse;
    }

    public String getContent() {
        return content;
    }
}
