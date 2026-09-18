/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured runtime context for heartbeat and cron runs.
 *
 * <p>Mirrors Python's {@code RunContext} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunContext {
    private HeartbeatReason reason;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("context_mode")
    private String contextMode;

    private Map<String, Object> extra = new LinkedHashMap<>();

    public HeartbeatReason getReason() {
        return reason;
    }

    public void setReason(HeartbeatReason reason) {
        this.reason = reason;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getContextMode() {
        return contextMode;
    }

    public void setContextMode(String contextMode) {
        this.contextMode = contextMode;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
    }
}
