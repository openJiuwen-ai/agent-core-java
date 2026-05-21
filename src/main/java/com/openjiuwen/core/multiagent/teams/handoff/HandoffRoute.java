/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import java.util.Objects;

/**
 * Routing rule: source agent may hand off to target agent.
 * <p>
 * Mirrors Python's {@code HandoffRoute} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_config}.
 * <p>
 * Immutable route definition.
 */
public final class HandoffRoute {
    
    private final String source;
    private final String target;
    
    public HandoffRoute(String source, String target) {
        this.source = source;
        this.target = target;
    }
    
    public String getSource() { return source; }
    public String getTarget() { return target; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandoffRoute that = (HandoffRoute) o;
        return Objects.equals(source, that.source) && Objects.equals(target, that.target);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(source, target);
    }
    
    @Override
    public String toString() {
        return String.format("HandoffRoute(source=%s, target=%s)", source, target);
    }
}