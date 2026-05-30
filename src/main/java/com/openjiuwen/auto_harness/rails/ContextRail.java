/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail;

/**
 * Auto-harness context rail.
 *
 * <p>Context processor rail without workspace/context prompt injection.</p>
 *
 * <p>Mirrors Python's {@code AutoHarnessContextRail} in {@code openjiuwen.auto_harness.rails.context_rail}.</p>
 */
public class ContextRail extends ContextProcessorRail {
    private final boolean preset;

    public ContextRail() {
        this(true);
    }

    public ContextRail(boolean preset) {
        super();
        this.preset = preset;
    }

    public boolean isPreset() {
        return preset;
    }

    /**
     * Do not inject workspace/tools/context prompt sections.
     *
     * @param ctx the agent callback context
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        // Intentionally empty.
    }

    /**
     * Do not mutate system prompt sections on teardown.
     *
     * @param agent the agent
     */
    @Override
    public void uninit(Object agent) {
        // Intentionally empty.
    }
}
