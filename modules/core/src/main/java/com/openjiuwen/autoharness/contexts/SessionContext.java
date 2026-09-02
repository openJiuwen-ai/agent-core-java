/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.contexts;

import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;

/**
 * Public class SessionContext used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SessionContext extends BaseExecutionContext {
    /**
     * SessionContext.
     * 
     * @param orchestrator orchestrator
     * @since 0.1.7
     */
    public SessionContext(AutoHarnessOrchestrator orchestrator) {
        super(orchestrator);
    }
}
