/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single-endpoint router allocator.
 *
 * <p>Mirrors Python's {@code RouterAllocator} in
 * {@code openjiuwen/agent_teams/models/allocator.py}.</p>
 */
public class RouterAllocator implements ModelAllocator {

    private final List<ModelPoolEntry> pool;
    private final Map<String, ModelPoolEntry> byName = new LinkedHashMap<>();
    private final String poolDigest;

    public RouterAllocator(List<ModelPoolEntry> pool) {
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("RouterAllocator requires a non-empty pool");
        }
        List<String> names = new ArrayList<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (ModelPoolEntry entry : pool) {
            if (names.contains(entry.getModelName())) {
                duplicates.add(entry.getModelName());
            }
            names.add(entry.getModelName());
        }
        if (!duplicates.isEmpty()) {
            List<String> sorted = new ArrayList<>(duplicates);
            sorted.sort(Comparator.naturalOrder());
            throw new IllegalArgumentException(
                    "RouterAllocator pool must have unique model_names; duplicates: " + sorted);
        }
        this.pool = new ArrayList<>(pool);
        for (ModelPoolEntry entry : this.pool) {
            byName.put(entry.getModelName(), entry);
        }
        this.poolDigest = ModelAllocators.poolDigest(this.pool);
    }

    @Override
    public Allocation allocate(String modelName) {
        if (modelName == null) {
            return new Allocation(pool.get(0), 0);
        }
        ModelPoolEntry entry = byName.get(modelName);
        return entry == null ? null : new Allocation(entry, 0);
    }

    @Override
    public Map<String, Object> stateDict() {
        return Map.of("pool_digest", poolDigest);
    }

    @Override
    public void loadStateDict(Map<String, Object> state) {
        // No rotating counter exists for router allocation.
    }
}
