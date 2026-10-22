/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link ConcurrentHashMap} store; entries live with the JVM
 * (架构文档 §3.1). TTL is not enforced here — expiry is owned by the
 * catalog status and the Janitor, and every load path consults the catalog
 * before touching a store.
 *
 * @since 2026-01-01
 */
public class MemoryOffloadStore implements OffloadStore {
    private final ConcurrentHashMap<String, OffloadPayload> entries = new ConcurrentHashMap<>();

    @Override
    public OffloadBackend backend() {
        return OffloadBackend.MEMORY;
    }

    @Override
    public void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl) {
        Objects.requireNonNull(ns, "ns must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        entries.put(ns.toRedisKey(handle), payload);
    }

    @Override
    public OffloadPayload load(OffloadNamespace ns, String handle) {
        OffloadPayload payload = entries.get(ns.toRedisKey(handle));
        if (payload == null) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload entry not found: " + handle);
        }
        return payload;
    }

    @Override
    public boolean delete(OffloadNamespace ns, String handle) {
        return entries.remove(ns.toRedisKey(handle)) != null;
    }

    @Override
    public boolean exists(OffloadNamespace ns, String handle) {
        return entries.containsKey(ns.toRedisKey(handle));
    }
}
