/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all evolution rails.
 * <p>
 * All evolution rails inherit from this class and get trajectory collection.
 * Subclasses override extension points to implement evolution algorithms.
 * <p>
 * Mirrors Python's {@code EvolutionRail} in
 * {@code openjiuwen.harness.rails.evolution.evolution_rail}.
 */
public class EvolutionRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(EvolutionRail.class);

    /** Evolution trigger modes. */
    public enum EvolutionTrigger {
        MANUAL, PER_ROUND, PER_SESSION, ON_DEMAND
    }

    private final EvolutionTrigger trigger;
    private boolean evolutionEnabled = true;

    public EvolutionRail() {
        this(EvolutionTrigger.MANUAL);
    }

    public EvolutionRail(EvolutionTrigger trigger) {
        super();
        this.trigger = trigger;
    }

    @Override
    public void init(Object agent) {
        LOG.info("[EvolutionRail] Initialized with trigger={}", trigger);
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[EvolutionRail] Uninitialized");
    }

    // Extension points for subclasses
    protected void onBeforeInvoke(Object ctx) {}
    protected void onAfterModelCall(Object ctx, Object response) {}
    protected void onAfterToolCall(Object ctx, Object toolCall, Object result) {}
    protected void onAfterInvoke(Object ctx) {}
    protected void runEvolution() {}

    public boolean isEvolutionEnabled() { return evolutionEnabled; }
    public void setEvolutionEnabled(boolean enabled) { this.evolutionEnabled = enabled; }
}
