/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility functions for model-pool refresh and identity inheritance.
 *
 * <p>Mirrors Python's module functions {@code _entry_signature} and
 * {@code inherit_pool_ids} in
 * {@code openjiuwen/agent_teams/models/pool.py}.</p>
 */
public final class ModelPoolSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private ModelPoolSupport() {
    }

    public static List<ModelPoolEntry> inheritPoolIds(
            List<ModelPoolEntry> currentPool,
            List<ModelPoolEntry> newPool
    ) {
        Map<String, List<ModelPoolEntry>> oldBySignature = new LinkedHashMap<>();
        for (ModelPoolEntry entry : nullToEmpty(currentPool)) {
            oldBySignature.computeIfAbsent(entrySignature(entry), ignored -> new ArrayList<>()).add(entry);
        }

        List<ModelPoolEntry> result = new ArrayList<>();
        for (ModelPoolEntry newEntry : nullToEmpty(newPool)) {
            List<ModelPoolEntry> bucket = oldBySignature.get(entrySignature(newEntry));
            if (bucket != null && !bucket.isEmpty()) {
                String inheritedId = bucket.remove(0).getModelId();
                result.add(newEntry.copyWithModelId(inheritedId));
            } else {
                result.add(newEntry);
            }
        }
        return result;
    }

    public static String entrySignature(ModelPoolEntry entry) {
        try {
            return OBJECT_MAPPER.writeValueAsString(canonicalize(entry.signaturePayload()));
        } catch (JsonProcessingException exc) {
            throw new IllegalStateException("Unable to serialize model pool entry signature", exc);
        }
    }

    static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return result;
    }

    static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> rawList) {
            List<Object> result = new ArrayList<>();
            for (Object item : rawList) {
                result.add(deepCopyValue(item));
            }
            return result;
        }
        return value;
    }

    private static List<ModelPoolEntry> nullToEmpty(List<ModelPoolEntry> entries) {
        return entries == null ? List.of() : entries;
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> sorted = new TreeMap<>(Comparator.naturalOrder());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> rawList) {
            List<Object> result = new ArrayList<>();
            for (Object item : rawList) {
                result.add(canonicalize(item));
            }
            return result;
        }
        return value;
    }
}
