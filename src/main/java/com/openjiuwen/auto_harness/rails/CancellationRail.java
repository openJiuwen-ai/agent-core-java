/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.AgentRail;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Checks orchestrator cancellation and requests force finish at agent checkpoints.
 *
 * <p>Mirrors Python's {@code CancellationRail} in
 * {@code openjiuwen/auto_harness/rails/cancellation_rail.py}.</p>
 */
public class CancellationRail extends AgentRail {

    private static final Logger LOGGER = Logger.getLogger(CancellationRail.class.getName());
    private AutoHarnessOrchestrator orchestrator;

    public CancellationRail() {
        setPriority(100);
    }

    /**
     * Bind the orchestrator reference after reflective construction.
     *
     * @param orchestrator orchestrator to monitor
     */
    public void bind(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public AutoHarnessOrchestrator getOrchestrator() {
        return orchestrator;
    }

    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        requestForceFinishWhenCancelled(context);
        return completed();
    }

    @Override
    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        requestForceFinishWhenCancelled(context);
        return completed();
    }

    private void requestForceFinishWhenCancelled(AgentCallbackContext context) {
        if (context == null || orchestrator == null || !orchestrator.shouldCancel()) {
            return;
        }
        LOGGER.info("[CancellationRail] cancellation detected, requesting force_finish");
        context.requestForceFinish(Map.of(
                "reason", "user_cancelled",
                "cancelled", true
        ));
    }
}
