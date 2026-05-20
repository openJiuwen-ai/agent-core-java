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
     * Auto-generated for codecheck compliance.
     */
    public RolloutWithReward(List<Integer> inputPromptIds, List<Integer> outputResponseIds) {
        this(null, null, null, inputPromptIds, outputResponseIds, null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RolloutWithReward(
            Integer turnId,
            String taskId,
            String rolloutId,
            List<Integer> inputPromptIds,
            List<Integer> outputResponseIds,
            Double reward,
            Integer nTurns,
            List<Integer> lossMask
    ) {
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
     * Auto-generated for codecheck compliance.
     */
    public void setInputPromptIds(List<Integer> inputPromptIds) {
        this.inputPromptIds = Objects.requireNonNull(inputPromptIds, "inputPromptIds is required");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOutputResponseIds(List<Integer> outputResponseIds) {
        this.outputResponseIds = Objects.requireNonNull(outputResponseIds, "outputResponseIds is required");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Integer> getInput_prompt_ids() {
        return getInputPromptIds();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Integer> getOutput_response_ids() {
        return getOutputResponseIds();
    }
}
