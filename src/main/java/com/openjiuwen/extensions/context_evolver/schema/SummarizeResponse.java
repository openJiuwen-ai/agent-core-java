/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.SummarizeResponse}.
 * 
 * @since 0.1.7
 */
public class SummarizeResponse {
    private final String status;
    private final List<?> memory;

    /**
     * SummarizeResponse.
     * 
     * @param status status
     * @param memory memory
     * @since 0.1.7
     */
    public SummarizeResponse(String status, List<?> memory) {
        this.status = status;
        this.memory = memory != null ? new ArrayList<>(memory) : List.of();
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("memory", SchemaUtils.toPayload(memory));
        return result;
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getStatus() {
        return status;
    }

    /**
     * getMemory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<?> getMemory() {
        return new ArrayList<>(memory);
    }
}
