/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis-backed shared trajectory store for scored RL training samples.
 * <p>
 * Mirrors Python's {@code RedisTrajectoryStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.storage.redis_trajectory_store}.
 */
public class RedisTrajectoryStore implements TrajectorySampleStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTrajectoryStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String KEY_PREFIX = "rl:traj";
    private static final String IDX_PREFIX = "rl:traj_idx";
    private static final String USERS_SET_KEY = "rl:traj_users";
    private static final String DEFAULT_USER_ID = "online";
    private static final String LUA_FETCH_AND_MARK = """
            local pending_key   = KEYS[1]
            local training_key  = KEYS[2]
            local limit         = tonumber(ARGV[1])
            local now_score     = tonumber(ARGV[2])
            local new_status    = ARGV[3]
            local traj_prefix   = ARGV[4]

            local ids = redis.call('ZRANGE', pending_key, 0, limit - 1)
            if #ids == 0 then return {} end

            redis.call('ZREM', pending_key, unpack(ids))
            for _, id in ipairs(ids) do
                redis.call('ZADD', training_key, now_score, id)
                redis.call('HSET', traj_prefix .. id, 'status', new_status)
            end
            return ids
            """;

    private final RedisTrajectoryStoreBackend backend;
    private final RedisTrajectoryStoreBackend.RedisTrajectoryStoreFetchScript fetchScript;

    public RedisTrajectoryStore(RedisTrajectoryStoreBackend backend) {
        this.backend = backend;
        this.fetchScript = backend.registerFetchAndMarkScript(LUA_FETCH_AND_MARK);
    }

    @Override
    public void saveSample(Map<String, Object> sample, String userId) {
        String sampleId = String.valueOf(sample != null ? sample.getOrDefault("sample_id", "") : "").trim();
        if (sampleId.isEmpty()) {
            throw new IllegalArgumentException("sample_id is required");
        }

        Map<String, Object> normalized = deepCopy(sample);
        String normalizedUserId = pythonOrString(normalized.get("user_id"), userId, DEFAULT_USER_ID);
        normalized.put("user_id", normalizedUserId);
        normalized.put("_store_status", "pending");

        String createdAt = pythonOrString(normalized.get("created_at"), OffsetDateTime.now(ZoneOffset.UTC).toString());
        String sessionId = pythonOrString(normalized.get("session_id"), "default");
        String payload = writeJson(normalized);
        double score = epoch(createdAt);

        List<Object> existing = backend.hmget(trajKey(sampleId), List.of("user_id", "status"));
        String existingUserId = decode(existing.size() > 0 ? existing.get(0) : null);
        String existingStatus = decode(existing.size() > 1 ? existing.get(1) : null);

        RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline = backend.pipeline();
        if (existingUserId != null && existingStatus != null) {
            pipeline.zrem(idxKey(existingUserId, existingStatus), sampleId);
        }
        pipeline.hset(trajKey(sampleId), Map.of(
                "sample_id", sampleId,
                "user_id", normalizedUserId,
                "session_id", sessionId,
                "created_at", createdAt,
                "status", "pending",
                "sample_json", payload
        ));
        pipeline.zadd(idxKey(normalizedUserId, "pending"), Map.of(sampleId, score));
        pipeline.sadd(USERS_SET_KEY, normalizedUserId);
        pipeline.execute();
    }

    @Override
    public int getPendingCount(String userId) {
        return (int) backend.zcard(idxKey(userId, "pending"));
    }

    @Override
    public List<String> getUsersAboveThreshold(int threshold) {
        Set<Object> members = backend.smembers(USERS_SET_KEY);
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        List<String> userIds = members.stream().map(RedisTrajectoryStore::decode).toList();
        RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline = backend.pipeline();
        for (String uid : userIds) {
            pipeline.zcard(idxKey(uid, "pending"));
            pipeline.zcard(idxKey(uid, "training"));
            pipeline.zcard(idxKey(uid, "trained"));
            pipeline.zcard(idxKey(uid, "failed"));
        }
        List<Object> counts = pipeline.execute();

        List<String> result = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            int pending = toInt(counts.get(i * 4));
            int training = toInt(counts.get(i * 4 + 1));
            int trained = toInt(counts.get(i * 4 + 2));
            int failed = toInt(counts.get(i * 4 + 3));
            String uid = userIds.get(i);
            if (pending >= threshold) {
                result.add(uid);
            } else if (pending == 0 && training == 0 && trained == 0 && failed == 0) {
                stale.add(uid);
            }
        }
        if (!stale.isEmpty()) {
            backend.srem(USERS_SET_KEY, stale.toArray());
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> fetchAndMarkTraining(String userId, int limit) {
        List<Object> rawIds = fetchScript.execute(
                List.of(idxKey(userId, "pending"), idxKey(userId, "training")),
                List.of(Math.max(1, limit), epochNow(), "training", KEY_PREFIX + ":")
        );
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }

        List<String> sampleIds = rawIds.stream().map(RedisTrajectoryStore::decode).toList();
        RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline = backend.pipeline();
        for (String sampleId : sampleIds) {
            pipeline.hget(trajKey(sampleId), "sample_json");
        }
        List<Object> rows = pipeline.execute();

        List<Map<String, Object>> samples = new ArrayList<>();
        for (Object raw : rows) {
            if (raw == null) {
                continue;
            }
            Map<String, Object> sample = readJson(decode(raw));
            sample.put("_store_status", "training");
            samples.add(sample);
        }
        return samples;
    }

    @Override
    public void markTrained(List<String> sampleIds) {
        updateStatus(sampleIds, "training", "trained");
    }

    @Override
    public void markFailed(List<String> sampleIds) {
        updateStatus(sampleIds, "training", "failed");
    }

    @Override
    public void resetToPending(List<String> sampleIds) {
        updateStatus(sampleIds, "training", "pending");
    }

    @Override
    public Map<String, Integer> stats() {
        Set<Object> members = backend.smembers(USERS_SET_KEY);
        if (members == null || members.isEmpty()) {
            return zeroStats();
        }

        List<String> userIds = members.stream().map(RedisTrajectoryStore::decode).toList();
        RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline = backend.pipeline();
        for (String uid : userIds) {
            pipeline.zcard(idxKey(uid, "pending"));
            pipeline.zcard(idxKey(uid, "training"));
            pipeline.zcard(idxKey(uid, "trained"));
            pipeline.zcard(idxKey(uid, "failed"));
        }
        List<Object> counts = pipeline.execute();

        int pending = 0;
        int training = 0;
        int trained = 0;
        int failed = 0;
        for (int i = 0; i < counts.size(); i += 4) {
            pending += toInt(counts.get(i));
            training += toInt(counts.get(i + 1));
            trained += toInt(counts.get(i + 2));
            failed += toInt(counts.get(i + 3));
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total_samples", pending + training + trained + failed);
        result.put("pending_samples", pending);
        result.put("training_samples", training);
        result.put("trained_samples", trained);
        result.put("failed_samples", failed);
        return result;
    }

    private void updateStatus(List<String> sampleIds, String fromStatus, String toStatus) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            return;
        }

        RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline = backend.pipeline();
        for (String sampleId : sampleIds) {
            pipeline.hmget(trajKey(sampleId), List.of("user_id", "sample_json"));
        }
        List<Object> rows = pipeline.execute();

        List<Transition> transitions = new ArrayList<>();
        for (int i = 0; i < sampleIds.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof List<?> values) || values.isEmpty() || values.get(0) == null) {
                continue;
            }
            String userId = decode(values.get(0));
            String payload = values.size() > 1 ? decode(values.get(1)) : null;
            if (payload == null) {
                LOGGER.warn("Skipping status transition for sample={} due to missing sample_json; keeping {} index unchanged", sampleIds.get(i), fromStatus);
                continue;
            }
            Map<String, Object> sample;
            try {
                sample = readJson(payload);
            } catch (RuntimeException error) {
                LOGGER.warn("Skipping status transition for sample={} due to invalid sample_json; keeping {} index unchanged: {}", sampleIds.get(i), fromStatus, error.getMessage());
                continue;
            }
            sample.put("_store_status", toStatus);
            transitions.add(new Transition(sampleIds.get(i), userId, sample));
        }

        if (transitions.isEmpty()) {
            return;
        }

        double nowScore = epochNow();
        pipeline = backend.pipeline();
        for (Transition transition : transitions) {
            pipeline.zrem(idxKey(transition.userId, fromStatus), transition.sampleId);
            pipeline.zadd(idxKey(transition.userId, toStatus), Map.of(transition.sampleId, nowScore));
            pipeline.hset(trajKey(transition.sampleId), Map.of(
                    "status", toStatus,
                    "sample_json", writeJson(transition.sample)
            ));
        }
        pipeline.execute();
    }

    private static String trajKey(String sampleId) {
        return KEY_PREFIX + ":" + sampleId;
    }

    private static String idxKey(String userId, String status) {
        return IDX_PREFIX + ":" + userId + ":" + status;
    }

    private static String decode(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String pythonOrString(Object first, Object... fallbacks) {
        if (pythonTruthy(first)) {
            return String.valueOf(first);
        }
        for (Object fallback : fallbacks) {
            if (pythonTruthy(fallback)) {
                return String.valueOf(fallback);
            }
        }
        return "";
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double epoch(String isoDateTime) {
        return OffsetDateTime.parse(isoDateTime.replace("Z", "+00:00")).toInstant().toEpochMilli() / 1000.0;
    }

    private static double epochNow() {
        return System.currentTimeMillis() / 1000.0;
    }

    private static Map<String, Integer> zeroStats() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total_samples", 0);
        result.put("pending_samples", 0);
        result.put("training_samples", 0);
        result.put("trained_samples", 0);
        result.put("failed_samples", 0);
        return result;
    }

    private static Map<String, Object> deepCopy(Map<String, Object> sample) {
        if (sample == null) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.convertValue(sample, MAP_TYPE);
    }

    private static String writeJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trajectory sample", e);
        }
    }

    private static Map<String, Object> readJson(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse trajectory sample json", e);
        }
    }

    private record Transition(String sampleId, String userId, Map<String, Object> sample) {
    }
}
