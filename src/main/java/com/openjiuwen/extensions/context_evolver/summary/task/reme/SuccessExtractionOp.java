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
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme.update.SuccessExtractionOp}.
 * 
 * Extract insights from successful trajectories.
 */
public class SuccessExtractionOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(SuccessExtractionOp.class);
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> successTrajectories = context.has("success_trajectories")
            ? (List<Map<String, Object>>) context.get("success_trajectories")
            : new ArrayList<>();
        
        if (successTrajectories.isEmpty()) {
            log.warn("No successful trajectories to extract from");
            context.set("success_memories", new ArrayList<>());
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Extracting insights from {} successful trajectories", successTrajectories.size());
        
        List<Map<String, Object>> successMemories = new ArrayList<>();
        
        for (Map<String, Object> trajectory : successTrajectories) {
            // In a proper implementation, this would call the LLM to extract insights
            // Placeholder: create a simple memory item
            Map<String, Object> memory = new java.util.HashMap<>();
            memory.put("type", "success");
            memory.put("trajectory_id", trajectory.getOrDefault("id", "unknown"));
            memory.put("content", "Success pattern extracted from trajectory");
            successMemories.add(memory);
        }
        
        context.set("success_memories", successMemories);
        
        log.info("Extracted {} success memories", successMemories.size());
        
        return CompletableFuture.completedFuture(null);
    }
}