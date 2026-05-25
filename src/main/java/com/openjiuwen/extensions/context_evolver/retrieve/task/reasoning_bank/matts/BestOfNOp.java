/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank.matts;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank.matts.BestOfNOp}.
 * 
 * Best-of-N selection: Select the best trajectory from multiple candidates.
 * 
 * This operation evaluates multiple trajectories and selects the best one
 * using an LLM-based evaluator.
 */
public class BestOfNOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(BestOfNOp.class);
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (!context.has("parallel_trajectories")) {
            log.warn("No parallel trajectories found for Best-of-N selection");
            return CompletableFuture.completedFuture(null);
        }
        
        List<Map<String, Object>> trajectories = (List<Map<String, Object>>) context.get("parallel_trajectories");
        if (trajectories.isEmpty()) {
            log.warn("Empty trajectories list for Best-of-N selection");
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Selecting best trajectory from {} candidates", trajectories.size());
        
        String query = context.getQuery();
        int numTrajectories = trajectories.size();
        
        // Build trajectory descriptions
        List<String> trajDescriptions = new ArrayList<>();
        for (int i = 0; i < trajectories.size(); i++) {
            Map<String, Object> traj = trajectories.get(i);
            String answer = (String) traj.getOrDefault("answer", "");
            String truncatedAnswer = answer.length() > 200 ? answer.substring(0, 200) + "..." : answer;
            trajDescriptions.add("Trajectory " + i + ": " + truncatedAnswer);
        }
        
        // Build evaluation prompt
        StringBuilder evalPrompt = new StringBuilder();
        evalPrompt.append("You will be given the user query and ").append(numTrajectories)
            .append(" candidate trajectories.\n");
        evalPrompt.append("Your job is to select the single best trajectory that most effectively ");
        evalPrompt.append("and efficiently solves the task.\n\n");
        evalPrompt.append("Query: ").append(query).append("\n\n");
        
        for (String desc : trajDescriptions) {
            evalPrompt.append(desc).append("\n");
        }
        
        evalPrompt.append("\n## Evaluation Criteria:\n");
        evalPrompt.append("1. Progress Toward Goal: How well the trajectory advances toward completing the task\n");
        evalPrompt.append("2. Trajectory Efficiency: How efficiently progress is achieved given number of steps\n");
        evalPrompt.append("3. Error Severity: Assess fatal, significant, or minor errors\n");
        evalPrompt.append("4. Overall Quality: Logical flow, coherence, and closeness to goal\n\n");
        evalPrompt.append("Return ONLY the index (0-").append(trajectories.size() - 1)
            .append(") of the best trajectory.");
        
        // Get LLM evaluation
        try {
            Object llm = getLlm();
            // In a proper implementation, this would call the LLM
            // Placeholder: use first trajectory
            int bestIdx = 0;
            
            // Extract index from response (placeholder logic)
            for (int i = 0; i < trajectories.size(); i++) {
                // This would parse the LLM response in actual implementation
                if (i == 0) {
                    bestIdx = i;
                    break;
                }
            }
            
            log.info("Selected trajectory {} as best", bestIdx);
            
            // Set the best trajectory as the result
            Map<String, Object> bestTraj = trajectories.get(bestIdx);
            context.set("answer", bestTraj.getOrDefault("answer", ""));
            context.set("best_trajectory_index", bestIdx);
            context.set("best_trajectory", bestTraj);
            
            // Calculate Pass@k - count how many trajectories succeeded
            int successCount = 0;
            for (Map<String, Object> t : trajectories) {
                if ((boolean) t.getOrDefault("success", false)) {
                    successCount++;
                }
            }
            double passAtK = trajectories.isEmpty() ? 0.0 : successCount / trajectories.size();
            context.set("pass_at_k", passAtK);
            
        } catch (Exception e) {
            log.error("Error in Best-of-N selection: {}", e.getMessage());
            // Fallback: use first trajectory
            Map<String, Object> firstTraj = trajectories.get(0);
            context.set("answer", firstTraj.getOrDefault("answer", ""));
            context.set("best_trajectory_index", 0);
            context.set("best_trajectory", firstTraj);
        }
        
        return CompletableFuture.completedFuture(null);
    }
}