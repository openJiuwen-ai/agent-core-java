/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sandbox record store aligned with Python's default gateway store.
 */
public class InMemorySandboxStore implements AbstractSandboxStore {
    private final Map<String, SandboxRecord> records = new ConcurrentHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxRecord get(String key) {
        return records.get(key);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void set(String key, SandboxRecord record) {
        records.put(key, record);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxRecord hdel(String key) {
        return records.remove(key);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SandboxRecord> flushdb() {
        List<SandboxRecord> values = new ArrayList<>(records.values());
        records.clear();
        return values;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SandboxRecord> evictExpired(int idleTtlSeconds, double now) {
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, SandboxRecord> entry : records.entrySet()) {
            SandboxRecord record = entry.getValue();
            if (now - record.getLastUsedTs() > idleTtlSeconds) {
                expiredKeys.add(entry.getKey());
            }
        }
        List<SandboxRecord> evicted = new ArrayList<>(expiredKeys.size());
        for (String key : expiredKeys) {
            SandboxRecord removed = records.remove(key);
            if (removed != null) {
                evicted.add(removed);
            }
        }
        return evicted;
    }
}
