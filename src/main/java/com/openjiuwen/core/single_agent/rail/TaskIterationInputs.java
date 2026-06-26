/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input data for task-iteration lifecycle events.
 *
 * <p>Mirrors Python's {@code TaskIterationInputs} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskIterationInputs implements EventInputs {
    private int iteration;

    @JsonProperty("loop_event")
    private Object loopEvent;

    @JsonProperty("conversation_id")
    private String conversationId;

    private Map<String, Object> result;
    private String query;

    @JsonProperty("is_follow_up")
    private boolean followUp;

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public Object getLoopEvent() {
        return loopEvent;
    }

    public void setLoopEvent(Object loopEvent) {
        this.loopEvent = loopEvent;
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

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isFollowUp() {
        return followUp;
    }

    public void setFollowUp(boolean followUp) {
        this.followUp = followUp;
    }
}
