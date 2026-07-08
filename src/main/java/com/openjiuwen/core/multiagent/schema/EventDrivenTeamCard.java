/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

/**
 * Camelcase package compatibility facade for event-driven team cards.
 *
 * <p>Mirrors Python's {@code EventDrivenTeamCard} in
 * {@code openjiuwen/core/multi_agent/schema/team_card.py}.</p>
 */
public class EventDrivenTeamCard extends com.openjiuwen.core.multi_agent.schema.EventDrivenTeamCard {

    public EventDrivenTeamCard() {
        super();
    }

    public EventDrivenTeamCard(String id, String name, String description) {
        super(id, name, description);
    }
}
