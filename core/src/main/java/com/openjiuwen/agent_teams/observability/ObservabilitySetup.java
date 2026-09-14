/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.AgentEvents;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.LLMCallEvents;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Tracer lifecycle and callback wiring for agent-team observability.
 *
 * <p>Mirrors Python's module-level functions in
 * {@code openjiuwen/agent_teams/observability/setup.py}.</p>
 */
public final class ObservabilitySetup {

    public static final String NAMESPACE = "agent_teams.observability";
    public static final String CALLBACK_TRACER_NAME = "openjiuwen.agent_teams.observability";
    public static final String MONITOR_TRACER_NAME = "openjiuwen.agent_teams.observability.monitor";
    public static final String RAIL_TRACER_NAME = "openjiuwen.agent_teams.observability.rail";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final List<Registration> REGISTERED = new ArrayList<>();
    private static final Set<TeamAgent> ATTACHED_AGENTS = Collections.newSetFromMap(new IdentityHashMap<>());

    private static TelemetryTracer provider;
    private static OtelCallbackHandler callbackHandler;
    private static OtelTeamMonitorHandler monitorHandler;

    private ObservabilitySetup() {
    }

    public static synchronized void initObservability(ObservabilityConfig config) {
        initObservability(config, null);
    }

    public static synchronized void initObservability(ObservabilityConfig config, TelemetryTracer tracerOverride) {
        ObservabilityConfig activeConfig = config == null ? new ObservabilityConfig() : config;
        if (!activeConfig.isEnabled()) {
            TEAM_LOGGER.info("observability disabled by config");
            return;
        }
        if (provider != null) {
            TEAM_LOGGER.warning("observability already initialized; skipping re-init");
            return;
        }
        if (tracerOverride != null) {
            provider = tracerOverride;
        } else if (activeConfig.getExporter() == ObservabilityExporter.FILE) {
            TraceFileExporter fileExporter = new TraceFileExporter(
                    activeConfig.getTracesDir(),
                    activeConfig.getFileRetentionDays()
            );
            provider = new TelemetryTracer.FileBacked(fileExporter);
        } else {
            provider = new TelemetryTracer.InMemory();
        }
        callbackHandler = new OtelCallbackHandler(activeConfig, getTracer(CALLBACK_TRACER_NAME));
        monitorHandler = new OtelTeamMonitorHandler(activeConfig, getTracer(MONITOR_TRACER_NAME));
        wireCallbackHandlers(callbackHandler);
    }

    public static synchronized void shutdownObservability() {
        AsyncCallbackFramework framework = runnerCallbackFramework();
        if (framework != null) {
            for (Registration registration : List.copyOf(REGISTERED)) {
                try {
                    framework.unregisterSync(registration.event(), registration.callback());
                } catch (Exception error) {
                    TEAM_LOGGER.warning("otel: failed to unregister {} - {}", registration.event(), error);
                }
            }
        }
        REGISTERED.clear();
        ATTACHED_AGENTS.clear();
        provider = null;
        callbackHandler = null;
        monitorHandler = null;
        SpanContext.resetAll();
    }

    public static synchronized TelemetryTracer getTracer(String name) {
        if (provider != null) {
            return provider;
        }
        provider = new TelemetryTracer.InMemory();
        return provider;
    }

    public static synchronized void attachToTeamAgent(TeamAgent teamAgent) {
        if (monitorHandler == null) {
            TEAM_LOGGER.warning("attach_to_team_agent called before init_observability");
            return;
        }
        if (teamAgent != null && ATTACHED_AGENTS.add(teamAgent)) {
            teamAgent.addEventListener(monitorHandler);
        }
    }

    public static synchronized void detachFromTeamAgent(TeamAgent teamAgent) {
        if (monitorHandler == null || teamAgent == null) {
            return;
        }
        if (ATTACHED_AGENTS.remove(teamAgent)) {
            teamAgent.removeEventListener(monitorHandler);
        }
    }

    public static OtelCallbackHandler getCallbackHandler() {
        return callbackHandler;
    }

    public static OtelTeamMonitorHandler getMonitorHandler() {
        return monitorHandler;
    }

    public static List<String> registeredEvents() {
        return REGISTERED.stream().map(Registration::event).toList();
    }

    private static void wireCallbackHandlers(OtelCallbackHandler handler) {
        AsyncCallbackFramework framework = runnerCallbackFramework();
        if (framework == null) {
            TEAM_LOGGER.warning("otel: Runner.callback_framework unavailable; skipping wiring");
            return;
        }
        register(framework, LLMCallEvents.LLM_INVOKE_INPUT, handler::onLlmInvokeInput);
        register(framework, LLMCallEvents.LLM_STREAM_INPUT, handler::onLlmStreamInput);
        register(framework, LLMCallEvents.LLM_STREAM_OUTPUT, handler::onLlmStreamOutput);
        register(framework, LLMCallEvents.LLM_INVOKE_OUTPUT, handler::onLlmInvokeOutput);
        register(framework, LLMCallEvents.LLM_CALL_ERROR, handler::onLlmCallError);
        register(framework, ToolCallEvents.TOOL_CALL_STARTED, handler::onToolCallStarted);
        register(framework, ToolCallEvents.TOOL_CALL_FINISHED, handler::onToolCallFinished);
        register(framework, ToolCallEvents.TOOL_CALL_ERROR, handler::onToolCallError);
        register(framework, AgentEvents.AGENT_INVOKE_INPUT, handler::onAgentInvokeInput);
        register(framework, AgentEvents.AGENT_INVOKE_OUTPUT, handler::onAgentInvokeOutput);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback
    ) {
        framework.registerSync(
                event,
                callback,
                0,
                false,
                NAMESPACE,
                null,
                null,
                null,
                null,
                0,
                0.0,
                null,
                ""
        );
        REGISTERED.add(new Registration(event, callback));
    }

    private static AsyncCallbackFramework runnerCallbackFramework() {
        try {
            return Runner.getCallbackFramework();
        } catch (Exception error) {
            TEAM_LOGGER.warning("otel: cannot reach Runner.callback_framework - {}", error);
            return null;
        }
    }

    private record Registration(String event, Function<Map<String, Object>, Object> callback) {
    }
}
