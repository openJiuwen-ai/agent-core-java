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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * KvTodoStorage.
 *
 * @since 0.1.7
 */
public class KvTodoStorage implements TodoStorage {
    private static final Logger logger = LoggerFactory.getLogger(KvTodoStorage.class);
    private final BaseKVStore kvStore;
    private final BiConsumer<String, String> write;
    private final Consumer<String> afterRead;

    public KvTodoStorage(BaseKVStore kvStore) {
        this(kvStore, null, false);
    }

    /** Resolve expiry behavior once, while assembling this Todo storage. */
    public KvTodoStorage(BaseKVStore kvStore, Duration ttl, boolean refreshOnRead) {
        this.kvStore = Objects.requireNonNull(kvStore);
        if (ttl == null) {
            write = kvStore::set;
            afterRead = key -> { };
        } else {
            if (ttl.isNegative() || ttl.isZero() || ttl.getNano() != 0
                    || ttl.getSeconds() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Todo TTL must be positive whole seconds within integer range");
            }
            if (!(kvStore instanceof ExpirableKVStore expirable)) {
                throw new IllegalArgumentException("Selected KV store does not support Todo TTL");
            }
            write = (key, value) -> expirable.set(key, value, ttl);
            afterRead = refreshOnRead ? key -> expirable.refreshTtl(List.of(key), ttl) : key -> { };
        }
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
        afterRead.accept(key);
        return new ArrayList<>(List.of(items));
    }

    @Override
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        String key = buildKey(sessionId);
        write.accept(key, JsonUtils.safeJsonDumps(todos, "[]"));
    }

    @Override
    public void delete(String sessionId) throws IOException {
        String key = buildKey(sessionId);
        kvStore.delete(key);
    }
}
