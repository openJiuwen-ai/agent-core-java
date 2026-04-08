/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.Map;

/**
 * State maintained by LLMExecutable for caching stream results.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.LLMExecutableState}.
 */
public class LLMExecutableState {

    private Map<String, Object> finalResult = Map.of();
    private final StringBuilder accumulatedContent = new StringBuilder();

    /**
     * Accumulate stream content chunks.
     */
    public void accumulateContent(String content) {
        accumulatedContent.append(content);
    }

    /**
     * Build final result from accumulated content.
     */
    public Map<String, Object> buildFinalResult(Map<String, Object> responseFormat,
                                                 Map<String, Object> outputConfig) {
        if (accumulatedContent.length() == 0) {
            return Map.of();
        }
        return OutputFormatter.formatResponse(accumulatedContent.toString(), responseFormat, outputConfig);
    }

    /**
     * Clear state.
     */
    public void clear() {
        finalResult = Map.of();
        accumulatedContent.setLength(0);
    }

    public Map<String, Object> getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(Map<String, Object> finalResult) {
        this.finalResult = finalResult;
    }
}
