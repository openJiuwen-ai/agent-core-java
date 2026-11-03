/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Textual optimizer parameter wrapping a legacy LLM call.
 *
 * <p>Mirrors Python's {@code TextualParameter} in
 * {@code openjiuwen/dev_tools/tune/optimizer/base.py}.</p>
 */
public class TextualParameter {

    private final LLMCall llmCall;
    private final Map<String, String> gradients = new LinkedHashMap<>();
    private String description = "";

    public TextualParameter(LLMCall llmCall) {
        this.llmCall = Objects.requireNonNull(llmCall, "llmCall");
    }

    public LLMCall getLlmCall() {
        return llmCall;
    }

    public Map<String, String> getGradients() {
        return gradients;
    }

    public void setGradient(String name, String gradient) {
        gradients.put(name, gradient);
    }

    public void set_gradient(String name, String gradient) {
        setGradient(name, gradient);
    }

    public String getGradient(String name) {
        return gradients.get(name);
    }

    public String get_gradient(String name) {
        return getGradient(name);
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void set_description(String description) {
        setDescription(description);
    }

    public String getDescription() {
        return description;
    }

    public String get_description() {
        return getDescription();
    }
}
