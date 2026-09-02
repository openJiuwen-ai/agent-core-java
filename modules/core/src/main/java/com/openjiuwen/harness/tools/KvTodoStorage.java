/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.spi.store.BaseKVStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
        return new ArrayList<>(List.of(items));
    }

    @Override
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        String key = buildKey(sessionId);
        kvStore.set(key, JsonUtils.safeJsonDumps(todos, "[]"));
    }

    @Override
    public void delete(String sessionId) throws IOException {
        String key = buildKey(sessionId);
        kvStore.delete(key);
    }
}
