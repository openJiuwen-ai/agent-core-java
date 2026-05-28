/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-driven team card with subscription information.
 * <p>
 * Mirrors Python's {@code EventDrivenTeamCard} in 
 * {@code openjiuwen.core.multi_agent.schema.team_card}.
 * <p>
 * Extends TeamCard with subscription mapping for event-driven message routing.
 * <p>
 * Attributes:
 * <ul>
 *     <li>subscriptions: Mapping of agent_id to list of subscribed topics</li>
 * </ul>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventDrivenTeamCard extends TeamCard {
    
    /** Subscription mapping: {agent_id: [topic1, topic2, ...]} */
    @Builder.Default
    private Map<String, List<String>> subscriptions = new HashMap<>();
}