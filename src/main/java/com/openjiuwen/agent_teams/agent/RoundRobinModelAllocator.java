/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Round-robin allocator over a TeamSpec.model_pool.
 * <p>
 * Each call to allocate returns the next entry in pool order and
 * wraps when the end is reached, so a team with N members and M pool
 * entries spreads members evenly across endpoints.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.agent.model_allocator.RoundRobinModelAllocator}.
 */
public class RoundRobinModelAllocator implements ModelAllocator {

    private final List<ModelPoolEntry> pool;
    private final String poolDigest;
    private int index;

    // Pre-compute name → list-of-entries for group_index lookups
    private final Map<String, List<ModelPoolEntry>> groups;

    /**
     * Initialize with the pool entries to rotate over.
     *
     * @param pool Pool entries
     */
    public RoundRobinModelAllocator(List<ModelPoolEntry> pool) {
        this.pool = pool != null ? new ArrayList<>(pool) : new ArrayList<>();
        this.poolDigest = computePoolDigest(this.pool);
        this.index = 0;
        this.groups = new LinkedHashMap<>();

        for (ModelPoolEntry entry : this.pool) {
            groups.computeIfAbsent(entry.getModelName(), k -> new ArrayList<>()).add(entry);
        }
    }

    @Override
    public Allocation allocate(String modelName) {
        // Round-robin is name-agnostic
        if (pool.isEmpty()) {
            return null;
        }
        ModelPoolEntry entry = pool.get(index % pool.size());
        index++;
        List<ModelPoolEntry> group = groups.getOrDefault(entry.getModelName(), Arrays.asList(entry));
        int groupIdx = groupIndexOf(entry, group);
        return new Allocation(entry, groupIdx);
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
        if (state == null) {
            index = 0;
            return;
        }
        String digest = (String) state.get("pool_digest");
        if (!poolDigest.equals(digest)) {
            index = 0;
            return;
        }
        try {
            Object rawIndex = state.get("index");
            if (rawIndex instanceof Number) {
                index = ((Number) rawIndex).intValue();
            } else {
                index = 0;
            }
        } catch (Exception e) {
            index = 0;
        }
    }

    /**
     * Compute stable digest of a pool's structural shape.
     */
    private static String computePoolDigest(List<ModelPoolEntry> pool) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (ModelPoolEntry entry : pool) {
                md.update(entry.getModelName().getBytes());
                md.update((byte) 0);
                md.update(entry.getApiBaseUrl().getBytes());
                md.update((byte) 0x1f);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * Return entry's position within group by reference identity.
     */
    private static int groupIndexOf(ModelPoolEntry entry, List<ModelPoolEntry> group) {
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i) == entry) {
                return i;
            }
        }
        return 0;
    }
}