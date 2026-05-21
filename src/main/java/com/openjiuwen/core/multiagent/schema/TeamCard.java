/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Team Identity Card.
 * <p>
 * Mirrors Python's {@code TeamCard} in 
 * {@code openjiuwen.core.multi_agent.schema.team_card}.
 * <p>
 * Immutable identity information for an agent team.
 * Inherits from BaseCard: id, name, description.
 * <p>
 * Attributes:
 * <ul>
 *     <li>agentCards: List of AgentCards for agents in this team</li>
 *     <li>topic: Team's primary topic/domain</li>
 *     <li>version: Team version string</li>
 *     <li>tags: Optional tags for categorization</li>
 * </ul>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeamCard extends BaseCard {
    
    /** Agent cards for team members (metadata only, not instances). */
    @Builder.Default
    private List<AgentCard> agentCards = new ArrayList<>();
    
    /** Team's primary topic or domain. */
    @Builder.Default
    private String topic = "";
    
    /** Team version. */
    @Builder.Default
    private String version = "1.0.0";
    
    /** Tags for categorization. */
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}