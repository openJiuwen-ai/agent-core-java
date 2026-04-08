/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy.schema;

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
 * Legacy Event-driven group card with subscription information.
 * <p>
 * Mirrors Python's legacy {@code EventDrivenGroupCard} in {@code multi_agent/legacy/schema/group_card.py}.
 *
 * @deprecated Use {@link com.openjiuwen.core.multiagent.schema.EventDrivenGroupCard}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class LegacyEventDrivenGroupCard extends LegacyGroupCard {

    /**
     * Subscription mapping: {agent_id: [topic1, topic2, ...]}.
     */
    @Builder.Default
    private Map<String, List<String>> subscriptions = new HashMap<>();
}
