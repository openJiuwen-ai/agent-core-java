/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

/**
 * Common interruption state fields.
 *
 * <p>Mirrors Python's {@code BaseInterruptionState} in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseInterruptionState {
    @JsonProperty("ai_message")
    private AssistantMessage aiMessage;

    private int iteration;

    @JsonProperty("original_query")
    private String originalQuery = "";

    public AssistantMessage getAiMessage() {
        return aiMessage;
    }

    public void setAiMessage(AssistantMessage aiMessage) {
        this.aiMessage = aiMessage;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery == null ? "" : originalQuery;
    }
}
