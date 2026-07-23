/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.spi.store.BaseKVStore;

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
            return new ArrayList<>();
        }
        String json = String.valueOf(value);
        if (json.isBlank()) {
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
