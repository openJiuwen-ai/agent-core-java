/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inputs field model.
 * <p>
 * Mirrors Python's {@code InputsField} dataclass.
 */
public class InputsField {
    private final Map<String, InputVariable> inputParameters;
    private final Map<String, Object> llmParam;
    private final Map<String, String> systemPrompt;
    private final List<Map<String, String>> intents;
    private final String language;
    private final String code;
    private final Map<String, String> pluginParam;
    private final Map<String, String> content;
    private final Boolean historyEnable;
    private final Integer maxResponse;

    public InputsField() {
        this(new LinkedHashMap<>(), null, null, null, null, null, null, null, null, null);
    }

    public InputsField(Map<String, Object> llmParam) {
        this(new LinkedHashMap<>(), llmParam, null, null, null, null, null, null, null, null);
    }

    public InputsField(Map<String, Object> llmParam, String code) {
        this(new LinkedHashMap<>(), llmParam, null, null, null, code, null, null, null, null);
    }

    public InputsField(Map<String, InputVariable> inputParameters,
                       Map<String, Object> llmParam,
                       Map<String, String> systemPrompt,
                       List<Map<String, String>> intents,
                       String language,
                       String code,
                       Map<String, String> pluginParam,
                       Map<String, String> content,
                       Boolean historyEnable,
                       Integer maxResponse) {
        this.inputParameters = inputParameters != null ? new LinkedHashMap<>(inputParameters) : new LinkedHashMap<>();
        this.llmParam = llmParam != null ? new LinkedHashMap<>(llmParam) : null;
        this.systemPrompt = systemPrompt != null ? new LinkedHashMap<>(systemPrompt) : null;
        this.intents = intents;
        this.language = language;
        this.code = code;
        this.pluginParam = pluginParam != null ? new LinkedHashMap<>(pluginParam) : null;
        this.content = content != null ? new LinkedHashMap<>(content) : null;
        this.historyEnable = historyEnable;
        this.maxResponse = maxResponse;
    }

    public Map<String, InputVariable> getInputParameters() {
        return inputParameters;
    }

    public Map<String, Object> getLlmParam() {
        return llmParam;
    }

    public Map<String, String> getSystemPrompt() {
        return systemPrompt;
    }

    public List<Map<String, String>> getIntents() {
        return intents;
    }

    public String getLanguage() {
        return language;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getPluginParam() {
        return pluginParam;
    }

    public Map<String, String> getContent() {
        return content;
    }

    public Boolean getHistoryEnable() {
        return historyEnable;
    }

    public Integer getMaxResponse() {
        return maxResponse;
    }
}
