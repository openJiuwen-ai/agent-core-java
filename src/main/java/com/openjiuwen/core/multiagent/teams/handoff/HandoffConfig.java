/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Orchestration parameters for HandoffTeam.
 * <p>
 * Mirrors Python's {@code HandoffConfig} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_config}.
 * <p>
 * Attributes:
 * <ul>
 *     <li>startAgent: AgentCard of the first agent to run</li>
 *     <li>maxHandoffs: Maximum number of handoff transfers</li>
 *     <li>routes: Explicit routing rules</li>
 *     <li>terminationCondition: Optional termination predicate</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoffConfig {
    
    /** AgentCard of the first agent to run. */
    private AgentCard startAgent;
    
    /** Maximum number of handoff transfers (default: 10). */
    @Builder.Default
    private int maxHandoffs = 10;
    
    /** Explicit routing rules. Empty list means full-mesh. */
    @Builder.Default
    private List<HandoffRoute> routes = new ArrayList<>();
    
    /** Optional termination condition predicate. */
    private Function<Object, Object> terminationCondition;
    
    public Optional<AgentCard> getStartAgent() {
        return Optional.ofNullable(startAgent);
    }
}
