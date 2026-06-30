/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.ContextProcessorRail;

import java.util.List;

/**
 * Auto-harness context rail.
 *
 * <p>Reuses {@link ContextProcessorRail} processor installation but disables prompt-section
 * injection/removal that would conflict with auto-harness identity prompts.</p>
 */
public class AutoHarnessContextRail extends ContextProcessorRail {
    /**
     * Auto-generated for codecheck compliance.
     */
    public AutoHarnessContextRail() {
        super();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AutoHarnessContextRail(boolean isPreset) {
        super(isPreset, List.of(), false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AutoHarnessContextRail(boolean isPreset, List<String> processorKeys, boolean isSessionMemoryEnabled) {
        super(isPreset, processorKeys, isSessionMemoryEnabled);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void beforeModelCall(AgentCallbackContext ctx) {
        // Python AutoHarnessContextRail intentionally skips workspace/context prompt injection.
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void uninit(Object agent) {
        // Keep installed prompt sections untouched on teardown, matching Python's noop uninit.
    }
}
