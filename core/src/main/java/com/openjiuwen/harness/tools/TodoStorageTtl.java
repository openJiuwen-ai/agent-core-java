/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.spi.store.BaseKVStore;

import java.time.Duration;
import java.util.Map;

/**
 * Parsed, per-Todo expiry settings. Never changes a shared store's defaults.
 *
 * @since 0.1.15
 */
final class TodoStorageTtl {
    private final Duration ttl;
    private final boolean shouldRefreshOnRead;

    private TodoStorageTtl(Duration ttl, boolean shouldRefreshOnRead) {
        this.ttl = ttl;
        this.shouldRefreshOnRead = shouldRefreshOnRead;
    }

    static TodoStorageTtl from(Map<String, Object> conf, Duration inheritedTtl, boolean shouldInheritRefresh) {
        Object raw = conf == null ? null : conf.get("ttl");
        if (raw == null || (raw instanceof Map<?, ?> map && map.isEmpty())) {
            return new TodoStorageTtl(inheritedTtl, shouldInheritRefresh);
        }
        if (!(raw instanceof Map<?, ?> settings)
                || !(settings.get("default_ttl") instanceof Number minutes)) {
            throw new IllegalArgumentException("Todo ttl.default_ttl must be a positive number of minutes");
        }
        double seconds = Math.ceil(minutes.doubleValue() * 60);
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Todo ttl.default_ttl is outside the supported range");
        }
        Object refresh = settings.get("refresh_on_read");
        if (refresh != null && !(refresh instanceof Boolean)) {
            throw new IllegalArgumentException("Todo ttl.refresh_on_read must be a boolean");
        }
        return new TodoStorageTtl(Duration.ofSeconds((long) seconds), Boolean.TRUE.equals(refresh));
    }

    KvTodoStorage create(BaseKVStore store) {
        return new KvTodoStorage(store, ttl, shouldRefreshOnRead);
    }

    static void rejectUnsupported(Map<String, Object> conf) {
        TodoStorageTtl settings = from(conf, null, false);
        if (settings.ttl != null) {
            throw new IllegalArgumentException("Selected Todo storage does not support TTL");
        }
    }
}
