/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.schemas.Rollout.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rollout {
    private Integer turnId;
    private Map<String, Object> inputPrompt;
    private Map<String, Object> outputResponse;
    private Map<String, Object> llmConfig;
    private List<Integer> inputPromptIds;
    private List<Integer> outputResponseIds;

    /**
     * Rollout.
     * 
     * @param turnId turnId
     * @param inputPrompt inputPrompt
     * @param outputResponse outputResponse
     * @param llmConfig llmConfig
     * @param inputPromptIds inputPromptIds
     * @param outputResponseIds outputResponseIds
     * @since 0.1.7
     */
    public Rollout(Integer turnId, Map<String, Object> inputPrompt, Map<String, Object> outputResponse,
            Map<String, Object> llmConfig, List<Integer> inputPromptIds, List<Integer> outputResponseIds) {
        this.turnId = turnId;
        this.inputPrompt = inputPrompt;
        this.outputResponse = outputResponse;
        this.llmConfig = llmConfig;
        this.inputPromptIds = inputPromptIds;
        this.outputResponseIds = outputResponseIds;
    }

    /**
     * getOutput_response.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getOutput_response() {
        return getOutputResponse();
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

    /**
     * getLlm_config.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLlm_config() {
        return getLlmConfig();
    }

    /**
     * getInput_prompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInput_prompt() {
        return getInputPrompt();
    }
}
