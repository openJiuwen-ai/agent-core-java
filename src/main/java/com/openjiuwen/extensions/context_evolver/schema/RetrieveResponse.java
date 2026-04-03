// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

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

    public RetrieveResponse(String status, String memoryString, List<?> retrievedMemory) {
        this.status = status;
        this.memoryString = memoryString;
        this.retrievedMemory = retrievedMemory != null ? new ArrayList<>(retrievedMemory) : List.of();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("memory_string", memoryString);
        result.put("retrieved_memory", SchemaUtils.toPayload(retrievedMemory));
        return result;
    }

    public String getStatus() {
        return status;
    }

    public String getMemoryString() {
        return memoryString;
    }

    public List<?> getRetrievedMemory() {
        return new ArrayList<>(retrievedMemory);
    }
}
