/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Optional;

/**
 * Message bus configuration.
 * <p>
 * Mirrors Python's {@code MessageBusConfig} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.message_bus}.
 * <p>
 * Attributes:
 * <ul>
 *     <li>maxQueueSize: Maximum message queue size</li>
 *     <li>processTimeout: Message processing timeout in seconds</li>
 *     <li>teamId: Team ID for topic isolation</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageBusConfig {
    
    /** Maximum message queue size. */
    @Builder.Default
    private int maxQueueSize = 1000;
    
    /** Message processing timeout in seconds (30 minutes). */
    @Builder.Default
    private Double processTimeout = 1800.0;
    
    /** Team ID for topic isolation. */
    private String teamId;
    
    public Optional<String> getTeamId() {
        return Optional.ofNullable(teamId);
    }
    
    public Optional<Double> getProcessTimeout() {
        return Optional.ofNullable(processTimeout);
    }
}