/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * File-backed key-value store that mirrors Python's shelve-based behavior.
 * <p>
 * Mirrors Python's {@code ShelveStore} in
 * {@code openjiuwen/core/foundation/store/kv/shelve_store.py}.
 */
public class ShelveStore extends BaseKVStore {

    static final String EXCLUSIVE_EXPIRY_KEY = "exclusive_expiry";
    static final String EXCLUSIVE_VALUE_KEY = "exclusive_value";

    private final Path dbPath;

    public ShelveStore(String dbPath) {
        this.dbPath = Path.of(dbPath);
        try {
            Path parent = this.dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to initialize ShelveStore path", error);
        }
    }

    @Override
    public CompletableFuture<Void> set(String key, Object value) {
        return CompletableFuture.runAsync(() -> withStore(store -> {
            store.put(key, value);
            return null;
        }));
    }

    @Override
    public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
        return CompletableFuture.supplyAsync(() -> withStore(store -> {
            double now = nowSeconds();
            Object existing = store.get(key);
            if (existing != null) {
                if (existing instanceof Map<?, ?> map && map.containsKey(EXCLUSIVE_EXPIRY_KEY)) {
                    Object oldExpire = map.get(EXCLUSIVE_EXPIRY_KEY);
                    if (oldExpire == null || asDouble(oldExpire) > now) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            Double expireAt = expiry != null ? now + expiry : null;
            store.put(key, new ExclusiveRecord(value, expireAt));
            return true;
        }));
    }

    @Override
    public CompletableFuture<Object> get(String key) {
        return CompletableFuture.supplyAsync(() -> withStore(store -> unwrapExclusiveValue(store.get(key))));
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        return CompletableFuture.supplyAsync(() -> withStore(store -> store.containsKey(key)));
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        return CompletableFuture.runAsync(() -> withStore(store -> {
            store.remove(key);
            return null;
        }));
    }

    @Override
    public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
        return CompletableFuture.supplyAsync(() -> withStore(store -> {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : store.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }));
    }

    @Override
    public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
        return CompletableFuture.runAsync(() -> withStore(store -> {
            List<String> keysToDelete = new ArrayList<>();
            for (String key : store.keySet()) {
                if (key.startsWith(prefix)) {
                    keysToDelete.add(key);
                }
            }
            if (batchSize == null || batchSize <= 0) {
                for (String key : keysToDelete) {
                    store.remove(key);
                }
            } else {
                for (int index = 0; index < keysToDelete.size(); index += batchSize) {
                    List<String> batch = keysToDelete.subList(index, Math.min(keysToDelete.size(), index + batchSize));
                    for (String key : batch) {
                        store.remove(key);
                    }
                }
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<Object>> mget(List<String> keys) {
        return CompletableFuture.supplyAsync(() -> withStore(store -> {
            List<Object> results = new ArrayList<>(keys.size());
            for (String key : keys) {
                results.add(store.get(key));
            }
            return results;
        }));
    }

    @Override
    public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
        return CompletableFuture.supplyAsync(() -> withStore(store -> {
            int deleted = 0;
            if (batchSize == null || batchSize <= 0) {
                for (String key : keys) {
                    if (store.remove(key) != null) {
                        deleted++;
                    }
                }
            } else {
                for (int index = 0; index < keys.size(); index += batchSize) {
                    List<String> batch = keys.subList(index, Math.min(keys.size(), index + batchSize));
                    for (String key : batch) {
                        if (store.remove(key) != null) {
                            deleted++;
                        }
                    }
                }
            }
            return deleted;
        }));
    }

    @Override
    public BasedKVStorePipeline pipeline() {
        return new BasedKVStorePipeline(operations -> CompletableFuture.supplyAsync(() -> withStore(store -> {
            List<Object> results = new ArrayList<>();
            for (BasedKVStorePipeline.PipelineOperation operation : operations) {
                switch (operation.kind()) {
                    case "set" -> store.put(operation.key(), operation.value());
                    case "get" -> results.add(store.get(operation.key()));
                    case "exists" -> results.add(store.containsKey(operation.key()));
                    default -> throw new IllegalArgumentException("Unsupported pipeline operation: " + operation.kind());
                }
            }
            return results;
        })));
    }

    private synchronized <T> T withStore(Function<Map<String, Object>, T> handler) {
        Map<String, Object> store = loadStore();
        T result = handler.apply(store);
        saveStore(store);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadStore() {
        if (!Files.exists(dbPath)) {
            return new LinkedHashMap<>();
        }
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(dbPath))) {
            Object loaded = input.readObject();
            if (loaded instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return result;
            }
            return new LinkedHashMap<>();
        } catch (IOException | ClassNotFoundException error) {
            throw new IllegalStateException("Failed to load ShelveStore database", error);
        }
    }

    private void saveStore(Map<String, Object> store) {
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(dbPath))) {
            output.writeObject(store);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to save ShelveStore database", error);
        }
    }

    private static Object unwrapExclusiveValue(Object value) {
        if (value instanceof ExclusiveRecord exclusiveRecord) {
            return exclusiveRecord.exclusiveValue();
        }
        if (value instanceof Map<?, ?> map && map.containsKey(EXCLUSIVE_VALUE_KEY)) {
            return map.get(EXCLUSIVE_VALUE_KEY);
        }
        return value;
    }

    private static double nowSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private record ExclusiveRecord(Object exclusiveValue, Double exclusiveExpiry) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
