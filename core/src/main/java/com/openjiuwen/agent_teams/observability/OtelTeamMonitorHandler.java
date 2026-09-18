/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.EventListener;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Event listener that turns TeamAgent event messages into telemetry spans/events.
 *
 * <p>Mirrors Python's {@code OtelTeamMonitorHandler} in
 * {@code openjiuwen/agent_teams/observability/monitor_handler.py}.</p>
 */
public class OtelTeamMonitorHandler implements EventListener {

    public static final String TRACER_NAME = "openjiuwen.agent_teams.observability.monitor";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final Set<String> TASK_OPEN_TYPES = Set.of(TeamEvent.TASK_CREATED);
    private static final Set<String> TASK_CLOSE_TYPES = Set.of(
            TeamEvent.TASK_COMPLETED,
            TeamEvent.TASK_CANCELLED,
            TeamEvent.TASK_UNBLOCKED
    );
    private static final Set<String> MEMBER_TYPES = Set.of(
            TeamEvent.MEMBER_SPAWNED,
            TeamEvent.MEMBER_RESTARTED,
            TeamEvent.MEMBER_STATUS_CHANGED,
            TeamEvent.MEMBER_EXECUTION_CHANGED,
            TeamEvent.MEMBER_SHUTDOWN,
            TeamEvent.MEMBER_CANCELED
    );
    private static final Set<String> MESSAGE_TYPES = Set.of(TeamEvent.MESSAGE, TeamEvent.BROADCAST);

    private final ObservabilityConfig config;
    private final TelemetryTracer injectedTracer;
    private final Map<String, TelemetrySpan> teamSpans = new LinkedHashMap<>();
    private final Map<String, TelemetrySpan> taskSpans = new LinkedHashMap<>();

    public OtelTeamMonitorHandler(ObservabilityConfig config) {
        this(config, null);
    }

    public OtelTeamMonitorHandler(ObservabilityConfig config, TelemetryTracer tracer) {
        this.config = config == null ? new ObservabilityConfig() : config;
        this.injectedTracer = tracer;
    }

    @Override
    public CompletionStage<Void> onEvent(EventMessage event) {
        try {
            String eventType = event.getEventType();
            Map<String, Object> payload = safePayload(event);
            String teamName = stringValue(payload.get("team_name"));

            if (TeamEvent.CREATED.equals(eventType)) {
                openTeamSpan(teamName, payload);
            } else if (TeamEvent.CLEANED.equals(eventType)) {
                closeTeamSpan(teamName);
            } else if (TeamEvent.STANDBY.equals(eventType)) {
                recordTeamEvent(teamName, eventType, Map.of(ObservabilitySemconv.AT_EVENT_TYPE, eventType));
            } else if (TASK_OPEN_TYPES.contains(eventType)) {
                openTaskSpan(teamName, payload);
            } else if (TASK_CLOSE_TYPES.contains(eventType)) {
                closeTaskSpan(payload, eventType);
            } else if (TeamEvent.TASK_UPDATED.equals(eventType) || TeamEvent.TASK_CLAIMED.equals(eventType)) {
                recordTaskEvent(payload, eventType);
            } else if (MEMBER_TYPES.contains(eventType)) {
                recordMemberEvent(teamName, payload, eventType);
            } else if (MESSAGE_TYPES.contains(eventType)) {
                recordMessageEvent(teamName, payload, eventType);
            }
        } catch (Exception error) {
            TEAM_LOGGER.warning("otel monitor handler failed for {}: {}", event == null ? "" : event.getEventType(), error);
        }
        return CompletableFuture.completedFuture(null);
    }

    public Map<String, TelemetrySpan> getTeamSpans() {
        return Map.copyOf(teamSpans);
    }

    public Map<String, TelemetrySpan> getTaskSpans() {
        return Map.copyOf(taskSpans);
    }

    private void openTeamSpan(String teamName, Map<String, Object> payload) {
        if (teamSpans.containsKey(teamName)) {
            return;
        }
        TelemetrySpan span = tracer().startSpan("team." + teamName, TelemetrySpan.Kind.INTERNAL);
        span.setAttribute(ObservabilitySemconv.AT_TEAM_NAME, teamName);
        span.setAttribute(
                ObservabilitySemconv.AT_TEAM_DISPLAY_NAME,
                stringValue(payload.getOrDefault("display_name", teamName))
        );
        span.setAttribute(ObservabilitySemconv.AT_EVENT_TYPE, TeamEvent.CREATED);
        Object leader = payload.get("leader_member_name");
        if (leader != null) {
            span.setAttribute("agentteam.team.leader", String.valueOf(leader));
        }
        teamSpans.put(teamName, span);
    }

