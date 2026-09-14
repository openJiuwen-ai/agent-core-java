/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.spi.store.BaseKVStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * KvTodoStorage.
 *
 * @since 0.1.7
 */
public class KvTodoStorage implements TodoStorage {
    private static final Logger logger = LoggerFactory.getLogger(KvTodoStorage.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BaseKVStore kvStore;

    public KvTodoStorage(BaseKVStore kvStore) {
        this.kvStore = Objects.requireNonNull(kvStore);
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
        // Redis clients may return String or byte[]; String.valueOf(byte[]) is not JSON.
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
        return items == null ? new ArrayList<>() : new ArrayList<>(List.of(items));
    }

    @Override
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        String key = buildKey(sessionId);
        Object dumped = JsonUtils.safeJsonDumps(todos, "[]");
        kvStore.set(key, dumped == null ? "[]" : String.valueOf(dumped));
    }

    @Override
    public void delete(String sessionId) throws IOException {
        String key = buildKey(sessionId);
        kvStore.delete(key);
    }
}
