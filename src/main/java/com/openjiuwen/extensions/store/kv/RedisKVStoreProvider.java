/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreProvider;

import java.util.Map;

public final class RedisKVStoreProvider implements KVStoreProvider {
    @Override
    public String typeName() {
        return "redis";
    }

    @Override
    public BaseKVStore create(Map<String, Object> conf) {
        Object redisClient = resolveRedisClient(conf);
        return new RedisStore(redisClient);
    }

    private Object resolveRedisClient(Map<String, Object> conf) {
        Object existing = conf.get("redis_client");
        if (existing != null) {
            return existing;
        }
        String host = conf.getOrDefault("host", "localhost") instanceof String
            ? (String) conf.getOrDefault("host", "localhost") : "localhost";
        int port = conf.getOrDefault("port", 6379) instanceof Number
            ? ((Number) conf.getOrDefault("port", 6379)).intValue() : 6379;
        String password = conf.get("password") instanceof String
            ? (String) conf.get("password") : null;
        boolean cluster = Boolean.parseBoolean(String.valueOf(conf.getOrDefault("cluster", "false")));
        return createClientByReflection(host, port, password, cluster);
    }

    private Object createClientByReflection(String host, int port, String password, boolean cluster) {
        try {
            if (cluster) {
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
}
