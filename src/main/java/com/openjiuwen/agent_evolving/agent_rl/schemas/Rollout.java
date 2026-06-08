/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-turn dialogue rollout.
 *
 * <p>Mirrors Python's {@code Rollout} in
 * {@code openjiuwen/agent_evolving/agent_rl/schemas.py}.</p>
 */
public class Rollout {

    @JsonProperty("turn_id")
    private Integer turnId;

    @JsonProperty("input_prompt")
    private Map<String, Object> inputPrompt;

    @JsonProperty("output_response")
    private Map<String, Object> outputResponse;

    @JsonProperty("llm_config")
    private Map<String, Object> llmConfig;

    @JsonProperty("input_prompt_ids")
    private List<Integer> inputPromptIds;

    @JsonProperty("output_response_ids")
    private List<Integer> outputResponseIds;

    public Integer getTurnId() {
        return turnId;
    }

    public void setTurnId(Integer turnId) {
        this.turnId = turnId;
    }

    public Map<String, Object> getInputPrompt() {
        return inputPrompt;
    }

    public void setInputPrompt(Map<String, Object> inputPrompt) {
        this.inputPrompt = inputPrompt != null ? new HashMap<>(inputPrompt) : null;
    }

    public Map<String, Object> getOutputResponse() {
        return outputResponse;
    }

    public void setOutputResponse(Map<String, Object> outputResponse) {
        this.outputResponse = outputResponse != null ? new HashMap<>(outputResponse) : null;
    }

    public Map<String, Object> getLlmConfig() {
        return llmConfig;
    }

    public void setLlmConfig(Map<String, Object> llmConfig) {
        this.llmConfig = llmConfig != null ? new HashMap<>(llmConfig) : null;
    }

    public List<Integer> getInputPromptIds() {
        return inputPromptIds;
    }

    public void setInputPromptIds(List<Integer> inputPromptIds) {
        this.inputPromptIds = inputPromptIds != null ? new ArrayList<>(inputPromptIds) : null;
    }

    public List<Integer> getOutputResponseIds() {
        return outputResponseIds;
    }

    public void setOutputResponseIds(List<Integer> outputResponseIds) {
        this.outputResponseIds = outputResponseIds != null ? new ArrayList<>(outputResponseIds) : null;
    }
}
