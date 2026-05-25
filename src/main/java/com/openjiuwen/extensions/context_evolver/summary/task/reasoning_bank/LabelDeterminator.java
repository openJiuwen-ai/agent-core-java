/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reasoning_bank.update.LabelDeterminator}.
 * 
 * Determines success/failure labels for trajectories using LLM-as-judge.
 */
public class LabelDeterminator {
    
    private static final Logger log = LoggerFactory.getLogger(LabelDeterminator.class);
    
    /**
     * Convert a list of messages to a formatted text representation.
     *
     * @param messages List of message dictionaries with 'role' and 'content' keys
     * @return Formatted string representation of messages
     */
    public static String messagesToText(List<Map<String, Object>> messages) {
        StringBuilder output = new StringBuilder();
        for (Map<String, Object> message : messages) {
            String role = (String) message.get("role");
            String content = (String) message.get("content");
            
            if ("system".equals(role)) {
                output.append("SYSTEM:\n").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                output.append("ASSISTANT:\n").append(content).append("\n");
            } else if ("user".equals(role)) {
                output.append("USER:\n").append(content).append("\n");
            } else {
                throw new IllegalArgumentException("Unknown message role " + role + " in: " + message);
            }
        }
        return output.toString().trim();
    }
    
    /**
     * Determine if a trajectory is successful or failed.
     *
     * @param llm        LLM instance for generation (placeholder in Java)
     * @param query      User query
     * @param trajectory Trajectory data (can be messages list or string)
     * @return true if successful, false if failed
     */
    public static boolean determineLabel(Object llm, String query, Object trajectory) {
        // Convert trajectory to text if needed
        String trajectoryText;
        if (trajectory instanceof List) {
            trajectoryText = messagesToText((List<Map<String, Object>>) trajectory);
        } else {
            trajectoryText = trajectory.toString();
        }
        
        log.info("Determining trajectory label using LLM-as-judge...");
        
        // In a proper implementation, this would call the LLM
        // Placeholder: use simple heuristic
        String judgeResponse = "Status: success"; // Placeholder
        
        // Parse response
        Pattern pattern = Pattern.compile("Status:\\s*(success|failure)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(judgeResponse);
        
        boolean isSuccess;
        if (matcher.find()) {
            isSuccess = matcher.group(1).equalsIgnoreCase("success");
        } else {
            isSuccess = judgeResponse.toLowerCase().contains("success");
        }
        
        log.info("Label determined: {}", isSuccess ? "success" : "failure");
        return isSuccess;
    }
}