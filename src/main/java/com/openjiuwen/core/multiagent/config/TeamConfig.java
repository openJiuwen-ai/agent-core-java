/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Team configuration.
 * <p>
 * Mirrors Python's {@code TeamConfig} in 
 * {@code openjiuwen.core.multi_agent.team_config}.
 * <p>
 * Configuration for team runtime behavior including timeouts,
 * parallel execution limits, and agent settings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamConfig {

    /** Maximum number of agents allowed in the team. */
    @Builder.Default
    private int maxAgents = 10;

    /** Maximum concurrent message processing. */
    @Builder.Default
    private int maxConcurrentMessages = 100;

    /** Message processing timeout in seconds. */
    @Builder.Default
    private double messageTimeout = 30.0;
    
    /** Maximum parallel sub-agent executions. */
    @Builder.Default
    private int maxParallelSubAgents = 10;
    
    /** P2P message timeout in seconds. */
    @Builder.Default
    private double p2pTimeout = 1800.0;
    
    /** Enable message bus. */
    @Builder.Default
    private boolean enableMessageBus = true;
    
    /** Enable subscription management. */
    @Builder.Default
    private boolean enableSubscriptionManager = true;
    
    /** Team description. */
    private String description;
    
    /** Custom properties. */
    @Builder.Default
    private java.util.Map<String, Object> properties = new java.util.HashMap<>();

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
}
