/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.io_schema.RetrieveResponse}.
 * 
 * @since 0.1.7
 */
public class RetrieveResponse {
    private final String status;
    private final String memoryString;
    private final List<?> retrievedMemory;

    /**
     * RetrieveResponse.
     * 
     * @param status status
     * @param memoryString memoryString
     * @param retrievedMemory retrievedMemory
     * @since 0.1.7
     */
    public RetrieveResponse(String status, String memoryString, List<?> retrievedMemory) {
        this.status = status;
        this.memoryString = memoryString;
        this.retrievedMemory = retrievedMemory != null ? new ArrayList<>(retrievedMemory) : List.of();
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
        result.put("memory_string", memoryString);
        result.put("retrieved_memory", SchemaUtils.toPayload(retrievedMemory));
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
     * getMemoryString.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMemoryString() {
        return memoryString;
    }

    /**
     * getRetrievedMemory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<?> getRetrievedMemory() {
        return new ArrayList<>(retrievedMemory);
    }
}
