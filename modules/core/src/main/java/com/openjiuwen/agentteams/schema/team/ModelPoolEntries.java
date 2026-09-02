/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ModelPoolEntries.
 * 
 * @since 0.1.7
 */
public final class ModelPoolEntries {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * ModelPoolEntries.
     * 
     * @since 0.1.7
     */
    private ModelPoolEntries() {
    }

    /**
     * inheritPoolIds.
     * 
     * @param currentPool currentPool
     * @param newPool newPool
     * @return the result
     * @since 0.1.7
     */
    public static List<ModelPoolEntry> inheritPoolIds(List<ModelPoolEntry> currentPool, List<ModelPoolEntry> newPool) {
        Map<String, List<ModelPoolEntry>> oldBySignature = new LinkedHashMap<>();
        List<ModelPoolEntry> currentEntries = currentPool == null ? List.of() : currentPool;
        List<ModelPoolEntry> newEntries = newPool == null ? List.of() : newPool;
        for (ModelPoolEntry entry : currentEntries) {
            oldBySignature.computeIfAbsent(entrySignature(entry), key -> new ArrayList<>()).add(entry);
        }

        List<ModelPoolEntry> result = new ArrayList<>();
        for (ModelPoolEntry newEntry : newEntries) {
            List<ModelPoolEntry> bucket = oldBySignature.get(entrySignature(newEntry));
            if (bucket != null && !bucket.isEmpty()) {
                result.add(newEntry.toBuilder().modelId(bucket.remove(0).getModelId()).build());
            } else {
                result.add(newEntry);
            }
        }
        return result;
    }

    /**
     * entrySignature.
     * 
     * @param entry entry
     * @return the result
     * @since 0.1.7
     */
    private static String entrySignature(ModelPoolEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", entry.getProvider());
        payload.put("modelName", entry.getModelName());
        payload.put("apiKey", entry.getApiKey());
        payload.put("apiBaseUrl", entry.getApiBaseUrl());
        payload.put("description", entry.getDescription());
        payload.put("metadata", entry.getMetadata());
        payload.put("weight", entry.getWeight());
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize model pool entry signature", e);
        }
    }
}
