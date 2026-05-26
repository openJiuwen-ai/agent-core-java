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
        
        // Initialize batch builder with config values
        // Mirrors Python: batch_builder = RLBatchBuilder(max_prompt_length, pad_token_id, max_response_length)
        int maxPromptLength = getConfigValue("data.max_prompt_length", 3072);
        int padTokenId = getConfigValue("data.pad_token_id", 0);
        int maxResponseLength = getConfigValue("data.max_response_length", 3072);
        this.batchBuilder = new RLBatchBuilder(maxPromptLength, padTokenId, maxResponseLength);
    }
    
    /**
     * Helper method to extract config values.
     * Mirrors Python's nested dict access pattern.
     */
    @SuppressWarnings("unchecked")
    private int getConfigValue(String path, int defaultValue) {
        try {
            if (config instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) config;
                String[] parts = path.split("\\.");
                Object current = configMap;
                for (String part : parts) {
                    if (current instanceof Map) {
                        current = ((Map<String, Object>) current).get(part);
                    } else {
                        return defaultValue;
                    }
                }
                if (current instanceof Number) {
                    return ((Number) current).intValue();
                }
            }
        } catch (Exception e) {
            // Fall back to default value
        }
        return defaultValue;
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
     * <p>
     * Mirrors Python's _rollout_state checking logic in _filter_unfinished_tasks.
     * Returns true when all tasks in rollout_state are marked as finished.
     * 
     * @return true if should stop (all tasks finished)
     */
    public boolean shouldStop() {
        // Check if all tasks have finished based on rollout_state
        // Mirrors Python: is_finish check in _update_rollout_state
        if (rolloutState.isEmpty()) {
            return false;
        }
        
        for (Map<String, Object> state : rolloutState.values()) {
            Boolean finished = (Boolean) state.get("finished");
            if (finished == null || !finished) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build training batch from collected rollouts.
     * <p>
     * Mirrors Python's _build_rl_batch_from_caches method:
     * 1. Sample rollouts from positive and negative caches using processorsRegistry sampler
     * 2. Merge caches into unified dict
     * 3. Generate RL batch using batchBuilder
     * 
     * @return Training batch object (RLBatch)
     */
    public Object buildBatch() {
        // Mirrors Python: sampling_func = processors_registry.get_sampler(config["JiuwenRL"]["custom_fn"]["sampler"])
        Object sampler = processorsRegistry.getSampler("default_sampling");
        if (sampler == null) {
            // Default: just merge caches without sampling
            Map<String, List<RolloutWithReward>> mergedDict = mergeCaches(positiveCache, negativeCache);
            return buildBatchFromMerged(mergedDict);
        }
        
        // Sample from caches using sampler function
        // Mirrors Python: pos_rollout_dict, neg_rollout_dict = sampling_func(positive_cache, negative_cache)
        Map<String, List<RolloutWithReward>> sampledPos = new HashMap<>();
        Map<String, List<RolloutWithReward>> sampledNeg = new HashMap<>();
        
        // TODO: Apply sampler function when sampler interface is properly defined
        // For now, use caches directly
        
        Map<String, List<RolloutWithReward>> mergedDict = mergeCaches(positiveCache, negativeCache);
        return buildBatchFromMerged(mergedDict);
    }
    
    /**
     * Build batch from merged rollout dict.
     * Placeholder implementation.
     */
    private Object buildBatchFromMerged(Map<String, List<RolloutWithReward>> mergedDict) {
        // Placeholder - would call batchBuilder.generateBatch in full implementation
        return mergedDict;
    }
    
    /**
     * Merge positive and negative rollout caches into a unified dict keyed by task ID.
     * <p>
     * Mirrors Python's static merge_caches method.
     */
    private Map<String, List<RolloutWithReward>> mergeCaches(
            Map<String, List<RolloutWithReward>> posCache,
            Map<String, List<RolloutWithReward>> negCache) {
        Map<String, List<RolloutWithReward>> merged = new HashMap<>();
        
        // Get all unique task IDs from both caches
        java.util.Set<String> allKeys = new java.util.HashSet<>();
        allKeys.addAll(posCache.keySet());
        allKeys.addAll(negCache.keySet());
        
        for (String key : allKeys) {
            List<RolloutWithReward> combined = new ArrayList<>();
            if (posCache.containsKey(key)) {
                combined.addAll(posCache.get(key));
            }
            if (negCache.containsKey(key)) {
                combined.addAll(negCache.get(key));
            }
            merged.put(key, combined);
        }
        return merged;
    }

    /**
     * Run one training iteration.
     * <p>
     * Mirrors Python's demon loop iteration cycle:
     * 1. Get tasks from queue (submit_tasks_for_round)
     * 2. Execute rollouts (via parallel_executor)
     * 3. Collect results (collect_rollouts)
     * 4. Check stop conditions (should_stop)
     * 5. Build batch if ready (build_batch)
     */
    public void runIteration() {
        // Step 1: Get tasks from queue
        List<RLTask> pendingTasks = datastore.getPendingTasks();
        if (pendingTasks.isEmpty()) {
            return;
        }
        
        // Step 2: Submit tasks for execution
        // Mirrors Python: await self._submit_tasks_for_round(tasks_dic)
        for (RLTask task : pendingTasks) {
            datastore.markInProcessing(task.getTaskId());
        }
        
        // Step 3: Collect completed rollouts
        // Mirrors Python: collected_data = await self._wait_for_tasks_completion(round_id)
        List<RolloutMessage> rollouts = collectRollouts();
        
        // Step 4: Update rollout state from collected results
        for (RolloutMessage rollout : rollouts) {
            String taskId = rollout.getOriginTaskId();
            
            // Classify rollouts using processors_registry
            List<RolloutWithReward> encoded;
            if (rollout.getRolloutInfo() != null && !rollout.getRolloutInfo().isEmpty()) {
                encoded = encoder.build(rollout);
            } else {
                encoded = new ArrayList<>();
            }
            
            // Update caches
            List<RolloutWithReward> positiveRollouts = new ArrayList<>();
            List<RolloutWithReward> negativeRollouts = new ArrayList<>();
            
            // Simple classification: reward >= 0.5 is positive
            for (RolloutWithReward r : encoded) {
                if (r.getReward() != null && r.getReward() >= 0.5) {
                    positiveRollouts.add(r);
                } else {
                    negativeRollouts.add(r);
                }
            }
            
            if (!positiveRollouts.isEmpty()) {
                positiveCache.computeIfAbsent(taskId, k -> new ArrayList<>()).addAll(positiveRollouts);
                totalPositive += positiveRollouts.size();
            }
            if (!negativeRollouts.isEmpty()) {
                negativeCache.computeIfAbsent(taskId, k -> new ArrayList<>()).addAll(negativeRollouts);
                totalNegative += negativeRollouts.size();
            }
            
            // Update rollout state
            Map<String, Object> state = rolloutState.computeIfAbsent(taskId, k -> {
                Map<String, Object> s = new HashMap<>();
                s.put("pos", 0);
                s.put("neg", 0);
                s.put("finished", false);
                return s;
            });
            state.put("pos", (Integer) state.get("pos") + positiveRollouts.size());
            state.put("neg", (Integer) state.get("neg") + negativeRollouts.size());
            
            // Check if task is finished using validator
            var validator = processorsRegistry.getValidator("default_validate_stop");
            boolean isFinished = validator != null && validator.apply(
                positiveCache.getOrDefault(taskId, new ArrayList<>()),
                negativeCache.getOrDefault(taskId, new ArrayList<>())
            );
            state.put("finished", isFinished);
            
            totalActivateNum++;
        }
        
        // Step 5: Record round state
        Map<String, Object> roundInfo = new HashMap<>();
        roundInfo.put("round_id", roundState.size());
        roundInfo.put("active_num", pendingTasks.size());
        roundInfo.put("total_activate_num", totalActivateNum);
        roundState.add(roundInfo);
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