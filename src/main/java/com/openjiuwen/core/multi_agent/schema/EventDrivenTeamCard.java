/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-driven team card with subscription metadata.
 *
 * <p>Mirrors Python's {@code EventDrivenTeamCard} in
 * {@code openjiuwen/core/multi_agent/schema/team_card.py}.</p>
 */
public class EventDrivenTeamCard extends TeamCard {

    @JsonProperty("subscriptions")
    private Map<String, List<String>> subscriptions = new LinkedHashMap<>();

    public EventDrivenTeamCard() {
        super();
    }

    public EventDrivenTeamCard(String id, String name, String description) {
        super(id, name, description);
    }

    public Map<String, List<String>> getSubscriptions() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        subscriptions.forEach((key, values) -> copy.put(key, values == null ? List.of() : List.copyOf(values)));
        return copy;
    }

    public void setSubscriptions(Map<String, List<String>> subscriptions) {
        this.subscriptions = new LinkedHashMap<>();
        if (subscriptions == null) {
            return;
        }
        subscriptions.forEach((key, values) -> this.subscriptions.put(
                key,
                values == null ? new ArrayList<>() : new ArrayList<>(values)
        ));
    }
}
