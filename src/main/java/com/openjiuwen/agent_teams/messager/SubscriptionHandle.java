/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opaque handle for transport subscriptions.
 * <p>
 * Mirrors Python's {@code SubscriptionHandle} in
 * {@code openjiuwen/agent_teams/messager/base.py}.
 */
public class SubscriptionHandle {

    private String subscriptionId;
    private String topic;
    private String agentId;
    private Map<String, Object> backendMetadata = new LinkedHashMap<>();

    public SubscriptionHandle() {
    }

    public SubscriptionHandle(String subscriptionId, String topic) {
        this.subscriptionId = subscriptionId;
        this.topic = topic;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Map<String, Object> getBackendMetadata() {
        return new LinkedHashMap<>(backendMetadata);
    }

    public void setBackendMetadata(Map<String, Object> backendMetadata) {
        this.backendMetadata = backendMetadata != null ? new LinkedHashMap<>(backendMetadata) : new LinkedHashMap<>();
    }
}
