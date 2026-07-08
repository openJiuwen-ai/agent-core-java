/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

/**
 * Camelcase package compatibility facade for team cards.
 *
 * <p>Mirrors Python's {@code TeamCard} in
 * {@code openjiuwen/core/multi_agent/schema/team_card.py}.</p>
 */
public class TeamCard extends com.openjiuwen.core.multi_agent.schema.TeamCard {

    public TeamCard() {
        super();
    }

    public TeamCard(String id, String name, String description) {
        super(id, name, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected String id;
        protected String name;
        protected String description;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public TeamCard build() {
            return new TeamCard(id, name, description);
        }
    }
}
