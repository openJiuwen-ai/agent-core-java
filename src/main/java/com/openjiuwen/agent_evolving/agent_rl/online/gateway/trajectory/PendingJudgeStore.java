/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pending delayed-judge store for rail-v1 samples.
 * <p>
 * Mirrors Python's {@code PendingJudgeStore} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/pending_judge_store.py}.
 */
public final class PendingJudgeStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String KEY_PREFIX = "pending_judge";
    private static final String SESSION_PREFIX = "pending_judge_session";

    private final PendingJudgeStoreBackend redis;
    private final int ttlSec;

    public PendingJudgeStore(PendingJudgeStoreBackend redis) {
        this(redis, 24 * 3600);
    }

    public PendingJudgeStore(PendingJudgeStoreBackend redis, int ttlSec) {
        if (redis == null) {
            throw new IllegalArgumentException("PendingJudgeStore requires redis client");
        }
        this.redis = redis;
        this.ttlSec = ttlSec;
    }

    public void put(Map<String, Object> sample) {
        Map<String, Object> payload = deepCopy(sample);
        String sessionId = String.valueOf(payload.getOrDefault("session_id", ""));
        String trajectoryId = String.valueOf(payload.getOrDefault("trajectory_id", ""));
        int stepIndex = toInt(payload.get("step_index"));
        String key = sampleKey(sessionId, trajectoryId, stepIndex);
        payload.put("_pending_key", key);
        if (!payload.containsKey("_pending_created_at")) {
            payload.put("_pending_created_at", System.currentTimeMillis() / 1000.0d);
        }
        redis.set(key, writeJson(payload), ttlSec);
        redis.zadd(sessionKey(sessionId), Map.of(key, toDouble(payload.get("_pending_created_at"))));
        redis.expire(sessionKey(sessionId), ttlSec);
    }

    public List<Map<String, Object>> getBySession(String sessionId) {
        List<Object> rawKeys = redis.zrange(sessionKey(sessionId), 0, -1);
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Object rawKey : rawKeys) {
            keys.add(decode(rawKey));
        }
        List<Object> rows = redis.mget(keys);
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Object raw : rows) {
            if (raw == null) {
                continue;
            }
            samples.add(readJson(decode(raw)));
        }
        return samples;
    }

    public Map<String, Object> popOne(String sessionId, String trajectoryId, int stepIndex) {
        String key = sampleKey(sessionId, trajectoryId, stepIndex);
        Object raw = redis.get(key);
        PendingJudgeStoreBackend.PendingJudgeStorePipeline pipeline = redis.pipeline();
        pipeline.delete(key);
        pipeline.zrem(sessionKey(sessionId), key);
        pipeline.execute();
        if (raw == null) {
            return null;
        }
        return readJson(decode(raw));
    }

    public Map<String, Object> popEarliest(String sessionId) {
        List<Map<String, Object>> samples = getBySession(sessionId);
        if (samples.isEmpty()) {
            return null;
        }
        Map<String, Object> first = samples.getFirst();
        return popOne(
                sessionId,
                String.valueOf(first.getOrDefault("trajectory_id", "")),
                toInt(first.get("step_index"))
        );
    }

    public List<Map<String, Object>> popAll(String sessionId) {
        List<Map<String, Object>> samples = getBySession(sessionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> sample : samples) {
            Map<String, Object> popped = popOne(
                    sessionId,
                    String.valueOf(sample.getOrDefault("trajectory_id", "")),
                    toInt(sample.get("step_index"))
            );
            if (popped != null) {
                out.add(popped);
            }
        }
        return out;
    }

    public static List<Number> sortKey(Map<String, Object> sample) {
        return List.of(
                toDouble(sample != null ? sample.get("_pending_created_at") : null),
                toInt(sample != null ? sample.get("step_index") : null)
        );
    }

    private static String sampleKey(String sessionId, String trajectoryId, int stepIndex) {
        return KEY_PREFIX + ":" + sessionId + ":" + trajectoryId + ":" + stepIndex;
    }

    private static String sessionKey(String sessionId) {
        return SESSION_PREFIX + ":" + sessionId;
    }

    private static Map<String, Object> deepCopy(Map<String, Object> sample) {
        if (sample == null) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.convertValue(sample, MAP_TYPE);
    }

    private static String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize pending judge payload", exception);
        }
    }

    private static Map<String, Object> readJson(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse pending judge payload", exception);
        }
    }

    private static String decode(Object raw) {
        if (raw instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(raw);
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0d;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
