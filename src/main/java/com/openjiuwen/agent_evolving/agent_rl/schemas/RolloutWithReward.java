/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard MDP data unit used by the training framework.
 *
 * <p>Mirrors Python's {@code RolloutWithReward} in
 * {@code openjiuwen/agent_evolving/agent_rl/schemas.py}.</p>
 */
public class RolloutWithReward {

    @JsonProperty("turn_id")
    private Integer turnId;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("rollout_id")
    private String rolloutId;

    @JsonProperty("input_prompt_ids")
    private List<Integer> inputPromptIds = new ArrayList<>();

    @JsonProperty("output_response_ids")
    private List<Integer> outputResponseIds = new ArrayList<>();

    private Double reward;

    @JsonProperty("n_turns")
    private Integer nTurns;

    @JsonProperty("loss_mask")
    private List<Integer> lossMask;

    public Integer getTurnId() {
        return turnId;
    }

    public void setTurnId(Integer turnId) {
        this.turnId = turnId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRolloutId() {
        return rolloutId;
    }

    public void setRolloutId(String rolloutId) {
        this.rolloutId = rolloutId;
    }

    public List<Integer> getInputPromptIds() {
        return inputPromptIds;
    }

    public void setInputPromptIds(List<Integer> inputPromptIds) {
        this.inputPromptIds = inputPromptIds != null ? new ArrayList<>(inputPromptIds) : new ArrayList<>();
    }

    public List<Integer> getOutputResponseIds() {
        return outputResponseIds;
    }

    public void setOutputResponseIds(List<Integer> outputResponseIds) {
        this.outputResponseIds = outputResponseIds != null ? new ArrayList<>(outputResponseIds) : new ArrayList<>();
    }

    public Double getReward() {
        return reward;
    }

    public void setReward(Double reward) {
        this.reward = reward;
    }

    public Integer getNTurns() {
        return nTurns;
    }

    public void setNTurns(Integer nTurns) {
        this.nTurns = nTurns;
    }

    public List<Integer> getLossMask() {
        return lossMask;
    }

    public void setLossMask(List<Integer> lossMask) {
        this.lossMask = lossMask != null ? new ArrayList<>(lossMask) : null;
    }
}
