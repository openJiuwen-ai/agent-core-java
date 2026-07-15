/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.ContextProcessorRail;

import java.util.List;

/**
 * Auto-harness context rail.
 * <p>
 * Reuses {@link ContextProcessorRail} processor installation but disables prompt-section
 * injection/removal that would conflict with auto-harness identity prompts.
 * </p>
 * 
 * @since 0.1.7
 */
public class AutoHarnessContextRail extends ContextProcessorRail {
    /**
     * AutoHarnessContextRail.
     * 
     * @since 0.1.7
     */
    public AutoHarnessContextRail() {
        super();
    }

    /**
     * AutoHarnessContextRail.
     * 
     * @param isPreset isPreset
     * @since 0.1.7
     */
    public AutoHarnessContextRail(boolean isPreset) {
        super(isPreset, List.of(), false);
    }

    /**
     * AutoHarnessContextRail.
     * 
     * @param isPreset isPreset
     * @param processorKeys processorKeys
     * @param isSessionMemoryEnabled isSessionMemoryEnabled
     * @since 0.1.7
     */
    public AutoHarnessContextRail(boolean isPreset, List<String> processorKeys, boolean isSessionMemoryEnabled) {
        super(isPreset, processorKeys, isSessionMemoryEnabled);
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        // Python AutoHarnessContextRail intentionally skips workspace/context prompt injection.
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        // Keep installed prompt sections untouched on teardown, matching Python's noop uninit.
    }
}
