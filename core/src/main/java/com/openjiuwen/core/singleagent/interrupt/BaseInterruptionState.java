/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import java.io.Serial;
import java.io.Serializable;

/**
 * Common interruption state fields.
 *
 * <p>Mirrors Python's {@code BaseInterruptionState} in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 *
 * <p>An interruption state is kept in the session state, and a persisting checkpointer writes
 * that state with Java serialization. This hierarchy is therefore {@link Serializable}: without
 * it the checkpoint of the interrupted turn fails as a whole, so the pending tool call is never
 * stored and a later turn of the same session cannot resume it.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseInterruptionState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
