/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.singleagent.rail.AgentRail;

import lombok.Getter;

/**
 * Public class BrowserRuntimeRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Getter
public class BrowserRuntimeRail extends AgentRail {
    private final BrowserAgentRuntime runtime;

    /**
     * BrowserRuntimeRail.
     * 
     * @param runtime runtime
     * @since 0.1.7
     */
    public BrowserRuntimeRail(BrowserAgentRuntime runtime) {
        this.runtime = runtime;
    }
}
