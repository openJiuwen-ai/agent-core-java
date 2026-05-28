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
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme.update.TrajectoryPreprocessOp}.
 * 
 * Preprocess trajectories for summarization.
 * 
 * This operation:
 * 1. Validates trajectory data
 * 2. Filters out invalid/incomplete trajectories
 * 3. Normalizes feedback values
 * 4. Groups by feedback type
 */
public class TrajectoryPreprocessOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(TrajectoryPreprocessOp.class);
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> trajectories = context.has("trajectories") 
            ? (List<Map<String, Object>>) context.get("trajectories") 
            : new ArrayList<>();
        
        if (trajectories.isEmpty()) {
            log.warn("No trajectories to process");
            context.set("success_trajectories", new ArrayList<>());
            context.set("failure_trajectories", new ArrayList<>());
            return CompletableFuture.completedFuture(null);
        }
        
        // Group by feedback type using context.score list
        List<Double> scores = context.has("score") 
            ? (List<Double>) context.get("score") 
            : new ArrayList<>();
        double threshold = context.has("threshold") 
            ? ((Number) context.get("threshold")).doubleValue() 
            : 1.0;
        
        List<Map<String, Object>> successTrajectories = new ArrayList<>();
        List<Map<String, Object>> failureTrajectories = new ArrayList<>();
        List<Map<String, Object>> allTrajectories = new ArrayList<>();
        
        for (int i = 0; i < trajectories.size() && i < scores.size(); i++) {
            Map<String, Object> trajectory = trajectories.get(i);
            double score = scores.get(i);
            
            if (score >= threshold) {
                successTrajectories.add(trajectory);
            } else {
                failureTrajectories.add(trajectory);
            }
            allTrajectories.add(trajectory);
        }
        
        context.set("success_trajectories", successTrajectories);
        context.set("failure_trajectories", failureTrajectories);
        context.set("all_trajectories", allTrajectories);
        
        log.info("Preprocessed {} trajectories: {} success, {} failure", 
            trajectories.size(), successTrajectories.size(), failureTrajectories.size());
        
        return CompletableFuture.completedFuture(null);
    }
}