/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventDrivenGroupCard extends GroupCard {
    private Map<String, List<String>> subscriptions = new HashMap<>();
}