    private void closeTeamSpan(String teamName) {
        TelemetrySpan span = teamSpans.remove(teamName);
        if (span == null) {
            return;
        }
        span.setStatus(TelemetrySpan.StatusCode.OK);
        span.end();
    }

    private void recordTeamEvent(String teamName, String name, Map<String, Object> attrs) {
        TelemetrySpan span = teamSpans.get(teamName);
        if (span != null) {
            span.addEvent(name, attrs);
        }
    }

    private void openTaskSpan(String teamName, Map<String, Object> payload) {
        String taskId = stringValue(payload.get("task_id"));
        if (taskId.isBlank() || taskSpans.containsKey(taskId)) {
            return;
        }
        TelemetrySpan span = tracer().startSpan("task." + taskId, TelemetrySpan.Kind.INTERNAL);
        span.setAttribute(ObservabilitySemconv.AT_TASK_ID, taskId);
        if (!teamName.isBlank()) {
            span.setAttribute(ObservabilitySemconv.AT_TEAM_NAME, teamName);
        }
        Object status = payload.get("status");
        if (status != null) {
            span.setAttribute(ObservabilitySemconv.AT_TASK_STATUS, String.valueOf(status));
        }
        Object assignee = payload.containsKey("assignee") ? payload.get("assignee") : payload.get("member_name");
        if (assignee != null) {
            span.setAttribute(ObservabilitySemconv.AT_TASK_ASSIGNEE, String.valueOf(assignee));
        }
        taskSpans.put(taskId, span);
    }

    private void closeTaskSpan(Map<String, Object> payload, String eventType) {
        String taskId = stringValue(payload.get("task_id"));
        TelemetrySpan span = taskSpans.remove(taskId);
        if (span == null) {
            return;
        }
        span.setAttribute(ObservabilitySemconv.AT_TASK_STATUS, eventType.replace("task_", ""));
        span.setStatus(TelemetrySpan.StatusCode.OK);
        span.end();
    }

    private void recordTaskEvent(Map<String, Object> payload, String eventType) {
        String taskId = stringValue(payload.get("task_id"));
        TelemetrySpan span = taskSpans.get(taskId);
        if (span == null) {
            return;
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ObservabilitySemconv.AT_EVENT_TYPE, eventType);
        attrs.put(ObservabilitySemconv.AT_TASK_ID, taskId);
        Object member = payload.get("member_name");
        if (member != null) {
            attrs.put(ObservabilitySemconv.AT_TASK_ASSIGNEE, String.valueOf(member));
        }
        span.addEvent(eventType, attrs);
    }

    private void recordMemberEvent(String teamName, Map<String, Object> payload, String eventType) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ObservabilitySemconv.AT_EVENT_TYPE, eventType);
        attrs.put(ObservabilitySemconv.AT_MEMBER_NAME, stringValue(payload.get("member_name")));
        putString(attrs, ObservabilitySemconv.AT_MEMBER_STATUS_OLD, payload.get("old_status"));
        putString(attrs, ObservabilitySemconv.AT_MEMBER_STATUS_NEW, payload.get("new_status"));
        putString(attrs, ObservabilitySemconv.AT_MEMBER_RESTART_REASON, payload.get("reason"));
        if (payload.get("restart_count") instanceof Number number) {
            attrs.put(ObservabilitySemconv.AT_MEMBER_RESTART_COUNT, number.longValue());
        }
        if (payload.containsKey("force")) {
            attrs.put(ObservabilitySemconv.AT_MEMBER_SHUTDOWN_FORCE, Boolean.parseBoolean(String.valueOf(payload.get("force"))));
        }
        recordTeamEvent(teamName, eventType, attrs);
    }

    private void recordMessageEvent(String teamName, Map<String, Object> payload, String eventType) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ObservabilitySemconv.AT_EVENT_TYPE, eventType);
        attrs.put(ObservabilitySemconv.AT_MESSAGE_ID, stringValue(payload.get("message_id")));
        attrs.put(ObservabilitySemconv.AT_MESSAGE_FROM, stringValue(payload.get("from_member_name")));
        attrs.put(ObservabilitySemconv.AT_MESSAGE_TO, stringValue(payload.get("to_member_name")));
        attrs.put(ObservabilitySemconv.AT_MESSAGE_BROADCAST, TeamEvent.BROADCAST.equals(eventType));
        recordTeamEvent(teamName, eventType, attrs);
    }

    private TelemetryTracer tracer() {
        if (injectedTracer != null) {
            return injectedTracer;
        }
        return ObservabilitySetup.getTracer(TRACER_NAME);
    }

    private static Map<String, Object> safePayload(EventMessage event) {
        if (event == null || event.getPayloadData() == null) {
            return Map.of();
        }
        return event.getPayloadData();
    }

    private static void putString(Map<String, Object> attrs, String key, Object value) {
        if (value != null) {
            attrs.put(key, String.valueOf(value));
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
