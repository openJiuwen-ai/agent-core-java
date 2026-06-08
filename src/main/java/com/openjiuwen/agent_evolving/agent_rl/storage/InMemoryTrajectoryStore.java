/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight in-memory trajectory sample store.
 * <p>
 * Mirrors Python's {@code InMemoryTrajectoryStore} in
 * {@code openjiuwen/agent_evolving/agent_rl/storage/trajectory_store.py}.
 */
public class InMemoryTrajectoryStore implements TrajectorySampleStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_USER_ID = "online";

    private final Map<String, Map<String, Object>> samples = new LinkedHashMap<>();
    private final Map<String, Map<String, List<String>>> statusIndex = new LinkedHashMap<>();

    @Override
    public synchronized void saveSample(Map<String, Object> sample, String userId) {
        String sampleId = String.valueOf(sample != null ? sample.getOrDefault("sample_id", "") : "").trim();
        if (sampleId.isEmpty()) {
            throw new IllegalArgumentException("sample_id is required");
        }

        Map<String, Object> normalized = deepCopy(sample);
        String normalizedUserId = pythonOrString(normalized.get("user_id"), userId, DEFAULT_USER_ID);
        normalized.put("user_id", normalizedUserId);
        normalized.put("_store_status", "pending");

        Map<String, Object> old = samples.get(sampleId);
        if (old != null) {
            removeFromStatusIndex(sampleId, String.valueOf(old.get("user_id")), String.valueOf(old.get("_store_status")));
        }
        samples.put(sampleId, normalized);
        addToStatusIndex(sampleId, normalizedUserId, "pending");
    }

    @Override
    public synchronized int getPendingCount(String userId) {
        return statusIndex.getOrDefault(userId, Map.of()).getOrDefault("pending", List.of()).size();
    }

    @Override
    public synchronized List<String> getUsersAboveThreshold(int threshold) {
        List<String> users = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : statusIndex.entrySet()) {
            if (entry.getValue().getOrDefault("pending", List.of()).size() >= threshold) {
                users.add(entry.getKey());
            }
        }
        return users;
    }

    @Override
    public synchronized List<Map<String, Object>> fetchAndMarkTraining(String userId, int limit) {
        List<String> pending = new ArrayList<>(statusIndex.getOrDefault(userId, Map.of()).getOrDefault("pending", List.of()));
        int cappedLimit = Math.max(1, limit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String sampleId : pending.subList(0, Math.min(cappedLimit, pending.size()))) {
            Map<String, Object> sample = samples.get(sampleId);
            if (sample == null) {
                continue;
            }
            removeFromStatusIndex(sampleId, userId, "pending");
            addToStatusIndex(sampleId, userId, "training");
            sample.put("_store_status", "training");
            out.add(deepCopy(sample));
        }
        return out;
    }

    @Override
    public synchronized void markTrained(List<String> sampleIds) {
        updateStatus(sampleIds, "training", "trained");
    }

    @Override
    public synchronized void markFailed(List<String> sampleIds) {
        updateStatus(sampleIds, "training", "failed");
    }

    @Override
    public synchronized void resetToPending(List<String> sampleIds) {
        updateStatus(sampleIds, "training", "pending");
    }

    @Override
    public synchronized Map<String, Integer> stats() {
        int pending = 0;
        int training = 0;
        int trained = 0;
        int failed = 0;
        for (Map<String, List<String>> statuses : statusIndex.values()) {
            pending += statuses.getOrDefault("pending", List.of()).size();
            training += statuses.getOrDefault("training", List.of()).size();
            trained += statuses.getOrDefault("trained", List.of()).size();
            failed += statuses.getOrDefault("failed", List.of()).size();
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
        if (sampleIds == null) {
            return;
        }
        for (String sampleId : sampleIds) {
            Map<String, Object> sample = samples.get(sampleId);
            if (sample == null) {
                continue;
            }
            String userId = pythonOrString(sample.get("user_id"), DEFAULT_USER_ID);
            removeFromStatusIndex(sampleId, userId, fromStatus);
            addToStatusIndex(sampleId, userId, toStatus);
            sample.put("_store_status", toStatus);
        }
    }

    private void addToStatusIndex(String sampleId, String userId, String status) {
        Map<String, List<String>> userStatuses = statusIndex.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
        List<String> bucket = userStatuses.computeIfAbsent(status, ignored -> new ArrayList<>());
        if (!bucket.contains(sampleId)) {
            bucket.add(sampleId);
        }
    }

    private void removeFromStatusIndex(String sampleId, String userId, String status) {
        List<String> bucket = statusIndex.getOrDefault(userId, Map.of()).get(status);
        if (bucket != null) {
            bucket.remove(sampleId);
        }
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

    private static Map<String, Object> deepCopy(Map<String, Object> sample) {
        if (sample == null) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.convertValue(sample, MAP_TYPE);
    }
}
