/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * OTel handler that consumes the {@link EventMessage} stream from a leader {@code TeamAgent}.
 *
 * <p>The team span is managed by {@link ObservabilitySetup#startTeamTrace(String, String)}.
 * This handler only creates child spans (task/member/message) under the team span.</p>
 *
 * <p>Registered via {@code teamAgent.addEventListener(monitorHandler)} — the
 * Java event listener system invokes listeners as {@link Consumer}s.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.monitor_handler.OtelTeamMonitorHandler}.</p>
 *
 * @since 0.1.7
 */
public class OtelTeamMonitorHandler implements Consumer<EventMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(OtelTeamMonitorHandler.class);

    private static final String TRACER_NAME = "openjiuwen.agent_teams.observability.monitor";

    private static final Set<String> TASK_OPEN_TYPES = Set.of(TeamEvent.TASK_CREATED);
    private static final Set<String> TASK_CLOSE_TYPES = Set.of(TeamEvent.TASK_COMPLETED, TeamEvent.TASK_CANCELLED);
    private static final Set<String> TASK_EVENT_TYPES = Set.of(
            TeamEvent.TASK_CLAIMED, TeamEvent.TASK_UPDATED, TeamEvent.TASK_UNBLOCKED,
            TeamEvent.TASK_PLAN_REQUEST, TeamEvent.TASK_PLAN_RESPONSE);
    private static final Set<String> MEMBER_TYPES = Set.of(
            TeamEvent.MEMBER_SPAWNED, TeamEvent.MEMBER_RESTARTED, TeamEvent.MEMBER_STATUS_CHANGED,
            TeamEvent.MEMBER_EXECUTION_CHANGED, TeamEvent.MEMBER_SHUTDOWN, TeamEvent.MEMBER_CANCELED);
    private static final Set<String> MESSAGE_TYPES = Set.of(TeamEvent.MESSAGE, TeamEvent.BROADCAST);

    private final ObservabilityConfig config;
    private final Tracer tracer;
    private final Map<String, Span> taskSpans = new ConcurrentHashMap<>();

    /**
     * Construct a monitor handler with an explicit tracer.
     *
     * @param config the observability configuration
     * @param tracer the OTel tracer (injected for testing;
     *                production uses {@link ObservabilitySetup#getMonitorTracer()})
     * @since 0.1.7
     */
    public OtelTeamMonitorHandler(ObservabilityConfig config, Tracer tracer) {
        this.config = config;
        this.tracer = tracer;
    }

    /**
     * Construct a monitor handler that resolves the tracer lazily.
     *
     * @param config the observability configuration
     * @since 0.1.7
     */
    public OtelTeamMonitorHandler(ObservabilityConfig config) {
        this(config, null);
    }

    @Override
    public void accept(EventMessage event) {
        try {
            String etype = event.getEventType();
            Map<String, Object> payload = event.getPayload() != null
                    ? event.getPayload() : Map.of();
            String teamName = str(payload.get("team_name")).orElse(null);

            if (TeamEvent.CREATED.equals(etype)) {
                recordTeamCreated(teamName, payload);
            } else if (TeamEvent.CLEANED.equals(etype)) {
                recordTeamCleaned(teamName);
            } else if (TeamEvent.TEAM_COMPLETED.equals(etype)) {
                recordTeamCompleted(teamName, payload);
            } else if (TeamEvent.STANDBY.equals(etype)) {
                recordTeamEvent(teamName, "team.standby", attrs(TeamEvent.STANDBY));
            } else if (TASK_OPEN_TYPES.contains(etype)) {
                openTaskSpan(teamName, payload);
            } else if (TASK_CLOSE_TYPES.contains(etype)) {
                closeTaskSpan(teamName, payload, etype);
            } else if (TASK_EVENT_TYPES.contains(etype)) {
                recordTaskStatusSpan(teamName, payload, etype);
            } else if (TeamEvent.PLAN_APPROVAL.equals(etype)) {
                recordPlanApproval(teamName, payload);
            } else if (MEMBER_TYPES.contains(etype)) {
                recordMemberEvent(teamName, payload, etype);
            } else if (MESSAGE_TYPES.contains(etype)) {
                recordMessageEvent(teamName, payload, etype);
            } else {
                recordGenericEvent(teamName, etype, payload);
            }
        } catch (NullPointerException | IllegalStateException | ClassCastException e) {
            LOG.warn("otel monitor handler failed for {}: {}", event.getEventType(), e.getMessage());
        }
    }

    /**
     * Close every open task span and force-flush the provider.
     *
     * @since 0.1.7
     */
    public void closeAllSpans() {
        LOG.info("otel monitor: closeAllSpans - closing {} task spans", taskSpans.size());
        for (Map.Entry<String, Span> entry : taskSpans.entrySet()) {
            Span span = entry.getValue();
            if (span != null && span.getSpanContext().isValid()) {
                try {
                    span.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT, "closed");
                    span.setStatus(StatusCode.OK);
                    span.end();
                } catch (IllegalStateException | SecurityException e) {
                    LOG.warn("otel monitor: closeAllSpans failed for task={}: {}",
                            entry.getKey(), e.getMessage());
                }
            }
        }
        taskSpans.clear();
        ObservabilitySetup.forceFlushProvider(
                config != null ? config.getExportTimeoutMs() : 5000);
    }

    /**
     * Close task spans for a specific team.
     *
     * @param teamName the team name
     * @since 0.1.7
     */
    public void closeTeamSpans(String teamName) {
        for (Map.Entry<String, Span> entry : taskSpans.entrySet()) {
            Span span = entry.getValue();
            if (span != null && span.getSpanContext().isValid()) {
                try {
                    span.setStatus(StatusCode.OK);
                    span.end();
                } catch (IllegalStateException | SecurityException e) {
                    LOG.warn("otel monitor: closeTeamSpans failed for task={}: {}",
                            entry.getKey(), e.getMessage());
                }
            }
        }
        taskSpans.clear();
        ObservabilitySetup.forceFlushProvider(
                config != null ? config.getExportTimeoutMs() : 5000);
    }

    // ================================================================
    // Team span lifecycle
    // ================================================================

    /**
     * Stamp team-created attributes on the team span.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @since 0.1.7
     */
    private void recordTeamCreated(String teamName, Map<String, Object> payload) {
        Optional<Span> teamSpanOpt = OtelSpanContext.getTeamSpan();
        if (teamSpanOpt.isEmpty()) {
            LOG.warn("monitor: team_span is None for team={}, skip team_created", teamName);
            return;
        }
        Span teamSpan = teamSpanOpt.get();

        String displayName = str(payload.get("display_name"), teamName);
        teamSpan.setAttribute(ObservabilitySemConv.AT_TEAM_DISPLAY_NAME, displayName);
        teamSpan.setAttribute(ObservabilitySemConv.AT_EVENT_TYPE, TeamEvent.CREATED);

        String sessionId = str(payload.get("session_id")).orElse(null);
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = OtelSpanContext.getSessionId().orElse(null);
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            teamSpan.setAttribute(ObservabilitySemConv.LANGFUSE_SESSION_ID, sessionId);
        }

        String leader = str(payload.get("leader_member_name")).orElse(null);
        if (leader != null && !leader.isEmpty()) {
            teamSpan.setAttribute(ObservabilitySemConv.AT_TEAM_LEADER, leader);
        }

        String teamInput = str(payload.get("input"), str(payload.get("query")).orElse(null));
        if (teamInput != null && !teamInput.isEmpty()) {
            teamSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT, teamInput);
        }
    }

    /**
     * Record team.cleaned event and close dangling agent spans.
     *
     * @param teamName the team name
     * @since 0.1.7
     */
    private void recordTeamCleaned(String teamName) {
        recordTeamEvent(teamName, "team.cleaned", attrs(TeamEvent.CLEANED));
        OtelSpanContext.closeTeamAgentSpans(teamName);
    }

    /**
     * Record team.completed event.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @since 0.1.7
     */
    private void recordTeamCompleted(String teamName, Map<String, Object> payload) {
        recordTeamEvent(teamName, "team.completed", attrs(TeamEvent.TEAM_COMPLETED));
    }

    // ================================================================
    // Task span lifecycle
    // ================================================================

    /**
     * Open a task span under the team span.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @since 0.1.7
     */
    private void openTaskSpan(String teamName, Map<String, Object> payload) {
        String taskId = str(payload.get("task_id")).orElse(null);
        if (taskId == null || taskId.isEmpty()) {
            LOG.warn("otel monitor: openTaskSpan: no task_id in payload");
            return;
        }
        if (taskSpans.containsKey(taskId)) {
            LOG.debug("otel monitor: openTaskSpan: task {} already exists", taskId);
            return;
        }

        Span teamSpan = OtelSpanContext.getTeamSpan().orElse(null);
        if (teamSpan == null) {
            LOG.warn("monitor: no team span for team={}, skip task={}", teamName, taskId);
            return;
        }

        Context parentCtx = Context.current().with(teamSpan);
        Span taskSpan = resolveTracer().spanBuilder("task." + taskId)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentCtx)
                .startSpan();

        taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_ID, taskId);
        if (!teamName.isEmpty()) {
            taskSpan.setAttribute(ObservabilitySemConv.AT_TEAM_ID, teamName);
            taskSpan.setAttribute(ObservabilitySemConv.AT_TEAM_NAME, teamName);
        }
        taskSpan.setAttribute("agentteam.task.tag", "task:" + taskId);

        String status = str(payload.get("status")).orElse(null);
        if (status != null && !status.isEmpty()) {
            taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_STATUS, status);
        }

        String assignee = str(payload.get("assignee"), str(payload.get("member_name")).orElse(null));
        if (assignee != null && !assignee.isEmpty()) {
            taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_ASSIGNEE, assignee);
        }

        String taskContent = str(payload.get("content"), str(payload.get("title")).orElse(null));
        if (taskContent == null || taskContent.isEmpty()) {
            taskContent = str(payload.get("description")).orElse(null);
        }
        if (taskContent != null && !taskContent.isEmpty()) {
            taskSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT, taskContent);
        }

        String sid = OtelSpanContext.getSessionId().orElse(null);
        if (sid != null && !sid.isEmpty()) {
            taskSpan.setAttribute(ObservabilitySemConv.LANGFUSE_SESSION_ID, sid);
        }

        taskSpans.put(taskId, taskSpan);
    }

    /**
     * Close a task span.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @param etype    the event type
     * @since 0.1.7
     */
    private void closeTaskSpan(String teamName, Map<String, Object> payload, String etype) {
        String taskId = str(payload.get("task_id")).orElse(null);
        Span taskSpan = taskSpans.remove(taskId);
        if (taskSpan == null) {
            LOG.debug("monitor: closeTaskSpan: no existing span for task={}", taskId);
            return;
        }
        if (!taskSpan.getSpanContext().isValid()) {
            LOG.warn("monitor: closeTaskSpan: task {} span already ended", taskId);
            return;
        }

        String statusLabel = etype.replace("task_", "");
        String member = str(payload.get("member_name"), "");

        taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_STATUS, statusLabel);
        if (!member.isEmpty()) {
            taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_ASSIGNEE, member);
        }

        String taskResult = str(payload.get("result"), str(payload.get("output")).orElse(null));
        if (taskResult == null || taskResult.isEmpty()) {
            taskResult = etype;
        }
        taskSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_OUTPUT, taskResult);

        if (TeamEvent.TASK_CANCELLED.equals(etype)) {
            String reason = str(payload.get("reason"), str(payload.get("cancel_reason")).orElse(null));
            if (reason == null || reason.isEmpty()) {
                reason = "cancelled";
            }
            taskSpan.setStatus(StatusCode.ERROR, reason);
        } else {
            taskSpan.setStatus(StatusCode.OK);
        }
        taskSpan.end();
    }

    /**
     * Record a task status change span.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @param etype    the event type
     * @since 0.1.7
     */
    private void recordTaskStatusSpan(String teamName, Map<String, Object> payload, String etype) {
        String taskId = str(payload.get("task_id")).orElse(null);
        String member = str(payload.get("member_name"), "");
        Span taskSpan = taskSpans.get(taskId);

        String effectiveStatus = effectiveTaskStatus(etype, payload).orElse(null);

        if (taskSpan != null && taskSpan.getSpanContext().isValid()) {
            if (!member.isEmpty()) {
                taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_ASSIGNEE, member);
            }
            if (effectiveStatus != null && !effectiveStatus.isEmpty()) {
                taskSpan.setAttribute(ObservabilitySemConv.AT_TASK_STATUS, effectiveStatus);
            }
        }

        String statusLabel = etype.replace("task_", "");
        String spanName = "task." + taskId + "." + statusLabel;

        if (taskSpan != null && taskSpan.getSpanContext().isValid()) {
            Context taskCtx = Context.current().with(taskSpan);
            Span statusSpan = resolveTracer().spanBuilder(spanName)
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(taskCtx)
                    .startSpan();

            statusSpan.setAttribute(ObservabilitySemConv.AT_EVENT_TYPE, etype);
            statusSpan.setAttribute(ObservabilitySemConv.AT_TASK_ID, taskId);
            statusSpan.setAttribute(ObservabilitySemConv.AT_TASK_STATUS,
                    effectiveStatus != null && !effectiveStatus.isEmpty() ? effectiveStatus : statusLabel);
            if (!member.isEmpty()) {
                statusSpan.setAttribute(ObservabilitySemConv.AT_TASK_ASSIGNEE, member);
            }
            statusSpan.setStatus(StatusCode.OK);
            statusSpan.end();
        } else {
            recordTeamEvent(teamName, spanName, attrs(etype));
        }
    }

    /**
     * Record a plan approval event.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @since 0.1.7
     */
    private void recordPlanApproval(String teamName, Map<String, Object> payload) {
        boolean isApproved = bool(payload.get("approved"));
        String spanName = isApproved ? "plan.approved" : "plan.rejected";

        Span teamSpan = OtelSpanContext.getTeamSpan().orElse(null);
        if (teamSpan == null) {
            return;
        }
        Context parentCtx = Context.current().with(teamSpan);
        Span span = resolveTracer().spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentCtx)
                .startSpan();

        span.setAttribute(ObservabilitySemConv.AT_EVENT_TYPE, TeamEvent.PLAN_APPROVAL);
        span.setAttribute(ObservabilitySemConv.AT_PLAN_APPROVED, isApproved);
        String member = str(payload.get("member_name"), "");
        if (!member.isEmpty()) {
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_ID, member);
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_NAME, member);
            span.setAttribute(ObservabilitySemConv.AT_PLAN_SUBMITTED_BY, member);
        }
        span.setStatus(StatusCode.OK);
        span.end();
    }

    // ================================================================
    // Member / message events as short-lived child spans
    // ================================================================

    /**
     * Record a member event span.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @param etype    the event type
     * @since 0.1.7
     */
    private void recordMemberEvent(String teamName, Map<String, Object> payload, String etype) {
        String memberName = str(payload.get("member_name"), "");
        String suffix = etype.replace("member_", "");
        String spanName = memberName.isEmpty()
                ? "member." + suffix : "member." + memberName + "." + suffix;

        Span teamSpan = OtelSpanContext.getTeamSpan().orElse(null);
        if (teamSpan == null) {
            return;
        }
        Context parentCtx = Context.current().with(teamSpan);
        Span span = resolveTracer().spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentCtx)
                .startSpan();

        span.setAttribute(ObservabilitySemConv.AT_EVENT_TYPE, etype);
        span.setAttribute(ObservabilitySemConv.AT_MEMBER_ID, memberName);
        span.setAttribute(ObservabilitySemConv.AT_MEMBER_NAME, memberName);

        String oldStatus = str(payload.get("old_status")).orElse(null);
        if (oldStatus != null) {
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_STATUS_OLD, oldStatus);
        }
        String newStatus = str(payload.get("new_status")).orElse(null);
        if (newStatus != null) {
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_STATUS_NEW, newStatus);
        }
        String reason = str(payload.get("reason")).orElse(null);
        if (reason != null) {
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_RESTART_REASON, reason);
        }
        if (payload.containsKey("force")) {
            span.setAttribute(ObservabilitySemConv.AT_MEMBER_SHUTDOWN_FORCE, bool(payload.get("force")));
        }

        span.setStatus(StatusCode.OK);
        span.end();
    }

    /**
     * Record a message event span.
     *
     * @param teamName the team name
     * @param payload  the event payload
     * @param etype    the event type
     * @since 0.1.7
     */
    private void recordMessageEvent(String teamName, Map<String, Object> payload, String etype) {
        String fromName = str(payload.get("from_member_name"), "");
        String toName = str(payload.get("to_member_name"), "");
        boolean isBroadcast = TeamEvent.BROADCAST.equals(etype);

        String spanName = isBroadcast
                ? "msg.broadcast." + fromName : "msg." + fromName + "->" + toName;

        Span teamSpan = OtelSpanContext.getTeamSpan().orElse(null);
        if (teamSpan == null) {
            return;
        }
        Context parentCtx = Context.current().with(teamSpan);
        Span span = resolveTracer().spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentCtx)
                .startSpan();

        span.setAttribute(ObservabilitySemConv.AT_EVENT_TYPE, etype);
        span.setAttribute(ObservabilitySemConv.AT_MESSAGE_ID, str(payload.get("message_id"), ""));
        span.setAttribute(ObservabilitySemConv.AT_MESSAGE_FROM, fromName);
        span.setAttribute(ObservabilitySemConv.AT_MESSAGE_TO, toName);
        span.setAttribute(ObservabilitySemConv.AT_MESSAGE_BROADCAST, isBroadcast);

        span.setStatus(StatusCode.OK);
        span.end();
    }

    /**
     * Fallback handler for any team event not explicitly handled above.
     *
     * @param teamName the team name
     * @param etype    the event type
     * @param payload  the event payload
     * @since 0.1.7
     */
    private void recordGenericEvent(String teamName, String etype, Map<String, Object> payload) {
        String eventName = etype.replace("_", ".");
        String spanName = "event." + eventName;
        recordTeamEvent(teamName, spanName, attrs(etype));
    }

    // ================================================================
    // Shared helpers
    // ================================================================

    /**
     * Create a short-lived child span under the team span.
     *
     * @param teamName the team name
     * @param name     the span name
     * @param attrs    pre-built attributes map (event_type, etc.)
     * @since 0.1.7
     */
    private void recordTeamEvent(String teamName, String name, Map<String, String> attrs) {
        Span teamSpan = OtelSpanContext.getTeamSpan().orElse(null);
        if (teamSpan == null) {
            return;
        }
        Context parentCtx = Context.current().with(teamSpan);
        Span span = resolveTracer().spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentCtx)
                .startSpan();

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        if (!teamName.isEmpty()) {
            span.setAttribute(ObservabilitySemConv.AT_TEAM_ID, teamName);
            span.setAttribute(ObservabilitySemConv.AT_TEAM_NAME, teamName);
        }
        span.setStatus(StatusCode.OK);
        span.end();
    }

    /**
     * Resolve the effective task status for an event.
     *
     * @param etype   the event type
     * @param payload the event payload
     * @return an {@link Optional} containing the status string, or empty if unknown
     * @since 0.1.7
     */
    private static Optional<String> effectiveTaskStatus(String etype, Map<String, Object> payload) {
        if (TeamEvent.TASK_PLAN_REQUEST.equals(etype) || TeamEvent.TASK_PLAN_RESPONSE.equals(etype)) {
            String status = str(payload.get("status")).orElse(null);
            if (status != null && !status.isEmpty()) {
                return Optional.of(status);
            }
            if (TeamEvent.TASK_PLAN_RESPONSE.equals(etype)) {
                return bool(payload.get("approved")) ? Optional.of("plan_approved") : Optional.of("claimed");
            }
            return Optional.of("claimed");
        }
        if (TeamEvent.TASK_CLAIMED.equals(etype)) {
            return Optional.of("claimed");
        }
        if (TeamEvent.TASK_UNBLOCKED.equals(etype)) {
            return Optional.of("unblocked");
        }
        if (TeamEvent.TASK_UPDATED.equals(etype)) {
            return str(payload.get("status"));
        }
        return Optional.empty();
    }

    /**
     * Build a single-entry attributes map with just the event_type.
     *
     * @param eventType the event type
     * @return a map containing the event_type attribute
     * @since 0.1.7
     */
    private static Map<String, String> attrs(String eventType) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put(ObservabilitySemConv.AT_EVENT_TYPE, eventType);
        return m;
    }

    /**
     * Resolve the tracer, using the injected one or falling back to {@link ObservabilitySetup}.
     *
     * @return the OTel tracer
     * @since 0.1.7
     */
    private Tracer resolveTracer() {
        if (tracer != null) {
            return tracer;
        }
        return ObservabilitySetup.getTracer(TRACER_NAME).orElse(null);
    }

    /**
     * Convert an object to its string representation, returning {@code null} if null.
     *
     * @param obj the object
     * @return an {@link Optional} containing the string, or empty
     * @since 0.1.7
     */
    private static Optional<String> str(Object obj) {
        if (obj == null) {
            return Optional.empty();
        }
        return Optional.of(obj.toString());
    }

    /**
     * Convert an object to its string representation, returning a fallback.
     *
     * @param obj      the object
     * @param fallback the fallback value
     * @return the string, or the fallback
     * @since 0.1.7
     */
    private static String str(Object obj, String fallback) {
        if (obj == null) {
            return fallback;
        }
        String s = obj.toString();
        return s.isEmpty() ? fallback : s;
    }

    /**
     * Convert an object to a boolean.
     *
     * @param obj the object
     * @return {@code true} if the object is truthy
     * @since 0.1.7
     */
    private static boolean bool(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean isBool) {
            return isBool;
        }
        return Boolean.parseBoolean(obj.toString());
    }
}
