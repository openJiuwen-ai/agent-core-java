/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lookup-by-name allocator with intra-group round-robin counters.
 *
 * <p>Mirrors Python's {@code ByModelNameAllocator} in
 * {@code openjiuwen/agent_teams/models/allocator.py}.</p>
 */
public class ByModelNameAllocator implements ModelAllocator {

    private final Map<String, List<ModelPoolEntry>> groups = new LinkedHashMap<>();
    private final String poolDigest;
    private final Map<String, AtomicInteger> innerIndexes = new ConcurrentHashMap<>();

    public ByModelNameAllocator(List<ModelPoolEntry> pool) {
        List<ModelPoolEntry> safePool = pool == null ? List.of() : pool;
        for (ModelPoolEntry entry : safePool) {
            groups.computeIfAbsent(entry.getModelName(), ignored -> new ArrayList<>()).add(entry);
        }
        this.poolDigest = ModelAllocators.poolDigest(safePool);
        resetIndexes();
    }

    @Override
    public Allocation allocate(String modelName) {
        if (modelName == null || modelName.isBlank() || !groups.containsKey(modelName)) {
            return null;
        }
        List<ModelPoolEntry> group = groups.get(modelName);
        AtomicInteger counter = innerIndexes.computeIfAbsent(modelName, ignored -> new AtomicInteger());
        int idx = Math.floorMod(counter.getAndIncrement(), group.size());
        return new Allocation(group.get(idx), idx);
    }

    @Override
    public Map<String, Object> stateDict() {
        List<Map<String, Object>> counters = new ArrayList<>();
        for (String modelName : groups.keySet()) {
            AtomicInteger counter = innerIndexes.get(modelName);
            if (counter == null) {
                continue;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("model_name", modelName);
            record.put("index", counter.get());
            counters.add(record);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("counters", counters);
        state.put("pool_digest", poolDigest);
        return state;
    }

    @Override
    public void loadStateDict(Map<String, Object> state) {
        if (state == null || !poolDigest.equals(state.get("pool_digest"))) {
            resetIndexes();
            return;
        }
        Object counters = state.get("counters");
        if (counters instanceof List<?> records) {
            loadCounterRecords(records);
            return;
        }
        Object legacy = state.get("inner_indexes");
        if (legacy instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (!innerIndexes.containsKey(name)) {
                    continue;
                }
                innerIndexes.get(name).set(parseInt(entry.getValue(), 0));
            }
        }
    }

    private void loadCounterRecords(List<?> records) {
        for (Object item : records) {
            if (!(item instanceof Map<?, ?> record)) {
                continue;
            }
            Object nameValue = record.get("model_name");
            if (nameValue == null) {
                continue;
            }
            String name = String.valueOf(nameValue);
            if (!innerIndexes.containsKey(name)) {
                continue;
            }
            innerIndexes.get(name).set(parseInt(record.get("index"), 0));
        }
    }

    private void resetIndexes() {
        innerIndexes.clear();
        for (String name : groups.keySet()) {
            innerIndexes.put(name, new AtomicInteger());
        }
    }

    private static int parseInt(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exc) {
            return fallback;
        }
    }
}
