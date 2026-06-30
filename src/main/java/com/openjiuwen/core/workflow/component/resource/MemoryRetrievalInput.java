/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import lombok.Data;

import java.util.Map;

/**
 * Input model for the Memory Retrieval component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalInput}.
 *
 * @since 1.0.0
 */
@Data
public class MemoryRetrievalInput {
    private String query;
    private int topK = 5;

    /**
     * Convert from a map representation to MemoryRetrievalInput.
     *
     * @param inputs the input map
     * @return the MemoryRetrievalInput instance
     */
    public static MemoryRetrievalInput fromMap(Map<String, Object> inputs) {
        MemoryRetrievalInput input = new MemoryRetrievalInput();
        if (inputs != null) {
            if (inputs.containsKey("query")) {
                Object q = inputs.get("query");
                input.setQuery(q != null ? q.toString() : "");
            }
            if (inputs.containsKey("top_k")) {
                Object topK = inputs.get("top_k");
                if (topK instanceof Number) {
                    input.setTopK(((Number) topK).intValue());
                }
            }
        }
        return input;
    }
}
