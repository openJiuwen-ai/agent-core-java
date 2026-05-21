/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of sandbox store.
 * <p>
 * Uses a ConcurrentHashMap for thread-safe storage of sandbox records.
 * <p>
 * Mirrors Python's {@code InMemorySandboxStore} in {@code sandbox/gateway/sandbox_store.py}.
 */
public class InMemorySandboxStore extends AbstractSandboxStore {

    private final Map<String, SandboxRecord> records = new ConcurrentHashMap<>();

    @Override
    public SandboxRecord get(String key) {
        return records.get(key);
    }

    @Override
    public void set(String key, SandboxRecord record) {
        records.put(key, record);
    }

    @Override
    public SandboxRecord hdel(String key) {
        return records.remove(key);
    }

    @Override
    public List<SandboxRecord> flushdb() {
        List<SandboxRecord> allRecords = new ArrayList<>(records.values());
        records.clear();
        return allRecords;
    }

    @Override
    public List<SandboxRecord> evictExpired(int idleTtlSeconds, double now) {
        List<SandboxRecord> evicted = new ArrayList<>();
        List<String> keysToRemove = new ArrayList<>();
        
        for (Map.Entry<String, SandboxRecord> entry : records.entrySet()) {
            double idleTime = now - entry.getValue().getLastUsedTs();
            if (idleTime > idleTtlSeconds) {
                keysToRemove.add(entry.getKey());
            }
        }
        
        for (String key : keysToRemove) {
            SandboxRecord removed = records.remove(key);
            if (removed != null) {
                evicted.add(removed);
            }
        }
        
        return evicted;
    }
}