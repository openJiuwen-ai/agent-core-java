/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;

/**
 * Runtime context passed into session pipelines and stages.
 *
 * <p>Mirrors Python's {@code SessionContext} in {@code openjiuwen.auto_harness.contexts.execution}.</p>
 */
public class SessionContext extends BaseExecutionContext {

    public SessionContext(AutoHarnessOrchestrator orchestrator) {
        super(orchestrator);
    }
}