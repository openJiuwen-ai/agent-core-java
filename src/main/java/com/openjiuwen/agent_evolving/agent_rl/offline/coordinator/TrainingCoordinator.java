/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core loop coordinator handling task submission, rollout collection,
 * stop-condition checking, and construction of training-ready RL batches.
 * <p>
 * Mirrors Python's {@code TrainingCoordinator} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.training_coordinator}.
 */
public class TrainingCoordinator {

    private Object config;
    private Object tokenizer;
    private Object persistence;
    
    private int totalPositive = 0;
    private int totalNegative = 0;
    private int totalActivateNum = 0;
    private List<Map<String, Object>> roundState = new ArrayList<>();
    private Map<String, Map<String, Object>> rolloutState = new HashMap<>();
    private Map<String, List<RolloutWithReward>> positiveCache = new HashMap<>();
    private Map<String, List<RolloutWithReward>> negativeCache = new HashMap<>();
    
    private TaskQueue datastore;
    private ProcessorsRegistry processorsRegistry;
    private RLBatchBuilder batchBuilder;
    private RolloutEncoder encoder;
    private Object parallelExecutor;

    public TrainingCoordinator(Object config, Object tokenizer, Object persistence) {
        this.config = config;
        this.tokenizer = tokenizer;
        this.persistence = persistence;
        
        this.datastore = new TaskQueue();
        this.processorsRegistry = new ProcessorsRegistry();
        this.encoder = new RolloutEncoder(tokenizer);
        
        // TODO: Initialize batch builder with actual config values
        this.batchBuilder = new RLBatchBuilder(3072, 0, 3072);
    }

    /**
     * Submit a new task for rollout generation.
     * 
     * @param task RL task to submit
     * @return Task ID
     */
    public String submitTask(RLTask task) {
        return datastore.queueTask(task);
    }

    /**
     * Submit a new task using prompt data.
     * 
     * @param promptId Prompt identifier
     * @param promptData Prompt data
     * @return Task ID
     */
    public String submitPrompt(String promptId, Map<String, Object> promptData) {
        RLTask task = new RLTask(promptId, promptId, promptData, 0);
        return datastore.queueTask(task);
    }

    /**
     * Collect completed rollouts and check stop conditions.
     * 
     * @return List of collected rollouts
     */
    public List<RolloutMessage> collectRollouts() {
        Map<String, RolloutMessage> rollouts = datastore.getRollouts();
        return new ArrayList<>(rollouts.values());
    }

    /**
     * Check if training should stop based on collected rollouts.
     * 
     * @return true if should stop
     */
    public boolean shouldStop() {
        // TODO: Implement actual stop logic using processorsRegistry
        return false;
    }

    /**
     * Build training batch from collected rollouts.
     * 
     * @return Training batch object
     */
    public Object buildBatch() {
        // TODO: Implement batch building
        return null;
    }

    /**
     * Run one training iteration.
     */
    public void runIteration() {
        // TODO: Implement full iteration cycle
        // 1. Get tasks from queue
        // 2. Execute rollouts
        // 3. Collect results
        // 4. Check stop conditions
        // 5. Build batch if ready
    }

    /**
     * Get statistics.
     * 
     * @return Statistics map
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_positive", totalPositive);
        stats.put("total_negative", totalNegative);
        stats.put("total_activate_num", totalActivateNum);
        stats.put("round_count", roundState.size());
        stats.put("queue_size", datastore.getQueueSize());
        stats.put("in_processing_count", datastore.getInProcessingCount());
        stats.put("rollout_count", datastore.getRolloutCount());
        return stats;
    }

    public Object getConfig() { return config; }
    public TaskQueue getDatastore() { return datastore; }
    public ProcessorsRegistry getProcessorsRegistry() { return processorsRegistry; }
    public RLBatchBuilder getBatchBuilder() { return batchBuilder; }
    public RolloutEncoder getEncoder() { return encoder; }
}