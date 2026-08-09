/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;
import com.openjiuwen.spi.store.KVStoreProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SPI provider that creates Redis-backed {@link BaseKVStore} instances.
 * <p>
 * Wraps develop's foundation {@link RedisStore} (async CompletableFuture API)
 * with a synchronous adapter matching {@link BaseKVStore}.
 *
 * @since 0.1.7
 */
public final class RedisKVStoreProvider implements KVStoreProvider {
    @Override
    public String typeName() {
        return "redis";
    }

    @Override
    public BaseKVStore create(Map<String, Object> conf) {
        Object existing = conf.get("redis_client");
        if (existing != null) {
            return new SyncKVStoreAdapter(new RedisStore(existing, false));
        }
        Object redisClient = createClientByReflection(
                stringOrDefault(conf.get("host"), "localhost"),
                intOrDefault(conf.get("port"), 6379),
                conf.get("password") instanceof String ? (String) conf.get("password") : null,
                Boolean.parseBoolean(String.valueOf(conf.getOrDefault("cluster", "false"))));
        return new SyncKVStoreAdapter(new RedisStore(redisClient, true));
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value instanceof String s ? s : defaultValue;
    }

    private static int intOrDefault(Object value, int defaultValue) {
        return value instanceof Number n ? n.intValue() : defaultValue;
    }

    private Object createClientByReflection(String host, int port, String password, boolean isCluster) {
        try {
            if (isCluster) {
                Class<?> poolConfigClass = Class.forName("redis.clients.jedis.JedisPoolConfig");
                Object poolConfig = poolConfigClass.getDeclaredConstructor().newInstance();
                Class<?> clusterClass = Class.forName("redis.clients.jedis.JedisCluster");
                java.util.Set<String> nodes = java.util.Set.of(host + ":" + port);
                if (password != null && !password.isEmpty()) {
                    return clusterClass.getDeclaredConstructor(java.util.Set.class, poolConfigClass, String.class)
                            .newInstance(nodes, poolConfig, password);
                }
                return clusterClass.getDeclaredConstructor(java.util.Set.class, poolConfigClass)
                        .newInstance(nodes, poolConfig);
            }
            Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getDeclaredConstructor(String.class, int.class).newInstance(host, port);
            if (password != null && !password.isEmpty()) {
                jedisClass.getMethod("auth", String.class).invoke(jedis, password);
            }
            return jedis;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create Redis client via reflection: " + e.getMessage(), e);
        }
    }

    private static final class SyncKVStoreAdapter extends BaseKVStore {
        private final RedisStore delegate;

        SyncKVStoreAdapter(RedisStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void set(String key, Object value) {
            delegate.set(key, value).join();
        }

        @Override
        public boolean exclusiveSet(String key, Object value, Integer expiry) {
            return Boolean.TRUE.equals(delegate.exclusiveSet(key, value, expiry).join());
        }

        @Override
        public Object get(String key) {
            return delegate.get(key).join();
        }

        @Override
        public boolean isExists(String key) {
            return Boolean.TRUE.equals(delegate.exists(key).join());
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
        public List<Object> mget(List<String> keys) {
            return delegate.mget(keys).join();
        }

        @Override
        public int batchDelete(List<String> keys, Integer batchSize) {
            Integer deleted = delegate.batchDelete(keys, batchSize).join();
            return deleted == null ? 0 : deleted;
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
