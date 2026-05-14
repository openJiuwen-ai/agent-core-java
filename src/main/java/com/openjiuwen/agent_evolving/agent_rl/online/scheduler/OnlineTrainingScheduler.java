/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import com.openjiuwen.agent_evolving.agent_rl.storage.TrajectorySampleStore;

import java.util.List;
import java.util.Map;

/**
 * Minimal online training scheduler seam.
 * <p>
 * Mirrors Python's {@code OnlineTrainingScheduler} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.scheduler.online_training_scheduler}.
 * <p>
 * This batch intentionally ports only the deterministic train-batch state transition behavior.
 */
public class OnlineTrainingScheduler {

    private final String redisUrl;
    private final double pollInterval;
    private final int minSamplesForTraining;
    private final String baseModelPath;
    private final int nprocPerNode;
    private final String trainingGpuIds;
    private final String tmpRoot;
    private final String ppoConfigPath;

    private int trainingCount;
    private TrajectorySampleStore trajectoryStore;
    private PpoTrainingExecutor trainer;

    public OnlineTrainingScheduler(String redisUrl) {
        this(redisUrl, 30.0, 32, "", 1, "", "/tmp/agent_rl_online", null);
    }

    public OnlineTrainingScheduler(String redisUrl,
                                   double pollInterval,
                                   int minSamplesForTraining,
                                   String baseModelPath,
                                   int nprocPerNode,
                                   String trainingGpuIds,
                                   String tmpRoot,
                                   String ppoConfigPath) {
        this.redisUrl = redisUrl != null ? redisUrl : "redis://127.0.0.1:6379/0";
        this.pollInterval = pollInterval;
        this.minSamplesForTraining = minSamplesForTraining;
        this.baseModelPath = baseModelPath != null ? baseModelPath : "";
        this.nprocPerNode = nprocPerNode;
        this.trainingGpuIds = trainingGpuIds != null ? trainingGpuIds : "";
        this.tmpRoot = tmpRoot != null ? tmpRoot : "/tmp/agent_rl_online";
        this.ppoConfigPath = ppoConfigPath;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public double getPollInterval() {
        return pollInterval;
    }

    public int getMinSamplesForTraining() {
        return minSamplesForTraining;
    }

    public String getBaseModelPath() {
        return baseModelPath;
    }

    public int getNprocPerNode() {
        return nprocPerNode;
    }

    public String getTrainingGpuIds() {
        return trainingGpuIds;
    }

    public String getTmpRoot() {
        return tmpRoot;
    }

    public String getPpoConfigPath() {
        return ppoConfigPath;
    }

    public int getTrainingCount() {
        return trainingCount;
    }

    public void setTrainingCount(int trainingCount) {
        this.trainingCount = trainingCount;
    }

    public TrajectorySampleStore getTrajectoryStore() {
        return trajectoryStore;
    }

    public void setTrajectoryStore(TrajectorySampleStore trajectoryStore) {
        this.trajectoryStore = trajectoryStore;
    }

    public PpoTrainingExecutor getTrainer() {
        return trainer;
    }

    public void setTrainer(PpoTrainingExecutor trainer) {
        this.trainer = trainer;
    }

    public void trainBatch(String userId, List<Map<String, Object>> samples, List<String> sampleIds) {
        if (trajectoryStore == null) {
            throw new IllegalStateException("trajectory store is not initialized");
        }
        if (trainer == null) {
            throw new IllegalStateException("trainer is not initialized");
        }
        try {
            trainer.trainBatch(userId, samples, trainingCount, tmpRoot);
        } catch (RuntimeException error) {
            trajectoryStore.markFailed(sampleIds);
            throw error;
        }
        trajectoryStore.markTrained(sampleIds);
    }
}
