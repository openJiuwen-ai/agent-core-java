/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Routing rule that permits one source agent to hand off to one target agent.
 *
 * <p>Mirrors Python's {@code HandoffRoute} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
public final class HandoffRoute {

    private final String source;
    private final String target;

    @JsonCreator
    public HandoffRoute(@JsonProperty("source") String source, @JsonProperty("target") String target) {
        this.source = source;
        this.target = target;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HandoffRoute that)) {
            return false;
        }
        return Objects.equals(source, that.source) && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target);
    }

    @Override
    public String toString() {
        return "HandoffRoute(source=%s, target=%s)".formatted(source, target);
    }
}
