/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data for before/after invoke lifecycle events.
 *
 * <p>Mirrors Python's {@code InvokeInputs} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvokeInputs implements EventInputs {
    private Object query;

    @JsonProperty("conversation_id")
    private String conversationId;

    private Map<String, Object> result;

    @JsonProperty("run_kind")
    private RunKind runKind;

    @JsonProperty("run_context")
    private RunContext runContext;

    public boolean isHeartbeat() {
        return RunKind.HEARTBEAT == runKind;
    }

    public boolean isLightweightContext() {
        return runContext != null && "lightweight".equals(runContext.getContextMode());
    }

    public boolean isCron() {
        return RunKind.CRON == runKind;
    }

    public Object getQuery() {
        return query;
    }

    public void setQuery(Object query) {
        this.query = query;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result == null ? null : new LinkedHashMap<>(result);
    }

    public RunKind getRunKind() {
        return runKind;
    }

    public void setRunKind(RunKind runKind) {
        this.runKind = runKind;
    }

    public RunContext getRunContext() {
        return runContext;
    }

    public void setRunContext(RunContext runContext) {
        this.runContext = runContext;
    }
}
