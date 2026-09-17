/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.ExpirableKVStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * KvTodoStorage.
 *
 * @since 0.1.7
 */
public class KvTodoStorage implements TodoStorage {
    private static final Logger logger = LoggerFactory.getLogger(KvTodoStorage.class);
    private final BaseKVStore kvStore;
    private final Duration ttl;
    private final boolean refreshOnRead;

    public KvTodoStorage(BaseKVStore kvStore) {
        this(kvStore, null, false);
    }

    public KvTodoStorage(BaseKVStore kvStore, Duration ttl, boolean refreshOnRead) {
        this.kvStore = Objects.requireNonNull(kvStore);
        if (ttl != null && !(kvStore instanceof ExpirableKVStore)) {
            throw new IllegalArgumentException("Todo TTL requires an ExpirableKVStore");
        }
        if (ttl != null && (ttl.isZero() || ttl.isNegative() || ttl.getSeconds() > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Todo TTL must be positive and within supported seconds range");
        }
        if (refreshOnRead && ttl == null) {
            throw new IllegalArgumentException("Todo refresh requires TTL");
        }
        this.ttl = ttl;
        this.refreshOnRead = refreshOnRead;
    }

    private String buildKey(String sessionId) {
        String rawKey = sessionId + ":todo";
        return TenantKVStoreKeyResolver.resolveKey(rawKey);
    }

    @Override
    public List<TodoItem> load(String sessionId) throws IOException {
        String key = buildKey(sessionId);
        Object value = kvStore.get(key);
        if (value == null) {
            logger.info("No todo data found in Redis for key: {}", key);
            return new ArrayList<>();
        }

        // 【修复点】：安全地将 Object 转换为 String，兼容底层返回 byte[] 的情况
        String json;
        if (value instanceof byte[]) {
            json = new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            json = String.valueOf(value);
        }
        if (json.isBlank()) {
            logger.info("No todo data found in Redis for key: {}", key);
            return new ArrayList<>();
        }
        TodoItem[] items = JsonUtils.safeJsonLoads(json, TodoItem[].class, new TodoItem[0]);
        if (refreshOnRead && ttl != null) {
            ((ExpirableKVStore) kvStore).refreshTtl(List.of(key), ttl);
        }
        return new ArrayList<>(List.of(items));
    }

    @Override
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        String key = buildKey(sessionId);
        String json = JsonUtils.safeJsonDumps(todos, "[]");
        if (ttl != null) {
            ((ExpirableKVStore) kvStore).set(key, json, ttl);
        } else {
            kvStore.set(key, json);
        }
    }

    @Override
    public void delete(String sessionId) throws IOException {
        String key = buildKey(sessionId);
        kvStore.delete(key);
    }
}
