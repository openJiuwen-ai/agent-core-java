/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.optimizer.base.TextualParameter.
 * 
 * @since 0.1.7
 */
public class TextualParameter {
    private final LLMCall llmCall;
    private Map<String, String> gradients;
    private String description;

    /**
     * TextualParameter.
     * 
     * @param llmCall llmCall
     * @since 0.1.7
     */
    public TextualParameter(LLMCall llmCall) {
        this.llmCall = llmCall;
        this.gradients = new HashMap<>();
        this.description = "";
    }

    /**
     * getLlmCall.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LLMCall getLlmCall() {
        return llmCall;
    }

    /**
     * getGradients.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, String> getGradients() {
        return gradients;
    }

    /**
     * setGradients.
     * 
     * @param gradients gradients
     * @since 0.1.7
     */
    public void setGradients(Map<String, String> gradients) {
        this.gradients = gradients != null ? gradients : new HashMap<>();
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * setDescription.
     * 
     * @param description description
     * @since 0.1.7
     */
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    /**
     * setGradient.
     * 
     * @param name name
     * @param gradient gradient
     * @since 0.1.7
     */
    public void setGradient(String name, String gradient) {
        gradients.put(name, gradient);
    }

    /**
     * getGradient.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public Optional<String> getGradient(String name) {
        return Optional.ofNullable(gradients.get(name));
    }

    /**
     * clearGradients.
     * 
     * @since 0.1.7
     */
    public void clearGradients() {
        gradients.clear();
    }
}
