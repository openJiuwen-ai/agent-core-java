/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

/**
 * Legacy group-card alias for team resource registration.
 *
 * <p>Mirrors Python's multi-agent group card usage in
 * {@code openjiuwen/core/multi_agent/schema/team_card.py}.</p>
 */
public class GroupCard extends TeamCard {

    public GroupCard() {
        super();
    }

    public GroupCard(String id, String name, String description) {
        super(id, name, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends TeamCard.Builder {
        @Override
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        @Override
        public GroupCard build() {
            return new GroupCard(id, name, description);
        }
    }
}
