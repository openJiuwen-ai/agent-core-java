// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.messager;

import java.util.HashMap;
import java.util.Map;

/**
 * Opaque handle for transport subscriptions.
 * 
 * Mirrors Python's agent_teams.messager.base.SubscriptionHandle
 * 
 * @since 0.1.12
 */
public class SubscriptionHandle {
    
    /** Subscription identifier */
    private String subscriptionId;
    
    /** Topic this subscription is bound to */
    private String topic;
    
    /** Agent identifier */
    private String agentId;
    
    /** Backend-specific metadata */
    private Map<String, Object> backendMetadata = new HashMap<>();
    
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
        return backendMetadata;
    }
    
    public void setBackendMetadata(Map<String, Object> backendMetadata) {
        this.backendMetadata = backendMetadata != null ? backendMetadata : new HashMap<>();
    }
}