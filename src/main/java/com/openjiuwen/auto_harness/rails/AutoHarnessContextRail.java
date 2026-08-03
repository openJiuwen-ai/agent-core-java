/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.context.SessionMemoryConfig;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Context processor rail without workspace/context prompt injection.
 *
 * <p>Mirrors Python's {@code AutoHarnessContextRail} in
 * {@code openjiuwen/auto_harness/rails/context_rail.py}.</p>
 */
public class AutoHarnessContextRail extends ContextProcessorRail {

    public AutoHarnessContextRail() {
        super();
    }

    public AutoHarnessContextRail(boolean preset) {
        super(null, preset, null);
    }

    public AutoHarnessContextRail(List<ContextEngine.ProcessorSpec> processors) {
        super(processors);
    }

    public AutoHarnessContextRail(ContextEngine.ProcessorSpec processor) {
        super(processor);
    }

    public AutoHarnessContextRail(
            List<ContextEngine.ProcessorSpec> processors,
            boolean preset,
            SessionMemoryConfig sessionMemory
    ) {
        super(processors, preset, sessionMemory);
    }

    @Override
    public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
        return completed();
    }

    @Override
    public void uninit(BaseAgent agent) {
        // Python override is intentionally a no-op to preserve auto-harness prompt sections.
    }
}
