/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-driven group card with subscription information.
 * <p>
 * Extends {@link GroupCard} with subscription mapping for event-driven
 * message routing.
 * <p>
 * Mirrors Python's {@code EventDrivenGroupCard} in {@code multi_agent/schema/group_card.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventDrivenGroupCard extends GroupCard {

    /**
     * Subscription mapping: {agent_id: [topic1, topic2, ...]}.
     */
    private Map<String, List<String>> subscriptions = new HashMap<>();
}
