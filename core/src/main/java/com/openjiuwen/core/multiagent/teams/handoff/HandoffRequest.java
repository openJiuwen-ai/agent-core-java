/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.session.AgentTeamSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal drive message published between container agents.
 *
 * <p>Mirrors Python's {@code HandoffRequest} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_request.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandoffRequest {

    @JsonProperty("input_message")
    private Object inputMessage;

    @JsonProperty("history")
    private List<Map<String, Object>> history = new ArrayList<>();

    @JsonIgnore
    private AgentTeamSession session;

    public HandoffRequest() {
    }

    public HandoffRequest(Object inputMessage) {
        this(inputMessage, null, null);
    }

    public HandoffRequest(Object inputMessage, List<Map<String, Object>> history, AgentTeamSession session) {
        this.inputMessage = inputMessage;
        setHistory(history);
        this.session = session;
    }

    public Object getInputMessage() {
        return inputMessage;
    }

    public void setInputMessage(Object inputMessage) {
        this.inputMessage = inputMessage;
    }

    public List<Map<String, Object>> getHistory() {
        return history;
    }

    public void setHistory(List<Map<String, Object>> history) {
        this.history = new ArrayList<>();
        if (history == null) {
            return;
        }
        for (Map<String, Object> item : history) {
            this.history.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
        }
    }

    public HandoffRequest addHistory(Map<String, Object> item) {
        history.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
        return this;
    }

    @JsonIgnore
    public AgentTeamSession getSession() {
        return session;
    }

    public void setSession(AgentTeamSession session) {
        this.session = session;
    }

    @JsonProperty("session_id")
    public String getSessionId() {
        return session == null ? "" : session.getSessionId();
    }
}
