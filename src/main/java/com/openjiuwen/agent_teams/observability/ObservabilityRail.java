/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Opens and closes spans around outer task-loop iterations.
 *
 * <p>Mirrors Python's {@code ObservabilityRail} in
 * {@code openjiuwen/agent_teams/observability/rail.py}.</p>
 */
public class ObservabilityRail extends AgentRail {

    public static final int DEFAULT_PRIORITY = 10;
    public static final String SPAN_KEY = "_otel_task_iter_span";
    public static final String TRACER_NAME = ObservabilitySetup.RAIL_TRACER_NAME;

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final TelemetryTracer injectedTracer;

    public ObservabilityRail() {
        this(null);
    }

    public ObservabilityRail(TelemetryTracer tracer) {
        this.injectedTracer = tracer;
        setPriority(DEFAULT_PRIORITY);
    }

    @Override
    public CompletionStage<Void> beforeTaskIteration(AgentCallbackContext context) {
        try {
            AgentCallbackContext ctx = nonNullContext(context);
            Object inputs = ctx.getInputs();
            int iteration = readIteration(inputs);
            boolean followUp = readFollowUp(inputs);
            TelemetrySpan span = tracer().startSpan(
                    "deepagent.task_iteration." + iteration,
                    TelemetrySpan.Kind.INTERNAL
            );
            span.setAttribute(ObservabilitySemconv.DA_TASK_ITERATION, iteration);
            span.setAttribute(ObservabilitySemconv.DA_TASK_IS_FOLLOW_UP, followUp);
            ctx.getExtra().put(SPAN_KEY, span);
        } catch (Exception error) {
            TEAM_LOGGER.warning("otel rail before_task_iteration failed: {}", error);
        }
        return completed();
    }

    @Override
    public CompletionStage<Void> afterTaskIteration(AgentCallbackContext context) {
        try {
            AgentCallbackContext ctx = nonNullContext(context);
            Object rawSpan = ctx.getExtra().remove(SPAN_KEY);
            if (!(rawSpan instanceof TelemetrySpan span)) {
                return completed();
            }
            Exception exception = ctx.getException();
            if (exception != null) {
                span.recordException(exception);
                span.setStatus(TelemetrySpan.StatusCode.ERROR, exception.toString());
            } else {
                span.setStatus(TelemetrySpan.StatusCode.OK);
            }
            span.end();
        } catch (Exception error) {
            TEAM_LOGGER.warning("otel rail after_task_iteration failed: {}", error);
        }
        return completed();
    }

    private TelemetryTracer tracer() {
        if (injectedTracer != null) {
            return injectedTracer;
        }
        return ObservabilitySetup.getTracer(TRACER_NAME);
    }

    private static AgentCallbackContext nonNullContext(AgentCallbackContext context) {
        return context == null ? new AgentCallbackContext() : context;
    }

    private static int readIteration(Object inputs) {
        if (inputs instanceof TaskIterationInputs taskInputs) {
            return taskInputs.getIteration();
        }
        Object value = readValue(inputs, "iteration", "getIteration");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean readFollowUp(Object inputs) {
        if (inputs instanceof TaskIterationInputs taskInputs) {
            return taskInputs.isFollowUp();
        }
        Object value = readValue(inputs, "is_follow_up", "isFollowUp", "getFollowUp", "followUp");
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object readValue(Object target, String... names) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
            return null;
        }
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Python getattr-style tolerance: try the next possible field accessor.
            }
        }
        return null;
    }
}
