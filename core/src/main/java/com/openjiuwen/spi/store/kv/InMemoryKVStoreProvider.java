/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.kv;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;
import com.openjiuwen.spi.store.KVStoreProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Built-in KV store provider for in-memory storage.
 * <p>
 * Creates in-memory KV store instances backed by a ConcurrentHashMap.
 * Suitable for testing and single-process scenarios where persistence is not required.
 * 
 * @see KVStoreProvider
 * @see com.openjiuwen.core.foundation.store.kv.InMemoryKVStore
 * @since 0.1.7
 */
public final class InMemoryKVStoreProvider implements KVStoreProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String typeName() {
        return "in_memory";
    }

    /**
     * Creates a new in-memory KV store instance.
     * 
     * @param conf the configuration map (ignored for in-memory implementation)
     * @return a new InMemoryKVStore instance
     * @since 0.1.7
     */
    @Override
    public BaseKVStore create(Map<String, Object> conf) {
        InMemoryKVStore delegate = new InMemoryKVStore();
        return new SyncKVStoreAdapter(delegate);
    }

    private static final class SyncKVStoreAdapter extends BaseKVStore {
        private final InMemoryKVStore delegate;

        SyncKVStoreAdapter(InMemoryKVStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void set(String key, Object value) {
            delegate.set(key, value).join();
        }

        @Override
        public boolean exclusiveSet(String key, Object value, Integer expiry) {
            return delegate.exclusiveSet(key, value, expiry).join();
        }

        @Override
        public Object get(String key) {
            return delegate.get(key).join();
        }

        @Override
        public boolean isExists(String key) {
            return delegate.exists(key).join();
        }

        @Override
        public void delete(String key) {
            delegate.delete(key).join();
        }

        @Override
        public Map<String, Object> getByPrefix(String prefix) {
            return delegate.getByPrefix(prefix).join();
        }

        @Override
        public void deleteByPrefix(String prefix, Integer batchSize) {
            delegate.deleteByPrefix(prefix, batchSize).join();
        }

        @Override
        public java.util.List<Object> mget(java.util.List<String> keys) {
            return delegate.mget(keys).join();
        }

        @Override
        public int batchDelete(java.util.List<String> keys, Integer batchSize) {
            return delegate.batchDelete(keys, batchSize).join();
        }

        @Override
        public KVStorePipeline pipeline() {
            return new KVStorePipeline(ops -> {
                List<Object> results = new ArrayList<>();
                for (Object[] op : ops) {
                    String kind = (String) op[0];
                    switch (kind) {
                        case "set" -> {
                            delegate.set((String) op[1], op[2]).join();
                            results.add(null);
                        }
                        case "get" -> results.add(delegate.get((String) op[1]).join());
                        case "isExists", "exists" -> results.add(delegate.exists((String) op[1]).join());
                        default -> results.add(null);
                    }
                }
                return results;
            });
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
