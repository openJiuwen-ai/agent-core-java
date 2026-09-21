/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.ExpirableKVStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BaseKVStore kvStore;
    private final BiConsumer<String, String> write;
    private final Consumer<String> afterRead;

    public KvTodoStorage(BaseKVStore kvStore) {
        this(kvStore, null, false);
    }

    /**
     * Resolves expiry behavior once, while assembling this Todo storage.
     *
     * @param kvStore the store used for Todo data
     * @param ttl the Todo TTL, or null to retain ordinary writes without expiration
     * @param shouldRefreshOnRead whether successful reads renew the configured TTL
     * @throws IllegalArgumentException if the TTL is invalid or unsupported by the store
     * @since 0.1.15
     */
    public KvTodoStorage(BaseKVStore kvStore, Duration ttl, boolean shouldRefreshOnRead) {
        this.kvStore = Objects.requireNonNull(kvStore);
        if (ttl == null) {
            write = kvStore::set;
            afterRead = key -> {
            };
        } else {
            if (ttl.isNegative() || ttl.isZero() || ttl.getNano() != 0
                    || ttl.getSeconds() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Todo TTL must be positive whole seconds within integer range");
            }
            if (!(kvStore instanceof ExpirableKVStore expirable)) {
                throw new IllegalArgumentException("Selected KV store does not support Todo TTL");
            }
            write = (key, value) -> expirable.set(key, value, ttl);
            afterRead = shouldRefreshOnRead ? key -> expirable.refreshTtl(List.of(key), ttl) : key -> {
            };
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
        String json;
        if (value instanceof byte[] bytes) {
            json = new String(bytes, StandardCharsets.UTF_8);
        } else {
            json = String.valueOf(value);
        }
        if (json.isBlank()) {
            logger.info("No todo data found in Redis for key: {}", key);
            return new ArrayList<>();
        }
        TodoItem[] items = MAPPER.readValue(json, TodoItem[].class);
        List<TodoItem> result = items == null ? new ArrayList<>() : new ArrayList<>(List.of(items));
        afterRead.accept(key);
        return result;
    }

    @Override
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        String key = buildKey(sessionId);
        Object dumped = JsonUtils.safeJsonDumps(todos, "[]");
        write.accept(key, dumped == null ? "[]" : String.valueOf(dumped));
    }

    @Override
    public void delete(String sessionId) throws IOException {
        String key = buildKey(sessionId);
        kvStore.delete(key);
    }
}
