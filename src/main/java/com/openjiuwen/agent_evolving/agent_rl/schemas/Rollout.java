/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-turn dialogue rollout.
 * <p>
 * Format compatible with jiuwen_rl v1:
 * - input_prompt["message"]: input message list (OpenAI message format)
 * - input_prompt["tools"]: tool definition list
 * - output_response: LLM output message (content or tool_calls)
 * <p>
 * Mirrors Python's {@code Rollout} in
 * {@code openjiuwen.agent_evolving.agent_rl.schemas}.
 */
public class Rollout {

    private Integer turnId;
    private Map<String, Object> inputPrompt;
    private Map<String, Object> outputResponse;
    private Map<String, Object> llmConfig;
    private List<Integer> inputPromptIds; // Prompt token IDs from LLM service
    private List<Integer> outputResponseIds; // Completion token IDs from LLM service

    public Rollout() {
    }

    // Getters and setters
    public Integer getTurnId() { return turnId; }
    public void setTurnId(Integer turnId) { this.turnId = turnId; }
    public Map<String, Object> getInputPrompt() { return inputPrompt; }
    public void setInputPrompt(Map<String, Object> inputPrompt) { 
        this.inputPrompt = inputPrompt != null ? new HashMap<>(inputPrompt) : null; 
    }
    public Map<String, Object> getOutputResponse() { return outputResponse; }
    public void setOutputResponse(Map<String, Object> outputResponse) { 
        this.outputResponse = outputResponse != null ? new HashMap<>(outputResponse) : null; 
    }
    public Map<String, Object> getLlmConfig() { return llmConfig; }
    public void setLlmConfig(Map<String, Object> llmConfig) { 
        this.llmConfig = llmConfig != null ? new HashMap<>(llmConfig) : null; 
    }
    public List<Integer> getInputPromptIds() { return inputPromptIds; }
    public void setInputPromptIds(List<Integer> inputPromptIds) { 
        this.inputPromptIds = inputPromptIds != null ? new ArrayList<>(inputPromptIds) : null; 
    }
    public List<Integer> getOutputResponseIds() { return outputResponseIds; }
    public void setOutputResponseIds(List<Integer> outputResponseIds) { 
        this.outputResponseIds = outputResponseIds != null ? new ArrayList<>(outputResponseIds) : null; 
    }
}