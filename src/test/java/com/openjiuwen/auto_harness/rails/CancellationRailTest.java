/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code CancellationRail} in
 * {@code openjiuwen/auto_harness/rails/cancellation_rail.py}.
 */
class CancellationRailTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultPriorityRunsEarly() {
        CancellationRail rail = new CancellationRail();

        assertThat(rail.getPriority()).isEqualTo(100);
    }

    @Test
    void beforeToolCallDoesNothingWhenNotCancelled() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        CancellationRail rail = new CancellationRail();
        AgentCallbackContext context = new AgentCallbackContext();
        rail.bind(orchestrator);

        rail.beforeToolCall(context).toCompletableFuture().join();

        assertThat(context.hasForceFinishRequest()).isFalse();
    }

    @Test
    void beforeToolCallRequestsForceFinishWhenCancelled() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        CancellationRail rail = new CancellationRail();
        AgentCallbackContext context = new AgentCallbackContext();
        rail.bind(orchestrator);
        orchestrator.cancel();

        rail.beforeToolCall(context).toCompletableFuture().join();

        assertForceFinishPayload(context);
    }

    @Test
    void afterModelCallRequestsForceFinishWhenCancelled() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        CancellationRail rail = new CancellationRail();
        AgentCallbackContext context = new AgentCallbackContext();
        rail.bind(orchestrator);
        orchestrator.cancel();

        rail.afterModelCall(context).toCompletableFuture().join();

        assertForceFinishPayload(context);
    }

    @Test
    void orchestratorReflectivelyBindsCancellationRail() {
        AutoHarnessOrchestrator orchestrator = orchestrator();

        assertThat(orchestrator.getCancellationRail()).isInstanceOf(CancellationRail.class);
        CancellationRail rail = (CancellationRail) orchestrator.getCancellationRail();
        assertThat(rail.getOrchestrator()).isSameAs(orchestrator);
        assertThat(orchestrator.getStreamRails()).contains(rail);
    }

    private AutoHarnessOrchestrator orchestrator() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setWorkspace(tempDir.toString());
        return new AutoHarnessOrchestrator(config);
    }

    @SuppressWarnings("unchecked")
    private static void assertForceFinishPayload(AgentCallbackContext context) {
        assertThat(context.hasForceFinishRequest()).isTrue();
        Map<String, Object> result = context.consumeForceFinish().getResult();
        assertThat(result).containsEntry("reason", "user_cancelled");
        assertThat(result).containsEntry("cancelled", true);
    }
}
