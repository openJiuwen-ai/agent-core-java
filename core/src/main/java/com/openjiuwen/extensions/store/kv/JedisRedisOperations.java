/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.KVStorePipeline;

import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.JedisClusterCRC16;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Loaded only for a supplied Jedis client; isolates typed Jedis command
 * details.
 */
final class JedisRedisOperations extends ReflectiveRedisOperations {
    public JedisRedisOperations(Object client) {
        super(client);
        if (!(client instanceof Jedis) && !(client instanceof UnifiedJedis)) {
            throw new IllegalArgumentException("Unsupported Jedis client: " + client.getClass().getName());
        }
    }

    @Override
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(operations -> {
            List<Object> replies = executePipelineBatch(operations);
            if (replies != null) {
                return replies;
            }
            return executeSequentially(operations);
        });
    }

    private List<Object> executePipelineBatch(List<Object[]> operations) {
        if (operations.isEmpty()) {
            return new ArrayList<>();
        }
        if (!(redisClient instanceof UnifiedJedis unifiedJedis)) {
            return null;
        }
        AbstractPipeline pipeline = unifiedJedis.pipelined();
        if (!(pipeline instanceof Pipeline nativePipeline)) {
            closeQuietly(pipeline);
            return null;
        }
        try {
            enqueueOperations(nativePipeline, operations);
            List<Object> replies = nativePipeline.syncAndReturnAll();
            return mapRepliesToOperations(replies, operations);
        } catch (Exception e) {
            // A failed sync may already have applied commands; replaying could overwrite
            // concurrent writes.
            throw new IllegalStateException("Redis pipeline batch failed", e);
        } finally {
            closeQuietly(pipeline);
        }
    }

    /**
     * Queues each operation on the native pipeline; SET with a positive expiry
     * becomes a single SETEX command so TTL is applied atomically with the write.
     *
     * @param nativePipeline pipeline accepting queued commands
     * @param operations     queued pipeline operations
     * @since 0.1.16
     */
    private static void enqueueOperations(Pipeline nativePipeline, List<Object[]> operations) {
        for (Object[] operation : operations) {
            String action = String.valueOf(operation[0]);
            String key = operation.length > 1 ? String.valueOf(operation[1]) : "";
            switch (action) {
            case "set" -> {
                Integer expiry = extractExpiry(operation);
                Object value = operation.length > 2 ? operation[2] : null;
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = toBytes(value);
                if (expiry != null && expiry > 0) {
                    nativePipeline.setex(keyBytes, expiry, valueBytes);
                } else {
                    nativePipeline.set(keyBytes, valueBytes);
                }
            }
            case "get" -> nativePipeline.get(key.getBytes(StandardCharsets.UTF_8));
            case "isExists" -> nativePipeline.exists(key);
            default -> throw new IllegalArgumentException("Unsupported pipeline op: " + action);
            }
        }
    }

    /**
     * Aligns pipeline replies with operations 1:1 (enforced by count check) and
     * normalizes each reply to the type the per-operation path would return.
     *
     * @param replies    raw replies from {@code syncAndReturnAll()}
     * @param operations queued pipeline operations
     * @return results in operation order
     * @since 0.1.16
     */
    private static List<Object> mapRepliesToOperations(List<Object> replies, List<Object[]> operations) {
        if (replies == null || replies.size() != operations.size()) {
            throw new IllegalStateException("Pipeline reply count mismatch: ops=" + operations.size()
                    + ", replies=" + (replies == null ? 0 : replies.size()));
        }
        List<Object> results = new ArrayList<>(replies.size());
        for (int index = 0; index < operations.size(); index++) {
            Object reply = replies.get(index);
            if (reply instanceof RuntimeException failure) {
                throw failure;
            }
            String action = String.valueOf(operations.get(index)[0]);
            switch (action) {
            case "set" -> results.add(null);
            case "get" -> results.add(normalizeGetReply(reply));
            case "isExists" -> results.add(Boolean.TRUE.equals(reply));
            default -> throw new IllegalArgumentException("Unsupported pipeline op: " + action);
            }
        }
        return results;
    }

    /**
     * Closes a pipeline, swallowing close failures (best-effort cleanup).
     *
     * @param pipeline pipeline to close, may be null
     * @since 0.1.16
     */
    private static void closeQuietly(AbstractPipeline pipeline) {
        if (pipeline == null) {
            return;
        }
        try {
            pipeline.close();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(RedisStore.class).debug("Failed to close pipeline: {}", e.getMessage());
        }
    }

    /**
     * Encodes a pipeline SET value as bytes: byte[] passes through, other values
     * are UTF-8 encoded.
     *
     * @param value value to encode
     * @return encoded bytes
     * @since 0.1.16
     */
    private static byte[] toBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Normalizes a pipeline GET reply, mirroring the string-vs-binary preference of
     * the ordinary GET path: Java-serialization payloads (0xACED magic) stay as
     * bytes, and any payload that does not round-trip losslessly through UTF-8
     * stays as bytes too, so only genuine text is decoded to String.
     *
     * @param reply raw GET reply (byte[] or null)
     * @return String for losslessly decodable text, byte[] otherwise
     * @since 0.1.16
     */
    private static Object normalizeGetReply(Object reply) {
        if (!(reply instanceof byte[] bytes)) {
            return reply;
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xAC && bytes[1] == (byte) 0xED) {
            return bytes;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return Arrays.equals(text.getBytes(StandardCharsets.UTF_8), bytes) ? text : bytes;
    }

    @Override
    protected void setInternal(String key, Object value, Integer expiry) {
        SetParams params = new SetParams();
        if (expiry != null && expiry > 0) {
            params.ex(expiry);
        }
        try {
            write(key, value, params);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(RedisStore.class).error("Failed to set key: {}, error: {}", key,
                    e.getMessage());
            throw new RuntimeException("Failed to set key: " + key, e);
        }
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        write(key, value, new SetParams().ex(RedisOperations.ttlSeconds(ttl)));
    }

    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        SetParams params = new SetParams().nx();
        if (expiry != null && expiry > 0) {
            params.ex(expiry);
        }
        return "OK".equals(write(key, value, params));
    }

    private String write(String key, Object value, SetParams params) {
        requireKey(key);
        if (value instanceof byte[] bytes) {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            return redisClient instanceof Jedis client ? client.set(keyBytes, bytes, params)
                    : ((UnifiedJedis) redisClient).set(keyBytes, bytes, params);
        }
        return redisClient instanceof Jedis client ? client.set(key, String.valueOf(value), params)
                : ((UnifiedJedis) redisClient).set(key, String.valueOf(value), params);
    }

    @Override
    protected int deleteChunk(List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        if (redisClient instanceof JedisCluster cluster) {
            Map<Integer, List<String>> slots = new LinkedHashMap<>();
            for (String key : keys) {
                slots.computeIfAbsent(JedisClusterCRC16.getSlot(key), ignored -> new ArrayList<>()).add(key);
            }
            long deleted = 0;
            for (List<String> batch : slots.values()) {
                deleted += cluster.del(batch.toArray(String[]::new));
            }
            return Math.toIntExact(deleted);
        }
        String[] batch = keys.toArray(String[]::new);
        return Math.toIntExact(
                redisClient instanceof Jedis client ? client.del(batch) : ((UnifiedJedis) redisClient).del(batch));
    }

    @Override
    protected List<String> scanKeys(String prefix) {
        // Filter literal prefix locally, so Redis glob metacharacters in tenant/session
        // names stay literal.
        ScanParams params = new ScanParams().count(1000);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (redisClient instanceof JedisCluster cluster) {
            for (ConnectionPool pool : new ArrayList<>(cluster.getClusterNodes().values())) {
                try (Jedis node = new Jedis(pool.getResource())) {
                    scanNode(node, params, prefix, keys);
                }
            }
        } else if (redisClient instanceof Jedis client) {
            scanNode(client, params, prefix, keys);
        } else {
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                ScanResult<String> page = ((UnifiedJedis) redisClient).scan(cursor, params);
                for (String key : page.getResult()) {
                    if (key.startsWith(prefix)) {
                        keys.add(key);
                    }
                }
                cursor = page.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        }
        return new ArrayList<>(keys);
    }

    private void scanNode(Jedis node, ScanParams params, String prefix, LinkedHashSet<String> keys) {
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = node.scan(cursor, params);
            for (String key : page.getResult()) {
                if (key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            cursor = page.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
    }
}
