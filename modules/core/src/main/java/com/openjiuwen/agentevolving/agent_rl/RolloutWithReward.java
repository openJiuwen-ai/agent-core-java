/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RolloutWithReward {
    private Integer turnId;
    private String taskId;
    private String rolloutId;

    private List<Integer> inputPromptIds;
    private List<Integer> outputResponseIds;

    private Double reward;
    private Integer nTurns;
    private List<Integer> lossMask;

    /**
     * RolloutWithReward.
     * 
     * @param inputPromptIds inputPromptIds
     * @param outputResponseIds outputResponseIds
     * @since 0.1.7
     */
    public RolloutWithReward(List<Integer> inputPromptIds, List<Integer> outputResponseIds) {
        this(null, null, null, inputPromptIds, outputResponseIds, null, null, null);
    }

    /**
     * RolloutWithReward.
     * 
     * @param turnId turnId
     * @param taskId taskId
     * @param rolloutId rolloutId
     * @param inputPromptIds inputPromptIds
     * @param outputResponseIds outputResponseIds
     * @param reward reward
     * @param nTurns nTurns
     * @param lossMask lossMask
     * @since 0.1.7
     */
    public RolloutWithReward(Integer turnId, String taskId, String rolloutId, List<Integer> inputPromptIds,
            List<Integer> outputResponseIds, Double reward, Integer nTurns, List<Integer> lossMask) {
        this.turnId = turnId;
        this.taskId = taskId;
        this.rolloutId = rolloutId;
        setInputPromptIds(inputPromptIds);
        setOutputResponseIds(outputResponseIds);
        this.reward = reward;
        this.nTurns = nTurns;
        this.lossMask = lossMask;
    }

    /**
     * setInputPromptIds.
     * 
     * @param inputPromptIds inputPromptIds
     * @since 0.1.7
     */
    public void setInputPromptIds(List<Integer> inputPromptIds) {
        this.inputPromptIds = Objects.requireNonNull(inputPromptIds, "inputPromptIds is required");
    }

    /**
     * setOutputResponseIds.
     * 
     * @param outputResponseIds outputResponseIds
     * @since 0.1.7
     */
    public void setOutputResponseIds(List<Integer> outputResponseIds) {
        this.outputResponseIds = Objects.requireNonNull(outputResponseIds, "outputResponseIds is required");
    }

    /**
     * getInput_prompt_ids.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> getInput_prompt_ids() {
        return getInputPromptIds();
    }

    /**
     * getOutput_response_ids.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> getOutput_response_ids() {
        return getOutputResponseIds();
    }
}
