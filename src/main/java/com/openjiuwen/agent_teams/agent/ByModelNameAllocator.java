/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup-by-name allocator with intra-group round-robin.
 *
 * <p>Mirrors Python's {@code ByModelNameAllocator} in
 * {@code openjiuwen.agent_teams.agent.model_allocator}.</p>
 */
public class ByModelNameAllocator implements ModelAllocator {

    private final Map<String, List<ModelPoolEntry>> groups = new LinkedHashMap<>();
    private final Map<String, Integer> innerIndexes = new LinkedHashMap<>();
    private final String poolDigest;

    public ByModelNameAllocator(List<ModelPoolEntry> pool) {
        List<ModelPoolEntry> entries = pool != null ? pool : List.of();
        for (ModelPoolEntry entry : entries) {
            groups.computeIfAbsent(entry.getModelName(), ignored -> new ArrayList<>()).add(entry);
        }
        groups.keySet().forEach(name -> innerIndexes.put(name, 0));
        this.poolDigest = ModelAllocators.poolDigest(entries);
    }

    @Override
    public Allocation allocate(String modelName) {
        if (modelName == null || modelName.isBlank() || !groups.containsKey(modelName)) {
            return null;
        }
        List<ModelPoolEntry> group = groups.get(modelName);
        int index = innerIndexes.getOrDefault(modelName, 0) % group.size();
        innerIndexes.put(modelName, innerIndexes.getOrDefault(modelName, 0) + 1);
        return new Allocation(group.get(index), index);
    }

    @Override
    public Map<String, Object> stateDict() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("inner_indexes", new LinkedHashMap<>(innerIndexes));
        state.put("pool_digest", poolDigest);
        return state;
    }

    @Override
    public void loadStateDict(Map<String, Object> state) {
        if (state == null || !poolDigest.equals(state.get("pool_digest"))) {
            resetCounters();
            return;
        }
        Object rawInner = state.get("inner_indexes");
        if (!(rawInner instanceof Map<?, ?> rawMap)) {
            return;
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!innerIndexes.containsKey(name)) {
                continue;
            }
            innerIndexes.put(name, ModelAllocators.toInt(entry.getValue(), 0));
        }
    }

    private void resetCounters() {
        innerIndexes.replaceAll((ignored, previous) -> 0);
    }
}
