/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.Map;

/**
 * State maintained by LLMExecutable for caching stream results.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.LLMExecutableState}.
 * 
 * @since 0.1.7
 */
public class LLMExecutableState {
    private Map<String, Object> finalResult = Map.of();

    /**
     * StringBuilder.
     * 
     * @since 0.1.7
     */
    private final StringBuilder accumulatedContent = new StringBuilder();

    /**
     * Accumulate stream content chunks.
     * 
     * @param content content
     * @since 0.1.7
     */
    public synchronized void accumulateContent(String content) {
        accumulatedContent.append(content);
    }

    /**
     * Build final result from accumulated content.
     * 
     * @param responseFormat responseFormat
     * @param outputConfig outputConfig
     * @return the result
     * @since 0.1.7
     */
    public synchronized Map<String, Object> buildFinalResult(Map<String, Object> responseFormat, Map<String, Object> outputConfig) {
        if (accumulatedContent.length() == 0) {
            return Map.of();
        }
        return OutputFormatter.formatResponse(accumulatedContent.toString(), responseFormat, outputConfig);
    }

    /**
     * Clear state.
     * 
     * @since 0.1.7
     */
    public synchronized void clear() {
        finalResult = Map.of();
        accumulatedContent.setLength(0);
    }

    /**
     * getFinalResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getFinalResult() {
        return finalResult;
    }

    /**
     * setFinalResult.
     * 
     * @param finalResult finalResult
     * @since 0.1.7
     */
    public void setFinalResult(Map<String, Object> finalResult) {
        this.finalResult = finalResult;
    }
}
