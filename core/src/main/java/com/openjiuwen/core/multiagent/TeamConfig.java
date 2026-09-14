/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable runtime parameters for an agent team.
 * <p>
 * Mirrors Python's {@code TeamConfig} in
 * {@code openjiuwen/core/multi_agent/config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamConfig {

    @JsonProperty("max_agents")
    private int maxAgents = 10;

    @JsonProperty("max_concurrent_messages")
    private int maxConcurrentMessages = 100;

    @JsonProperty("message_timeout")
    private double messageTimeout = 30.0;

    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public int getMaxAgents() {
        return maxAgents;
    }

    public void setMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
    }

    public int getMaxConcurrentMessages() {
        return maxConcurrentMessages;
    }

    public void setMaxConcurrentMessages(int maxConcurrentMessages) {
        this.maxConcurrentMessages = maxConcurrentMessages;
    }

    public double getMessageTimeout() {
        return messageTimeout;
    }

    public void setMessageTimeout(double messageTimeout) {
        this.messageTimeout = messageTimeout;
    }

    public TeamConfig configureMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
        return this;
    }

    public TeamConfig configureTimeout(double timeout) {
        this.messageTimeout = timeout;
        return this;
    }

    public TeamConfig configureConcurrency(int maxConcurrent) {
        this.maxConcurrentMessages = maxConcurrent;
        return this;
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }
}
