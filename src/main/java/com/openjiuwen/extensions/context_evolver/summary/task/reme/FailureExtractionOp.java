/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme.update.FailureExtractionOp}.
 * 
 * Extract lessons from failed trajectories.
 */
public class FailureExtractionOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(FailureExtractionOp.class);
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> failureTrajectories = context.has("failure_trajectories")
            ? (List<Map<String, Object>>) context.get("failure_trajectories")
            : new ArrayList<>();
        
        if (failureTrajectories.isEmpty()) {
            log.warn("No failed trajectories to extract from");
            context.set("failure_memories", new ArrayList<>());
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Extracting lessons from {} failed trajectories", failureTrajectories.size());
        
        List<Map<String, Object>> failureMemories = new ArrayList<>();
        
        for (Map<String, Object> trajectory : failureTrajectories) {
            // In a proper implementation, this would call the LLM to extract lessons
            // Placeholder: create a simple memory item
            Map<String, Object> memory = new java.util.HashMap<>();
            memory.put("type", "failure");
            memory.put("trajectory_id", trajectory.getOrDefault("id", "unknown"));
            memory.put("content", "Failure lesson extracted from trajectory");
            failureMemories.add(memory);
        }
        
        context.set("failure_memories", failureMemories);
        
        log.info("Extracted {} failure memories", failureMemories.size());
        
        return CompletableFuture.completedFuture(null);
    }
}