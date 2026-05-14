/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pending delayed-judge store for rail-v1 samples.
 * <p>
 * Mirrors Python's {@code PendingJudgeStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.pending_judge_store}.
 */
public class PendingJudgeStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String KEY_PREFIX = "pending_judge";
    private static final String SESSION_PREFIX = "pending_judge_session";

    private final RedisTrajectoryStoreBackend redis;
    private final int ttlSec;

    public PendingJudgeStore(RedisTrajectoryStoreBackend redis) {
        this(redis, 24 * 3600);
    }

    public PendingJudgeStore(RedisTrajectoryStoreBackend redis, int ttlSec) {
        if (redis == null) {
            throw new IllegalArgumentException("PendingJudgeStore requires redis client");
        }
        this.redis = redis;
        this.ttlSec = ttlSec;
    }

    public void put(Map<String, Object> sample) {
        String sessionId = String.valueOf(sample.getOrDefault("session_id", ""));
        String trajectoryId = String.valueOf(sample.getOrDefault("trajectory_id", ""));
        int stepIndex = intValue(sample.get("step_index"), 0);
        String key = sampleKey(sessionId, trajectoryId, stepIndex);
        Map<String, Object> payload = new LinkedHashMap<>(sample);
        payload.put("_pending_key", key);
        payload.putIfAbsent("_pending_created_at", System.currentTimeMillis() / 1000.0);
        redisSet(key, writeJson(payload));
        redis.zadd(sessionKey(sessionId), Map.of(key, doubleValue(payload.get("_pending_created_at"), 0.0)));
    }

    public List<Map<String, Object>> getBySession(String sessionId) {
        List<Object> keys = zrange(sessionKey(sessionId));
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Object key : keys) {
            Object raw = redisGet(String.valueOf(key));
            if (raw != null) {
                samples.add(readJson(String.valueOf(raw)));
            }
        }
        samples.sort(Comparator.comparing(PendingJudgeStore::sortKey));
        return samples;
    }

    public Map<String, Object> popOne(String sessionId, String trajectoryId, int stepIndex) {
        String key = sampleKey(sessionId, trajectoryId, stepIndex);
        Object raw = redisGet(key);
        RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline = redis.pipeline();
        delete(pipeline, key);
        zrem(pipeline, sessionKey(sessionId), key);
        pipeline.execute();
        return raw == null ? null : readJson(String.valueOf(raw));
    }

    public Map<String, Object> popEarliest(String sessionId) {
        List<Map<String, Object>> samples = getBySession(sessionId);
        if (samples.isEmpty()) {
            return null;
        }
        Map<String, Object> first = samples.getFirst();
        return popOne(sessionId, String.valueOf(first.getOrDefault("trajectory_id", "")), intValue(first.get("step_index"), 0));
    }

    public List<Map<String, Object>> popAll(String sessionId) {
        List<Map<String, Object>> samples = getBySession(sessionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> sample : samples) {
            Map<String, Object> popped = popOne(sessionId, String.valueOf(sample.getOrDefault("trajectory_id", "")), intValue(sample.get("step_index"), 0));
            if (popped != null) {
                out.add(popped);
            }
        }
        return out;
    }

    private static String sampleKey(String sessionId, String trajectoryId, int stepIndex) {
        return KEY_PREFIX + ":" + sessionId + ":" + trajectoryId + ":" + stepIndex;
    }

    private static String sessionKey(String sessionId) {
        return SESSION_PREFIX + ":" + sessionId;
    }

    private static double sortKey(Map<String, Object> sample) {
        return doubleValue(sample.get("_pending_created_at"), 0.0) * 1_000_000 + intValue(sample.get("step_index"), 0);
    }

    private void redisSet(String key, String value) {
        if (redis instanceof TestablePendingJudgeBackend testable) {
            testable.set(key, value, ttlSec);
            testable.expire(sessionKey(extractSessionId(key)), ttlSec);
        }
    }

    private Object redisGet(String key) {
        if (redis instanceof TestablePendingJudgeBackend testable) {
            return testable.get(key);
        }
        return null;
    }

    private List<Object> zrange(String key) {
        if (redis instanceof TestablePendingJudgeBackend testable) {
            return new ArrayList<>(testable.zrange(key, 0, -1));
        }
        return List.of();
    }

    private void delete(RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline, String key) {
        if (pipeline instanceof TestablePendingJudgePipeline testable) {
            testable.delete(key);
        }
    }

    private void zrem(RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline, String key, String member) {
        if (pipeline instanceof TestablePendingJudgePipeline testable) {
            testable.zremSingle(key, member);
        }
    }

    private static String extractSessionId(String sampleKey) {
        String[] parts = sampleKey.split(":", 4);
        return parts.length >= 2 ? parts[1] : "";
    }

    private static String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception exception) {
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

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    public interface TestablePendingJudgeBackend {
        void set(String key, String value, int ttlSec);

        void expire(String key, int ttlSec);

        List<String> zrange(String key, int start, int end);

        Object get(String key);
    }

    public interface TestablePendingJudgePipeline {
        void delete(String key);

        void zremSingle(String key, String member);
    }
}
