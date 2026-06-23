/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests task-iteration observability rail spans.
 *
 * <p>Mirrors Python's {@code ObservabilityRail} tests in
 * {@code openjiuwen/agent_teams/observability/rail.py}.</p>
 */
class ObservabilityRailTest {

    @AfterEach
    void tearDown() {
        ObservabilitySetup.shutdownObservability();
    }

    @Test
    void opensAndClosesIterationSpan() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityRail rail = new ObservabilityRail(tracer);
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(3);
        inputs.setFollowUp(true);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);

        rail.beforeTaskIteration(context).toCompletableFuture().join();
        assertThat(context.getExtra()).containsKey(ObservabilityRail.SPAN_KEY);
        rail.afterTaskIteration(context).toCompletableFuture().join();

        TelemetrySpan span = findSpan(tracer, "deepagent.task_iteration.3");
        assertThat(span.getAttributes())
                .containsEntry(ObservabilitySemconv.DA_TASK_ITERATION, 3)
                .containsEntry(ObservabilitySemconv.DA_TASK_IS_FOLLOW_UP, true);
        assertThat(span.getStatusCode()).isEqualTo(TelemetrySpan.StatusCode.OK);
        assertThat(span.isEnded()).isTrue();
        assertThat(context.getExtra()).doesNotContainKey(ObservabilityRail.SPAN_KEY);
        assertThat(rail.getPriority()).isEqualTo(ObservabilityRail.DEFAULT_PRIORITY);
    }

    @Test
    void marksErrorOnException() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityRail rail = new ObservabilityRail(tracer);
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(1);
        RuntimeException error = new RuntimeException("kaboom");
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        context.setException(error);

        rail.beforeTaskIteration(context).toCompletableFuture().join();
        rail.afterTaskIteration(context).toCompletableFuture().join();

        TelemetrySpan span = findSpan(tracer, "deepagent.task_iteration.1");
        assertThat(span.getStatusCode()).isEqualTo(TelemetrySpan.StatusCode.ERROR);
        assertThat(span.getStatusDescription()).contains("kaboom");
        assertThat(span.getExceptions()).containsExactly(error);
        assertThat(span.isEnded()).isTrue();
    }

    @Test
    void supportsDynamicMapInputsAndMissingSpanAfterIsNoop() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityRail rail = new ObservabilityRail(tracer);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(Map.of("iteration", 4, "is_follow_up", false));

        rail.beforeTaskIteration(context).toCompletableFuture().join();
        rail.afterTaskIteration(context).toCompletableFuture().join();
        rail.afterTaskIteration(context).toCompletableFuture().join();

        TelemetrySpan span = findSpan(tracer, "deepagent.task_iteration.4");
        assertThat(span.getAttributes())
                .containsEntry(ObservabilitySemconv.DA_TASK_ITERATION, 4)
                .containsEntry(ObservabilitySemconv.DA_TASK_IS_FOLLOW_UP, false);
        assertThat(span.isEnded()).isTrue();
    }

    @Test
    void defaultTracerResolvesLazilyFromSetup() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilitySetup.initObservability(new ObservabilityConfig(), tracer);
        ObservabilityRail rail = new ObservabilityRail();
        AgentCallbackContext context = new AgentCallbackContext();

        rail.beforeTaskIteration(context).toCompletableFuture().join();
        rail.afterTaskIteration(context).toCompletableFuture().join();

        assertThat(findSpan(tracer, "deepagent.task_iteration.0").isEnded()).isTrue();
    }

    private static TelemetrySpan findSpan(TelemetryTracer.InMemory tracer, String name) {
        return tracer.getSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }
}
