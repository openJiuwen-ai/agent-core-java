/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard MDP data unit used by the training framework.
 * <p>
 * Represents one (input, output, reward) triple at token level
 * after tokenisation.
 * <p>
 * Mirrors Python's {@code RolloutWithReward} in
 * {@code openjiuwen.agent_evolving.agent_rl.schemas}.
 */
public class RolloutWithReward {

    private Integer turnId;
    private String taskId;
    private String rolloutId;
    private List<Integer> inputPromptIds = new ArrayList<>();
    private List<Integer> outputResponseIds = new ArrayList<>();
    private Double reward;
    private Integer nTurns;
    private List<Integer> lossMask; // Per-token loss mask for whole-trajectory mode

    public RolloutWithReward() {
    }

    public RolloutWithReward(List<Integer> inputPromptIds, List<Integer> outputResponseIds) {
        this.inputPromptIds = inputPromptIds != null ? new ArrayList<>(inputPromptIds) : new ArrayList<>();
        this.outputResponseIds = outputResponseIds != null ? new ArrayList<>(outputResponseIds) : new ArrayList<>();
    }
    
    /**
     * Full constructor for RolloutWithReward.
     */
    public RolloutWithReward(Integer turnId, String taskId, String rolloutId, 
                            List<Integer> inputPromptIds, List<Integer> outputResponseIds,
                            Double reward, Integer nTurns) {
        this.turnId = turnId;
        this.taskId = taskId;
        this.rolloutId = rolloutId;
        this.inputPromptIds = inputPromptIds != null ? new ArrayList<>(inputPromptIds) : new ArrayList<>();
        this.outputResponseIds = outputResponseIds != null ? new ArrayList<>(outputResponseIds) : new ArrayList<>();
        this.reward = reward;
        this.nTurns = nTurns;
    }

    // Getters and setters
    public Integer getTurnId() { return turnId; }
    public void setTurnId(Integer turnId) { this.turnId = turnId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getRolloutId() { return rolloutId; }
    public void setRolloutId(String rolloutId) { this.rolloutId = rolloutId; }
    public List<Integer> getInputPromptIds() { return inputPromptIds; }
    public void setInputPromptIds(List<Integer> inputPromptIds) { 
        this.inputPromptIds = inputPromptIds != null ? new ArrayList<>(inputPromptIds) : new ArrayList<>(); 
    }
    public List<Integer> getOutputResponseIds() { return outputResponseIds; }
    public void setOutputResponseIds(List<Integer> outputResponseIds) { 
        this.outputResponseIds = outputResponseIds != null ? new ArrayList<>(outputResponseIds) : new ArrayList<>(); 
    }
    public Double getReward() { return reward; }
    public void setReward(Double reward) { this.reward = reward; }
    public Integer getNTurns() { return nTurns; }
    public void setNTurns(Integer nTurns) { this.nTurns = nTurns; }
    public List<Integer> getLossMask() { return lossMask; }
    public void setLossMask(List<Integer> lossMask) { 
        this.lossMask = lossMask != null ? new ArrayList<>(lossMask) : null; 
    }

    /**
     * Check if this is a positive rollout (reward > 0).
     * 
     * @return true if reward > 0
     */
    public boolean isPositive() {
        return reward != null && reward > 0;
    }

    /**
     * Get total token count (input + output).
     * 
     * @return Total token count
     */
    public int getTotalTokenCount() {
        return inputPromptIds.size() + outputResponseIds.size();
    }
}