/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.external;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import java.util.List;

public final class ExternalToolPendingState {
    private final AssistantMessage assistantMessage;
    private final int iteration;
    private final String originalQuery;
    private final List<ToolCall> pendingToolCalls;
    private final List<ExternalToolCallRequest> externalCallRequests;

    public ExternalToolPendingState(AssistantMessage assistantMessage,
                                    int iteration,
                                    String originalQuery,
                                    List<ToolCall> pendingToolCalls,
                                    List<ExternalToolCallRequest> externalCallRequests) {
        this.assistantMessage = assistantMessage;
        this.iteration = iteration;
        this.originalQuery = originalQuery;
        this.pendingToolCalls = pendingToolCalls == null ? List.of() : List.copyOf(pendingToolCalls);
        this.externalCallRequests = externalCallRequests == null ? List.of() : List.copyOf(externalCallRequests);
    }

    public AssistantMessage getAssistantMessage() {
        return assistantMessage;
    }

    public int getIteration() {
        return iteration;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public List<ToolCall> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public List<ExternalToolCallRequest> getExternalToolCalls() {
        return externalCallRequests;
    }
}
