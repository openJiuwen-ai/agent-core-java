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
 */
public class TextualParameter {

    private final LLMCall llmCall;
    private Map<String, String> gradients;
    private String description;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TextualParameter(LLMCall llmCall) {
        this.llmCall = llmCall;
        this.gradients = new HashMap<>();
        this.description = "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMCall getLlmCall() {
        return llmCall;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> getGradients() {
        return gradients;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGradients(Map<String, String> gradients) {
        this.gradients = gradients != null ? gradients : new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGradient(String name, String gradient) {
        gradients.put(name, gradient);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Optional<String> getGradient(String name) {
        return Optional.ofNullable(gradients.get(name));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void clearGradients() {
        gradients.clear();
    }
}
