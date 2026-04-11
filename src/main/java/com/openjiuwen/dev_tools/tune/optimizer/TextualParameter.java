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

    public TextualParameter(LLMCall llmCall) {
        this.llmCall = llmCall;
        this.gradients = new HashMap<>();
        this.description = "";
    }

    public LLMCall getLlmCall() {
        return llmCall;
    }

    public Map<String, String> getGradients() {
        return gradients;
    }

    public void setGradients(Map<String, String> gradients) {
        this.gradients = gradients != null ? gradients : new HashMap<>();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public void setGradient(String name, String gradient) {
        gradients.put(name, gradient);
    }

    public Optional<String> getGradient(String name) {
        return Optional.ofNullable(gradients.get(name));
    }

    public void clearGradients() {
        gradients.clear();
    }
}