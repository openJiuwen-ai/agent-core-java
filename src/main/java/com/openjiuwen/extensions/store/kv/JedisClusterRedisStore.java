/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * RedisStore wrapper for Jedis cluster clients.
 */
public class JedisClusterRedisStore extends RedisStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {
    };
    private static final String ENVELOPE_MARKER = "__openjiuwen_envelope";
    private static final String ENVELOPE_KIND = "kind";
    private static final String ENVELOPE_VALUE = "value";
    private static final String KIND_BYTES = "bytes";
    private static final String KIND_JSON = "json";
    private static final String OK = "OK";

    private final JedisCluster jedisCluster;

    public JedisClusterRedisStore(JedisCluster jedisCluster) {
        super(Objects.requireNonNull(jedisCluster, "jedisCluster must not be null"));
        this.jedisCluster = jedisCluster;
    }

    public JedisCluster getJedisCluster() {
        return jedisCluster;
    }

    @Override
    public CompletableFuture<Void> set(String key, Object value) {
        try {
            setSync(key, value, null);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
        try {
            return CompletableFuture.completedFuture(exclusiveSetSync(key, value, expiry));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Object> get(String key) {
        try {
            return CompletableFuture.completedFuture(getSync(key));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        try {
            return CompletableFuture.completedFuture(existsSync(key));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        try {
            deleteSync(key);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
        try {
            return CompletableFuture.completedFuture(getByPrefixSync(prefix));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
        try {
            deleteByPrefixSync(prefix, batchSize);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<List<Object>> mget(List<String> keys) {
        try {
            return CompletableFuture.completedFuture(mgetSync(keys));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
        try {
            return CompletableFuture.completedFuture(batchDeleteSync(keys, batchSize));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public BasedKVStorePipeline pipeline() {
        return new BasedKVStorePipeline(operations -> {
            try {
                List<Object> results = new ArrayList<>(operations.size());
                for (BasedKVStorePipeline.PipelineOperation operation : operations) {
                    switch (operation.kind()) {
                    case "set" -> {
                        setSync(operation.key(), operation.value(), operation.ttl());
                        results.add(null);
                    }
                    case "get" -> results.add(getSync(operation.key()));
                    case "exists" -> results.add(existsSync(operation.key()));
                    default -> throw new IllegalArgumentException("Unsupported pipeline op: " + operation.kind());
                    }
                }
                return CompletableFuture.completedFuture(results);
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        });
    }

    /**
     * Refresh TTL (Time To Live) for given keys.
     *
     * @param keys       a list of keys to refresh TTL for
     * @param ttlSeconds the TTL value in seconds
     */
    @Override
    public void refreshTtl(List<String> keys, int ttlSeconds) {
        if (keys == null || keys.isEmpty() || ttlSeconds <= 0) {
            return;
        }
        for (String key : keys) {
            jedisCluster.expire(key, ttlSeconds);
        }
    }

    @Override
    public boolean isCluster() {
        return true;
    }

    private void setSync(String key, Object value, Integer expiry) {
        if (expiry != null && expiry > 0) {
            jedisCluster.set(key, serialize(value), SetParams.setParams().ex(expiry));
            return;
        }
        jedisCluster.set(key, serialize(value));
    }

    private boolean exclusiveSetSync(String key, Object value, Integer expiry) {
        SetParams params = SetParams.setParams().nx();
        if (expiry != null && expiry > 0) {
            params.ex(expiry);
        }
        return OK.equalsIgnoreCase(jedisCluster.set(key, serialize(value), params));
    }

    private Object getSync(String key) {
        String value = jedisCluster.get(key);
        return value == null ? null : deserialize(value);
    }

    private boolean existsSync(String key) {
        return jedisCluster.exists(key);
    }

    private void deleteSync(String key) {
        jedisCluster.del(key);
    }

    private Map<String, Object> getByPrefixSync(String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : scanKeysByPrefix(prefix)) {
            Object value = getSync(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private void deleteByPrefixSync(String prefix, Integer batchSize) {
        batchDeleteSync(new ArrayList<>(scanKeysByPrefix(prefix)), batchSize);
    }

    private List<Object> mgetSync(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> values = new ArrayList<>(keys.size());
        for (String key : keys) {
            values.add(getSync(key));
        }
        return values;
    }

    private int batchDeleteSync(List<String> keys, Integer batchSize) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        // Redis Cluster cross-slot deletes are executed key-by-key for correctness; batchSize is intentionally ignored.
        int deleted = 0;
        for (String key : keys) {
            deleted += jedisCluster.del(key);
        }
        return deleted;
    }

    private static String serialize(Object value) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(ENVELOPE_MARKER, true);
            if (value instanceof byte[] bytes) {
                envelope.put(ENVELOPE_KIND, KIND_BYTES);
                envelope.put(ENVELOPE_VALUE, Base64.getEncoder().encodeToString(bytes));
            } else {
                envelope.put(ENVELOPE_KIND, KIND_JSON);
                envelope.put(ENVELOPE_VALUE, value);
            }
            return OBJECT_MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Redis value", e);
        }
    }

    private static Object deserialize(String value) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(value, OBJECT_TYPE);
            if (!(parsed instanceof Map<?, ?> map) || !Boolean.TRUE.equals(map.get(ENVELOPE_MARKER))
                    || !map.containsKey(ENVELOPE_KIND)) {
                return parsed;
            }
            Object kind = map.get(ENVELOPE_KIND);
            Object envelopeValue = map.get(ENVELOPE_VALUE);
            if (KIND_BYTES.equals(kind) && envelopeValue instanceof String encoded) {
                return Base64.getDecoder().decode(encoded);
            }
            if (KIND_JSON.equals(kind)) {
                return envelopeValue;
            }
            return parsed;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Redis value", e);
        }
    }

    private LinkedHashSet<String> scanKeysByPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        ScanParams scanParams = new ScanParams().match(escapeRedisGlob(prefix) + "*").count(500);
        for (ConnectionPool pool : jedisCluster.getClusterNodes().values()) {
            try (Jedis jedis = new Jedis(pool.getResource())) {
                if (!isMaster(jedis)) {
                    continue;
                }
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                    for (String key : scanResult.getResult()) {
                        if (key.startsWith(prefix)) {
                            keys.add(key);
                        }
                    }
                    cursor = scanResult.getCursor();
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            }
        }
        return keys;
    }

    private String escapeRedisGlob(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '*' || ch == '?' || ch == '[' || ch == ']' || ch == '\\') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    static boolean isMasterReplicationInfo(String replicationInfo) {
        if (replicationInfo == null) {
            throw new IllegalStateException("Redis replication info is null; cannot determine node role");
        }
        for (String line : replicationInfo.split("\\R")) {
            if (!line.startsWith("role:")) {
                continue;
            }
            return "role:master".equals(line);
        }
        throw new IllegalStateException("Redis replication info does not contain a role line; cannot determine node role");
    }

    private boolean isMaster(Jedis jedis) {
        String replicationInfo;
        try {
            replicationInfo = jedis.info("replication");
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to read Redis replication info from cluster node", e);
        }
        return isMasterReplicationInfo(replicationInfo);
    }
}
