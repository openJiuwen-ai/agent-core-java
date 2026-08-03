/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.async;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * CompletableFuture/map bridge for vector-store compatibility APIs.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class CompletableMap<K, V> extends CompletableFuture<Map<K, V>> implements Map<K, V> {

    public CompletableMap(CompletableFuture<? extends Map<K, V>> delegate) {
        delegate.whenComplete((value, error) -> {
            if (error != null) {
                completeExceptionally(error);
                return;
            }
            complete(value == null ? new LinkedHashMap<>() : value);
        });
    }

    public static <K, V> CompletableMap<K, V> completed(Map<K, V> value) {
        return new CompletableMap<>(CompletableFuture.completedFuture(
                value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value)));
    }

    private Map<K, V> value() {
        return join();
    }

    @Override
    public int size() {
        return value().size();
    }

    @Override
    public boolean isEmpty() {
        return value().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return value().containsKey(key);
    }

    @Override
    public boolean containsValue(Object item) {
        return value().containsValue(item);
    }

    @Override
    public V get(Object key) {
        return value().get(key);
    }

    @Override
    public V put(K key, V item) {
        return value().put(key, item);
    }

    @Override
    public V remove(Object key) {
        return value().remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        value().putAll(map);
    }

    @Override
    public void clear() {
        value().clear();
    }

    @Override
    public Set<K> keySet() {
        return value().keySet();
    }

    @Override
    public Collection<V> values() {
        return value().values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return value().entrySet();
    }
}
