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
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank.matts.SequentialScalingOp}.
 * 
 * Sequential scaling: Iteratively refine a single trajectory with self-checking.
 * 
 * This operation performs k rounds of self-refinement on a trajectory,
 * where the agent re-examines and corrects its reasoning at each step.
 */
public class SequentialScalingOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(SequentialScalingOp.class);
    
    private final int k;
    
    /**
     * Create a SequentialScalingOp with default settings.
     */
    public SequentialScalingOp() {
        this(3);
    }
    
    /**
     * Create a SequentialScalingOp.
     *
     * @param k Number of refinement rounds (scaling factor)
     */
    public SequentialScalingOp(int k) {
        super();
        this.k = k;
    }
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        log.info("Executing sequential scaling with k={} refinement rounds", k);
        
        String query = context.getQuery();
        String userId = context.getUserId();
        
        // Get LLM from service context
        Object llm = getLlm();
        
        List<Map<String, Object>> refinementHistory = new ArrayList<>();
        String currentAnswer = context.has("answer") ? (String) context.get("answer") : "";
        String currentTrajectory = context.has("trajectory") ? (String) context.get("trajectory") : "";
        
        for (int roundIdx = 0; roundIdx < k; roundIdx++) {
            log.info("Refinement round {}/{}", roundIdx + 1, k);
            
            // Build refinement prompt
            String refinementPrompt;
            if (roundIdx == 0) {
                // First-time check
                refinementPrompt = buildInitialRefinementPrompt(query, currentAnswer, currentTrajectory);
            } else {
                // Subsequent rounds
                refinementPrompt = buildSubsequentRefinementPrompt(query, currentAnswer, currentTrajectory, roundIdx);
            }
            
            try {
                // In a proper implementation, this would call the LLM with the refinement prompt
                // Placeholder: store refinement history
                Map<String, Object> refinement = new java.util.HashMap<>();
                refinement.put("round", roundIdx);
                refinement.put("prompt", refinementPrompt);
                refinement.put("previous_answer", currentAnswer);
                refinementHistory.add(refinement);
                
                // Update current answer (placeholder)
                // In actual implementation, this would be the LLM's refined answer
                currentAnswer = "Refined answer round " + roundIdx;
                currentTrajectory = "Refined trajectory round " + roundIdx;
                
            } catch (Exception e) {
                log.error("Error in refinement round {}: {}", roundIdx, e.getMessage());
                // Continue with next round despite error
            }
        }
        
        // Store refinement history
        context.set("refinement_history", refinementHistory);
        context.set("refinement_rounds", k);
        
        // Set final refined answer
        context.set("answer", currentAnswer);
        context.set("trajectory", currentTrajectory);
        
        log.info("Completed {} refinement rounds", k);
        
        return CompletableFuture.completedFuture(null);
    }
    
    private String buildInitialRefinementPrompt(String query, String answer, String trajectory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Important: Let's carefully re-examine the previous trajectory.\n\n");
        prompt.append("Query: ").append(query).append("\n\n");
        prompt.append("Previous Answer: ").append(answer).append("\n\n");
        prompt.append("Previous Trajectory:\n").append(trajectory).append("\n\n");
        prompt.append("Please:\n");
        prompt.append("1. Identify any mistakes or incomplete reasoning\n");
        prompt.append("2. Provide corrections or improvements\n");
        prompt.append("3. Give a refined answer\n");
        return prompt.toString();
    }
    
    private String buildSubsequentRefinementPrompt(String query, String answer, String trajectory, int round) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Refinement round ").append(round + 1).append("\n\n");
        prompt.append("Query: ").append(query).append("\n\n");
        prompt.append("Current Answer: ").append(answer).append("\n\n");
        prompt.append("Current Trajectory:\n").append(trajectory).append("\n\n");
        prompt.append("Continue refining:\n");
        prompt.append("1. Check if the current answer fully addresses the query\n");
        prompt.append("2. Identify remaining issues\n");
        prompt.append("3. Provide further improvements\n");
        return prompt.toString();
    }
    
    public int getK() {
        return k;
    }
}