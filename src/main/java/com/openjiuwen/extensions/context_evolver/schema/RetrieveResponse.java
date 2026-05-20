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
 */
public class RetrieveResponse {

    private final String status;
    private final String memoryString;
    private final List<?> retrievedMemory;

    /**
     * Auto-generated for codecheck compliance.
     */
    public RetrieveResponse(String status, String memoryString, List<?> retrievedMemory) {
        this.status = status;
        this.memoryString = memoryString;
        this.retrievedMemory = retrievedMemory != null ? new ArrayList<>(retrievedMemory) : List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("memory_string", memoryString);
        result.put("retrieved_memory", SchemaUtils.toPayload(retrievedMemory));
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMemoryString() {
        return memoryString;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<?> getRetrievedMemory() {
        return new ArrayList<>(retrievedMemory);
    }
}
