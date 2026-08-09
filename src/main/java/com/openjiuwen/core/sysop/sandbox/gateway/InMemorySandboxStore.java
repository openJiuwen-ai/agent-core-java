/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mirrors Python's {@code InMemorySandboxStore} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/sandbox_store.py}.
 */
public final class InMemorySandboxStore extends AbstractSandboxStore {

    private final Map<String, SandboxRecord> records = new LinkedHashMap<>();

    @Override
    public Optional<SandboxRecord> get(String key) {
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public void set(String key, SandboxRecord record) {
        records.put(key, record);
    }

    @Override
    public Optional<SandboxRecord> hdel(String key) {
        return Optional.ofNullable(records.remove(key));
    }

    @Override
    public List<SandboxRecord> flushdb() {
        List<SandboxRecord> snapshot = new ArrayList<>(records.values());
        records.clear();
        return snapshot;
    }

    @Override
    public List<SandboxRecord> evictExpired(int idleTtlSeconds, double now) {
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, SandboxRecord> entry : records.entrySet()) {
            if ((now - entry.getValue().getLastUsedTs()) > idleTtlSeconds) {
                expiredKeys.add(entry.getKey());
            }
        }

        List<SandboxRecord> expiredRecords = new ArrayList<>(expiredKeys.size());
        for (String key : expiredKeys) {
            expiredRecords.add(records.remove(key));
        }
        return expiredRecords;
    }
}
