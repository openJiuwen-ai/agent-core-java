/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreProvider;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Redis provider sharing the existing Checkpointer connection vocabulary. */
public final class RedisKVStoreProvider implements KVStoreProvider {
    @Override
    public String typeName() {
        return "redis";
    }

    @Override
    public BaseKVStore create(Map<String, Object> conf) {
        RedisConnectionConfig connection = connection(conf);
        if (connection.getRedisClient() != null) {
            return new RedisStore(connection.getRedisClient());
        }
        try {
            Object client = Class.forName("com.openjiuwen.extensions.store.kv.JedisRedisClientFactory")
                    .getMethod("create", RedisConnectionConfig.class).invoke(null, connection);
            return new RedisStore(client);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException("Redis client creation failed", e.getCause());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException(
                    "Core-created Redis clients require an explicit redis.clients:jedis dependency", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot initialize Redis client factory", e);
        }
    }

    private static boolean booleanField(Map<String, Object> fields, String name) {
        Object value = fields.getOrDefault(name, false);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException(name + " must be boolean");
    }

    static RedisConnectionConfig connection(Map<String, Object> conf) {
        Map<String, Object> input = conf == null ? Map.of() : conf;
        Map<String, Object> fields;
        if (input.containsKey("connection")) {
            if (!(input.get("connection") instanceof Map<?, ?> nested)) {
                throw new IllegalArgumentException("connection must be a map");
            }
            fields = new LinkedHashMap<>();
            nested.forEach((key, value) -> fields.put(String.valueOf(key), value));
            for (String key : new String[] { "redis_client", "url", "host", "port", "password", "username", "database",
                    "cluster", "cluster_mode", "tls", "connection_args" }) {
                if (input.containsKey(key)) {
                    throw new IllegalArgumentException("connection conflicts with flat Redis field: " + key);
                }
            }
        } else {
            fields = new LinkedHashMap<>(input);
            // Legacy KV default applies only to the flat entrypoint.
            if (!fields.containsKey("url") && fields.get("redis_client") == null) {
                Object configuredHost = fields.getOrDefault("host", "localhost");
                if (!(configuredHost instanceof String host) || host.isBlank()) {
                    throw new IllegalArgumentException("Redis host must be a nonblank string");
                }
                Object configuredPort = fields.getOrDefault("port", 6379);
                if (!(configuredPort instanceof Number number) || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.doubleValue() < 1 || number.doubleValue() > 65535) {
                    throw new IllegalArgumentException("Redis port must be an integer in 1..65535");
                }
                int port = number.intValue();
                boolean tls = booleanField(fields, "tls");
                try {
                    fields.put("url",
                            new URI(tls ? "rediss" : "redis", null, host, port, "/0", null, null).toASCIIString());
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Invalid Redis host", e);
                }
            }
            if (!fields.containsKey("cluster_mode") && fields.containsKey("cluster")) {
                fields.put("cluster_mode", booleanField(fields, "cluster"));
            }
            Map<String, Object> args = new LinkedHashMap<>();
            if (fields.containsKey("connection_args") && fields.get("connection_args") != null
                    && !(fields.get("connection_args") instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("connection_args must be a map");
            }
            if (fields.get("connection_args") instanceof Map<?, ?> nested) {
                nested.forEach((k, v) -> args.put(String.valueOf(k), v));
            }
            for (String key : new String[] { "password", "username", "database" }) {
                if (fields.containsKey(key)) {
                    args.putIfAbsent(key, fields.get(key));
                }
            }
            fields.put("connection_args", args);
        }
        RedisConnectionConfig result = RedisConnectionConfig.fromMap(fields);
        result.validate();
        return result;
    }
}
