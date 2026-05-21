/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStore;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trajectory persistence and rail-ingest wiring for gateway runtime.
 * <p>
 * Mirrors Python's {@code GatewayTrajectoryRuntime} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.persistence}.
 */
public class GatewayTrajectoryRuntime implements SampleRecordingSink {

    private static final String SINGLE_USER_DEFAULT_ID = "jiuwenclaw-web";

    private final String defaultUserId;
    private final RedisTrajectoryStore trajectoryStore;
    private final SampleRecorder sampleRecorder;
    private final PendingJudgeStore pendingJudgeStore;
    private RailBatchIngestor railIngestor;

    public GatewayTrajectoryRuntime(GatewayConfig config, RedisTrajectoryStoreBackend redisBackend) {
        if (redisBackend == null) {
            throw new IllegalArgumentException("GatewayTrajectoryRuntime requires redis client");
        }
        try {
            Files.createDirectories(Path.of(config.getRecordDir()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create record directory", exception);
        }
        this.defaultUserId = config.isSingleUserDefault() ? SINGLE_USER_DEFAULT_ID : "";
        this.trajectoryStore = new RedisTrajectoryStore(redisBackend);
        this.sampleRecorder = new SampleRecorder(Path.of(config.getRecordDir(), "samples.jsonl"), config.isDumpTokenIds());
        this.pendingJudgeStore = new InMemoryPendingJudgeStore();
        setJudgeScorer(null);
    }

    public String getStoreBackend() {
        return trajectoryStore.getClass().getSimpleName();
    }

    public RailBatchIngestor getRailIngestor() {
        if (railIngestor == null) {
            throw new IllegalStateException("rail_ingestor is not initialized");
        }
        return railIngestor;
    }

    public void setJudgeScorer(JudgeScorer judgeScorer) {
        JudgeDispatcher judgeDispatcher = new JudgeDispatcher(pendingJudgeStore, this, judgeScorer);
        this.railIngestor = new RailBatchIngestor(pendingJudgeStore, judgeDispatcher, defaultUserId);
    }

    public void recordSample(Map<String, Object> sample) {
        Map<String, Object> normalized = new LinkedHashMap<>(sample);
        String normalizedUserId = String.valueOf(normalized.getOrDefault("user_id", defaultUserId)).trim();
        if (normalizedUserId.isBlank()) {
            throw new IllegalArgumentException("missing user_id; online training requires a stable user id");
        }
        normalized.put("user_id", normalizedUserId);
        trajectoryStore.saveSample(normalized, normalizedUserId);
        sampleRecorder.recordSample(normalized);
    }

    public Map<String, Object> snapshotStats() {
        Map<String, Integer> sampleStats = sampleRecorder.snapshotStats();
        Map<String, Integer> trainStats = trajectoryStore.stats();
        return Map.of(
                "total_samples", sampleStats.get("total_samples"),
                "trajectory_store_backend", getStoreBackend(),
                "trajectory_store_total", trainStats.get("total_samples"),
                "trajectory_store_pending", trainStats.get("pending_samples"),
                "trajectory_store_training", trainStats.get("training_samples"),
                "trajectory_store_trained", trainStats.get("trained_samples"),
                "trajectory_store_failed", trainStats.get("failed_samples")
        );
    }

    static final class InMemoryPendingJudgeStore extends PendingJudgeStore {
        private final Map<String, java.util.List<Map<String, Object>>> bySession = new LinkedHashMap<>();

        public void put(Map<String, Object> sample) {
            String sessionId = String.valueOf(sample.getOrDefault("session_id", ""));
            bySession.computeIfAbsent(sessionId, ignored -> new java.util.ArrayList<>()).add(sample);
        }

        public Map<String, Object> popEarliest(String sessionId) {
            java.util.List<Map<String, Object>> samples = bySession.get(sessionId);
            if (samples == null || samples.isEmpty()) {
                return null;
            }
            return samples.removeFirst();
        }

        public java.util.List<Map<String, Object>> popAll(String sessionId) {
            java.util.List<Map<String, Object>> samples = bySession.remove(sessionId);
            return samples != null ? samples : java.util.List.of();
        }
    }
}
