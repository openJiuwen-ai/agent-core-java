// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent;

import java.util.HashMap;
import java.util.Map;

/**
 * Group Runtime Configuration.
 * 
 * <p>Mutable runtime parameters for agent group execution.
 * Follows the same pattern as ReActAgentConfig with chained configuration methods.
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/multi_agent/config.py}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class GroupConfig {
    
    /**
     * Maximum number of agents allowed in group.
     * Default: 10
     */
    private int maxAgents = 10;
    
    /**
     * Maximum concurrent message processing.
     * Default: 100
     */
    private int maxConcurrentMessages = 100;
    
    /**
     * Timeout for message processing (seconds).
     * Default: 30.0
     */
    private double messageTimeout = 30.0;
    
    /**
     * Extra configuration options (for extensibility).
     */
    private Map<String, Object> extras = new HashMap<>();
    
    /**
     * Creates a GroupConfig with default values.
     */
    public GroupConfig() {
    }
    
    /**
     * Creates a GroupConfig with specified values.
     *
     * @param maxAgents maximum number of agents
     * @param messageTimeout timeout in seconds
     */
    public GroupConfig(int maxAgents, double messageTimeout) {
        this.maxAgents = maxAgents;
        this.messageTimeout = messageTimeout;
    }
    
    /**
     * Creates a GroupConfig with all specified values.
     *
     * @param maxAgents maximum number of agents
     * @param maxConcurrentMessages maximum concurrent messages
     * @param messageTimeout timeout in seconds
     */
    public GroupConfig(int maxAgents, int maxConcurrentMessages, double messageTimeout) {
        this.maxAgents = maxAgents;
        this.maxConcurrentMessages = maxConcurrentMessages;
        this.messageTimeout = messageTimeout;
    }
    
    // ========== Chained Configuration Methods ==========
    
    /**
     * Configure maximum agents.
     *
     * @param maxAgents Maximum number of agents
     * @return self (supports chaining)
     */
    public GroupConfig configureMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
        return this;
    }
    
    /**
     * Configure message timeout.
     *
     * @param timeout Timeout in seconds
     * @return self (supports chaining)
     */
    public GroupConfig configureTimeout(double timeout) {
        this.messageTimeout = timeout;
        return this;
    }
    
    /**
     * Configure concurrency limit.
     *
     * @param maxConcurrent Maximum concurrent messages
     * @return self (supports chaining)
     */
    public GroupConfig configureConcurrency(int maxConcurrent) {
        this.maxConcurrentMessages = maxConcurrent;
        return this;
    }
    
    // ========== Getters and Setters ==========
    
    /**
     * Gets the maximum number of agents.
     *
     * @return the maximum agents
     */
    public int getMaxAgents() {
        return maxAgents;
    }
    
    /**
     * Sets the maximum number of agents.
     *
     * @param maxAgents the maximum agents
     */
    public void setMaxAgents(int maxAgents) {
        this.maxAgents = maxAgents;
    }
    
    /**
     * Gets the maximum concurrent messages.
     *
     * @return the maximum concurrent messages
     */
    public int getMaxConcurrentMessages() {
        return maxConcurrentMessages;
    }
    
    /**
     * Sets the maximum concurrent messages.
     *
     * @param maxConcurrentMessages the maximum concurrent messages
     */
    public void setMaxConcurrentMessages(int maxConcurrentMessages) {
        this.maxConcurrentMessages = maxConcurrentMessages;
    }
    
    /**
     * Gets the message timeout.
     *
     * @return the message timeout in seconds
     */
    public double getMessageTimeout() {
        return messageTimeout;
    }
    
    /**
     * Sets the message timeout.
     *
     * @param messageTimeout the message timeout in seconds
     */
    public void setMessageTimeout(double messageTimeout) {
        this.messageTimeout = messageTimeout;
    }
    
    /**
     * Gets the extras map.
     *
     * @return the extras map
     */
    public Map<String, Object> getExtras() {
        return extras;
    }
    
    /**
     * Sets an extra configuration value.
     *
     * @param key the key
     * @param value the value
     * @return self (supports chaining)
     */
    public GroupConfig setExtra(String key, Object value) {
        this.extras.put(key, value);
        return this;
    }
    
    /**
     * Gets an extra configuration value.
     *
     * @param key the key
     * @return the value, or null if not found
     */
    public Object getExtra(String key) {
        return extras.get(key);
    }
    
    @Override
    public String toString() {
        return String.format("GroupConfig{maxAgents=%d, maxConcurrentMessages=%d, messageTimeout=%.1f}",
            maxAgents, maxConcurrentMessages, messageTimeout);
    }
}

