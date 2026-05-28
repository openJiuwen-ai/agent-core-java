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
 * Multi-agent runtime configuration.
 * <p>
 * Mirrors Python's {@code RuntimeConfig} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.team_runtime}.
 * <p>
 * Attributes:
 * <ul>
 *     <li>teamId: Team ID for topic isolation</li>
 *     <li>messageBus: Message bus configuration</li>
 *     <li>p2pTimeout: Default P2P message timeout in seconds</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuntimeConfig {
    
    /** Team ID for topic isolation. */
    @Builder.Default
    private String teamId = "default";
    
    /** Message bus configuration. */
    private MessageBusConfig messageBus;
    
    /** Default P2P message timeout in seconds (30 minutes). */
    @Builder.Default
    private double p2pTimeout = 1800.0;
    
    public Optional<MessageBusConfig> getMessageBus() {
        return Optional.ofNullable(messageBus);
    }
}