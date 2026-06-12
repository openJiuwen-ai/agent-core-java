/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Round-robin allocator over a model pool.
 *
 * <p>Mirrors Python's {@code RoundRobinModelAllocator} in
 * {@code openjiuwen/agent_teams/models/allocator.py}.</p>
 */
public class RoundRobinModelAllocator implements ModelAllocator {

    private final List<ModelPoolEntry> pool;
    private final String poolDigest;
    private final Map<String, List<ModelPoolEntry>> groups = new LinkedHashMap<>();
    private int index;

    public RoundRobinModelAllocator(List<ModelPoolEntry> pool) {
        this.pool = pool == null ? List.of() : new ArrayList<>(pool);
        this.poolDigest = ModelAllocators.poolDigest(this.pool);
        for (ModelPoolEntry entry : this.pool) {
            groups.computeIfAbsent(entry.getModelName(), ignored -> new ArrayList<>()).add(entry);
        }
    }

    @Override
    public Allocation allocate(String modelName) {
        if (pool.isEmpty()) {
            return null;
        }
        ModelPoolEntry entry = pool.get(Math.floorMod(index, pool.size()));
        index += 1;
        List<ModelPoolEntry> group = groups.getOrDefault(entry.getModelName(), List.of(entry));
        return new Allocation(entry, groupIndexOf(entry, group));
    }

    @Override
    public Map<String, Object> stateDict() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("index", index);
        state.put("pool_digest", poolDigest);
        return state;
    }

    @Override
    public void loadStateDict(Map<String, Object> state) {
        if (state == null || !poolDigest.equals(state.get("pool_digest"))) {
            index = 0;
            return;
        }
        try {
            index = Integer.parseInt(String.valueOf(state.getOrDefault("index", 0)));
        } catch (NumberFormatException exc) {
            index = 0;
        }
    }

    private static int groupIndexOf(ModelPoolEntry entry, List<ModelPoolEntry> group) {
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i) == entry) {
                return i;
            }
        }
        return 0;
    }
}
