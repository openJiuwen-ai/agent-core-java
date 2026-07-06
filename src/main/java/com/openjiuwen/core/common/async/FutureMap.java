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
 * Typed compatibility bridge for APIs that historically behaved like a map
 * while newer SDK code consumes a {@link CompletableFuture} of that map.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class FutureMap<K, V> extends CompletableFuture<Map<K, V>> implements Map<K, V> {

    public FutureMap(CompletableFuture<? extends Map<K, V>> delegate) {
        delegate.whenComplete((value, error) -> {
            if (error != null) {
                completeExceptionally(error);
                return;
            }
            complete(value == null ? new LinkedHashMap<>() : value);
        });
    }

    public static <K, V> FutureMap<K, V> completed(Map<K, V> value) {
        return new FutureMap<>(CompletableFuture.completedFuture(
                value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value)));
    }

    public static <K, V> FutureMap<K, V> fromFuture(CompletableFuture<? extends Map<K, V>> future) {
        return new FutureMap<>(future == null ? CompletableFuture.completedFuture(new LinkedHashMap<>()) : future);
    }

    private Map<K, V> value() {
        return join();
    }

    @Override
    public Map<K, V> join() {
        return super.join();
    }

    public CompletableFuture<Map<K, V>> toCompletableFuture() {
        return this;
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
    public boolean containsValue(Object value) {
        return value().containsValue(value);
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

    @Override
    public boolean equals(Object obj) {
        return value().equals(obj);
    }

    @Override
    public int hashCode() {
        return value().hashCode();
    }
}
