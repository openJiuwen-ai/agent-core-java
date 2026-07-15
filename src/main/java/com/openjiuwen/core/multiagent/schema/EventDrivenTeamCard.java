/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-driven compatibility card aligned with Python's
 * {@code EventDrivenTeamCard}.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventDrivenTeamCard extends TeamCard {
    private Map<String, List<String>> subscriptions = new LinkedHashMap<>();

    /**
     * putSubscription.
     * 
     * @param agentId agentId
     * @param topics topics
     * @since 0.1.7
     */
    public void putSubscription(String agentId, List<String> topics) {
        subscriptions.put(agentId, topics != null ? new ArrayList<>(topics) : new ArrayList<>());
    }
}
