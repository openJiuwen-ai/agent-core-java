/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OtelTeamMonitorHandler}.
 *
 * <p>Translates the Python tests
 * {@code test_team_monitor_handler_emits_team_and_task_spans},
 * {@code test_plan_request_advances_task_status_to_claimed},
 * {@code test_plan_response_approved_and_rejected_paths},
 * and {@code test_member_invoke_does_not_close_team_span} from
 * {@code test_observability.py} into JUnit 5.</p>
 *
 * <p>Verifies that the monitor handler creates child spans (task, member,
 * message, plan) under the team span with correct attributes and status.</p>
 *
 * @since 0.1.7
 */
@DisplayName("OtelTeamMonitorHandler tests")
class OtelTeamMonitorHandlerTest extends ObservabilityTestBase {

    // ================================================================
    // Team + task + member + message spans (end-to-end)
    // ================================================================

    @Test
    @DisplayName("monitor handler emits team, task, member, and message spans")
    void test_team_monitor_handler_emits_team_and_task_spans() {
        // 1. Create team span (simulates Runner._maybe_attach_observability).
        ObservabilitySetup.startTeamTrace("alpha", "sess-1");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // 2. Team created event stamps display name, leader, input.
        handler.accept(eventMessage(TeamEvent.CREATED, Map.of(
                "team_name", "alpha",
                "display_name", "Alpha Team",
                "leader_member_name", "leader",
                "session_id", "sess-1",
                "input", "solve the problem"
        )));

        // 3. Member spawned.
        handler.accept(eventMessage(TeamEvent.MEMBER_SPAWNED, Map.of(
                "team_name", "alpha",
                "member_name", "alice"
        )));

        // 4. Member status changed.
        handler.accept(eventMessage(TeamEvent.MEMBER_STATUS_CHANGED, Map.of(
                "team_name", "alpha",
                "member_name", "alice",
                "old_status", "UNSTARTED",
                "new_status", "READY"
        )));

        // 5. Direct message.
        handler.accept(eventMessage(TeamEvent.MESSAGE, Map.of(
                "team_name", "alpha",
                "message_id", "m1",
                "from_member_name", "leader",
                "to_member_name", "alice"
        )));

        // 6. Broadcast message.
        handler.accept(eventMessage(TeamEvent.BROADCAST, Map.of(
                "team_name", "alpha",
                "message_id", "m2",
                "from_member_name", "leader"
        )));

        // 7. Task created.
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "alpha",
                "task_id", "t1",
                "status", "open"
        )));

        // 8. Task completed.
        handler.accept(eventMessage(TeamEvent.TASK_COMPLETED, Map.of(
                "team_name", "alpha",
                "task_id", "t1"
        )));

        // 9. Team cleaned.
        handler.accept(eventMessage(TeamEvent.CLEANED, Map.of(
                "team_name", "alpha"
        )));

        // Close the team span so it appears in the exporter.
        ObservabilitySetup.finalizeTeamTrace("alpha");

        // --- Verify team span ---
        List<SpanData> teamSpans = spansByName("team.alpha");
        assertThat(teamSpans).as("team root span missing").isNotEmpty();
        SpanData teamSpan = teamSpans.get(0);
        assertThat(attr(teamSpan, ObservabilitySemConv.AT_TEAM_NAME)).isEqualTo("alpha");
        assertThat(attr(teamSpan, ObservabilitySemConv.AT_TEAM_DISPLAY_NAME)).isEqualTo("Alpha Team");
        assertThat(attr(teamSpan, ObservabilitySemConv.AT_TEAM_LEADER)).isEqualTo("leader");

        // --- Verify member spawned span ---
        List<SpanData> memberSpans = spansByName("member.alice.spawned");
        assertThat(memberSpans).as("member.alice.spawned span missing").isNotEmpty();

        // --- Verify message span ---
        List<SpanData> msgSpans = spansByName("msg.leader->alice");
        assertThat(msgSpans).as("msg.leader->alice span missing").isNotEmpty();

        // --- Verify broadcast span ---
        List<SpanData> bcSpans = spansByName("msg.broadcast.leader");
        assertThat(bcSpans).as("msg.broadcast.leader span missing").isNotEmpty();

        // --- Verify task span ---
        List<SpanData> taskSpans = spansByName("task.t1");
        assertThat(taskSpans).as("task span missing").isNotEmpty();
        SpanData taskSpan = taskSpans.get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("completed");
    }

    // ================================================================
    // Task span lifecycle
    // ================================================================

    @Test
    @DisplayName("task span has correct attributes on creation")
    void test_task_span_attributes_on_creation() {
        ObservabilitySetup.startTeamTrace("task_team", "sess-task");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "task_team",
                "task_id", "task-1",
                "status", "pending",
                "assignee", "worker-1",
                "content", "implement feature X"
        )));

        // Close open task spans so they appear in the exporter.
        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace("task_team");

        List<SpanData> taskSpans = spansByName("task.task-1");
        assertThat(taskSpans).as("task span missing").isNotEmpty();
        SpanData taskSpan = taskSpans.get(0);

        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_ID)).isEqualTo("task-1");
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS)).isEqualTo("pending");
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_ASSIGNEE)).isEqualTo("worker-1");
        assertThat(attr(taskSpan, ObservabilitySemConv.LANGFUSE_OBSERVATION_INPUT))
                .isEqualTo("implement feature X");
        assertThat(attr(taskSpan, ObservabilitySemConv.LANGFUSE_SESSION_ID))
                .isEqualTo("sess-task");
    }

    @Test
    @DisplayName("task completed span has OK status")
    void test_task_completed_span_has_ok_status() {
        ObservabilitySetup.startTeamTrace("complete_team", "sess-complete");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "complete_team",
                "task_id", "t-done",
                "status", "open"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_COMPLETED, Map.of(
                "team_name", "complete_team",
                "task_id", "t-done",
                "member_name", "worker-1",
                "result", "feature implemented"
        )));

        ObservabilitySetup.finalizeTeamTrace("complete_team");

        List<SpanData> taskSpans = spansByName("task.t-done");
        assertThat(taskSpans).as("task span missing").isNotEmpty();
        SpanData taskSpan = taskSpans.get(0);
        assertThat(taskSpan.getStatus().getStatusCode())
                .as("completed task should have OK status")
                .isEqualTo(StatusCode.OK);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("completed");
    }

    @Test
    @DisplayName("task cancelled span has ERROR status")
    void test_task_cancelled_span_has_error_status() {
        ObservabilitySetup.startTeamTrace("cancel_team", "sess-cancel");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "cancel_team",
                "task_id", "t-cancel",
                "status", "open"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_CANCELLED, Map.of(
                "team_name", "cancel_team",
                "task_id", "t-cancel",
                "reason", "duplicate task"
        )));

        ObservabilitySetup.finalizeTeamTrace("cancel_team");

        List<SpanData> taskSpans = spansByName("task.t-cancel");
        assertThat(taskSpans).as("task span missing").isNotEmpty();
        SpanData taskSpan = taskSpans.get(0);
        assertThat(taskSpan.getStatus().getStatusCode())
                .as("cancelled task should have ERROR status")
                .isEqualTo(StatusCode.ERROR);
    }

    @Test
    @DisplayName("duplicate task_created does not create second span")
    void test_duplicate_task_created_no_second_span() {
        ObservabilitySetup.startTeamTrace("dup_team", "sess-dup");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "dup_team",
                "task_id", "dup-1",
                "status", "open"
        )));
        // Second create for same task_id should be ignored.
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "dup_team",
                "task_id", "dup-1",
                "status", "open"
        )));

        // Close open task spans so they appear in the exporter.
        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace("dup_team");

        List<SpanData> taskSpans = spansByName("task.dup-1");
        assertThat(taskSpans).as("task span missing").hasSize(1);
    }

    @Test
    @DisplayName("task_created without task_id is skipped")
    void test_task_created_without_task_id_skipped() {
        ObservabilitySetup.startTeamTrace("no_id_team", "sess-no-id");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "no_id_team",
                "status", "open"
        )));

        ObservabilitySetup.finalizeTeamTrace("no_id_team");

        // No task span should exist.
        List<SpanData> allSpans = finishedSpans();
        assertThat(allSpans.stream().noneMatch(s -> s.getName().startsWith("task.")))
                .as("no task span should be created without task_id")
                .isTrue();
    }

    // ================================================================
    // Member event spans
    // ================================================================

    @Test
    @DisplayName("member spawned span has member name and event type")
    void test_member_spawned_span_attributes() {
        ObservabilitySetup.startTeamTrace("member_team", "sess-member");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.MEMBER_SPAWNED, Map.of(
                "team_name", "member_team",
                "member_name", "bob"
        )));

        ObservabilitySetup.finalizeTeamTrace("member_team");

        List<SpanData> memberSpans = spansByName("member.bob.spawned");
        assertThat(memberSpans).as("member span missing").isNotEmpty();
        SpanData memberSpan = memberSpans.get(0);
        assertThat(attr(memberSpan, ObservabilitySemConv.AT_EVENT_TYPE))
                .isEqualTo(TeamEvent.MEMBER_SPAWNED);
        assertThat(attr(memberSpan, ObservabilitySemConv.AT_MEMBER_ID)).isEqualTo("bob");
        assertThat(attr(memberSpan, ObservabilitySemConv.AT_MEMBER_NAME)).isEqualTo("bob");
    }

    @Test
    @DisplayName("member status changed span has old and new status")
    void test_member_status_changed_span_attributes() {
        ObservabilitySetup.startTeamTrace("status_team", "sess-status");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.MEMBER_STATUS_CHANGED, Map.of(
                "team_name", "status_team",
                "member_name", "carol",
                "old_status", "READY",
                "new_status", "BUSY"
        )));

        ObservabilitySetup.finalizeTeamTrace("status_team");

        List<SpanData> memberSpans = spansByName("member.carol.status_changed");
        assertThat(memberSpans).as("member status_changed span missing").isNotEmpty();
        SpanData memberSpan = memberSpans.get(0);
        assertThat(attr(memberSpan, ObservabilitySemConv.AT_MEMBER_STATUS_OLD)).isEqualTo("READY");
        assertThat(attr(memberSpan, ObservabilitySemConv.AT_MEMBER_STATUS_NEW)).isEqualTo("BUSY");
    }

    @Test
    @DisplayName("member shutdown span with force flag")
    void test_member_shutdown_span_with_force() {
        ObservabilitySetup.startTeamTrace("shutdown_team", "sess-shutdown");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.MEMBER_SHUTDOWN, Map.of(
                "team_name", "shutdown_team",
                "member_name", "dave",
                "force", true
        )));

        ObservabilitySetup.finalizeTeamTrace("shutdown_team");

        List<SpanData> memberSpans = spansByName("member.dave.shutdown");
        assertThat(memberSpans).as("member shutdown span missing").isNotEmpty();
        SpanData memberSpan = memberSpans.get(0);
        Object forceAttr = memberSpan.getAttributes()
                .get(AttributeKey.booleanKey(ObservabilitySemConv.AT_MEMBER_SHUTDOWN_FORCE));
        assertThat(forceAttr).isEqualTo(true);
    }

    // ================================================================
    // Message event spans
    // ================================================================

    @Test
    @DisplayName("direct message span has from, to, and message_id")
    void test_direct_message_span_attributes() {
        ObservabilitySetup.startTeamTrace("msg_team", "sess-msg");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.MESSAGE, Map.of(
                "team_name", "msg_team",
                "message_id", "msg-001",
                "from_member_name", "leader",
                "to_member_name", "worker"
        )));

        ObservabilitySetup.finalizeTeamTrace("msg_team");

        List<SpanData> msgSpans = spansByName("msg.leader->worker");
        assertThat(msgSpans).as("message span missing").isNotEmpty();
        SpanData msgSpan = msgSpans.get(0);
        assertThat(attr(msgSpan, ObservabilitySemConv.AT_MESSAGE_ID)).isEqualTo("msg-001");
        assertThat(attr(msgSpan, ObservabilitySemConv.AT_MESSAGE_FROM)).isEqualTo("leader");
        assertThat(attr(msgSpan, ObservabilitySemConv.AT_MESSAGE_TO)).isEqualTo("worker");
        Object isBroadcast = msgSpan.getAttributes()
                .get(AttributeKey.booleanKey(ObservabilitySemConv.AT_MESSAGE_BROADCAST));
        assertThat(isBroadcast).isEqualTo(false);
    }

    @Test
    @DisplayName("broadcast message span has broadcast flag true")
    void test_broadcast_message_span_attributes() {
        ObservabilitySetup.startTeamTrace("bc_team", "sess-bc");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.BROADCAST, Map.of(
                "team_name", "bc_team",
                "message_id", "bc-001",
                "from_member_name", "leader"
        )));

        ObservabilitySetup.finalizeTeamTrace("bc_team");

        List<SpanData> bcSpans = spansByName("msg.broadcast.leader");
        assertThat(bcSpans).as("broadcast span missing").isNotEmpty();
        SpanData bcSpan = bcSpans.get(0);
        Object isBroadcast = bcSpan.getAttributes()
                .get(AttributeKey.booleanKey(ObservabilitySemConv.AT_MESSAGE_BROADCAST));
        assertThat(isBroadcast).isEqualTo(true);
    }

    // ================================================================
    // Plan approval spans
    // ================================================================

    @Test
    @DisplayName("plan approved span has approved=true")
    void test_plan_approved_span() {
        ObservabilitySetup.startTeamTrace("plan_team", "sess-plan");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.PLAN_APPROVAL, Map.of(
                "team_name", "plan_team",
                "approved", true,
                "member_name", "planner"
        )));

        ObservabilitySetup.finalizeTeamTrace("plan_team");

        List<SpanData> planSpans = spansByName("plan.approved");
        assertThat(planSpans).as("plan.approved span missing").isNotEmpty();
        SpanData planSpan = planSpans.get(0);
        Object approved = planSpan.getAttributes()
                .get(AttributeKey.booleanKey(ObservabilitySemConv.AT_PLAN_APPROVED));
        assertThat(approved).isEqualTo(true);
        assertThat(attr(planSpan, ObservabilitySemConv.AT_PLAN_SUBMITTED_BY)).isEqualTo("planner");
    }

    @Test
    @DisplayName("plan rejected span has approved=false")
    void test_plan_rejected_span() {
        ObservabilitySetup.startTeamTrace("reject_team", "sess-reject");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.PLAN_APPROVAL, Map.of(
                "team_name", "reject_team",
                "approved", false,
                "member_name", "planner"
        )));

        ObservabilitySetup.finalizeTeamTrace("reject_team");

        List<SpanData> planSpans = spansByName("plan.rejected");
        assertThat(planSpans).as("plan.rejected span missing").isNotEmpty();
        SpanData planSpan = planSpans.get(0);
        Object approved = planSpan.getAttributes()
                .get(AttributeKey.booleanKey(ObservabilitySemConv.AT_PLAN_APPROVED));
        assertThat(approved).isEqualTo(false);
    }

    // ================================================================
    // Team lifecycle events
    // ================================================================

    @Test
    @DisplayName("team standby event creates team.standby span")
    void test_team_standby_event() {
        ObservabilitySetup.startTeamTrace("standby_team", "sess-standby");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.STANDBY, Map.of(
                "team_name", "standby_team"
        )));

        ObservabilitySetup.finalizeTeamTrace("standby_team");

        List<SpanData> standbySpans = spansByName("team.standby");
        assertThat(standbySpans).as("team.standby span missing").isNotEmpty();
    }

    @Test
    @DisplayName("team completed event creates team.completed span")
    void test_team_completed_event() {
        ObservabilitySetup.startTeamTrace("completed_team", "sess-completed");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TEAM_COMPLETED, Map.of(
                "team_name", "completed_team"
        )));

        ObservabilitySetup.finalizeTeamTrace("completed_team");

        List<SpanData> completedSpans = spansByName("team.completed");
        assertThat(completedSpans).as("team.completed span missing").isNotEmpty();
    }

    @Test
    @DisplayName("team cleaned event creates team.cleaned span")
    void test_team_cleaned_event() {
        ObservabilitySetup.startTeamTrace("cleaned_team", "sess-cleaned");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.CLEANED, Map.of(
                "team_name", "cleaned_team"
        )));

        ObservabilitySetup.finalizeTeamTrace("cleaned_team");

        List<SpanData> cleanedSpans = spansByName("team.cleaned");
        assertThat(cleanedSpans).as("team.cleaned span missing").isNotEmpty();
    }

    // ================================================================
    // Task status change spans
    // ================================================================

    @Test
    @DisplayName("task claimed creates status child span")
    void test_task_claimed_creates_status_span() {
        ObservabilitySetup.startTeamTrace("claim_team", "sess-claim");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "claim_team",
                "task_id", "claim-1",
                "status", "open"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_CLAIMED, Map.of(
                "team_name", "claim_team",
                "task_id", "claim-1",
                "member_name", "claimer"
        )));

        // Close open task spans so they appear in the exporter.
        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace("claim_team");

        List<SpanData> statusSpans = spansByName("task.claim-1.claimed");
        assertThat(statusSpans).as("task.claimed span missing").isNotEmpty();
        SpanData statusSpan = statusSpans.get(0);
        assertThat(attr(statusSpan, ObservabilitySemConv.AT_TASK_STATUS)).isEqualTo("claimed");
        assertThat(attr(statusSpan, ObservabilitySemConv.AT_TASK_ASSIGNEE)).isEqualTo("claimer");
    }

    @Test
    @DisplayName("task unblocked creates status child span")
    void test_task_unblocked_creates_status_span() {
        ObservabilitySetup.startTeamTrace("unblock_team", "sess-unblock");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "unblock_team",
                "task_id", "ub-1",
                "status", "blocked"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_UNBLOCKED, Map.of(
                "team_name", "unblock_team",
                "task_id", "ub-1",
                "member_name", "unblocker"
        )));

        // Close open task spans so they appear in the exporter.
        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace("unblock_team");

        List<SpanData> statusSpans = spansByName("task.ub-1.unblocked");
        assertThat(statusSpans).as("task.unblocked span missing").isNotEmpty();
    }

    // ================================================================
    // Generic event fallback
    // ================================================================

    @Test
    @DisplayName("unknown event type creates generic event span")
    void test_unknown_event_creates_generic_span() {
        ObservabilitySetup.startTeamTrace("generic_team", "sess-generic");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage("custom_event", Map.of(
                "team_name", "generic_team"
        )));

        ObservabilitySetup.finalizeTeamTrace("generic_team");

        List<SpanData> genericSpans = spansByName("event.custom.event");
        assertThat(genericSpans).as("generic event span missing").isNotEmpty();
    }

    // ================================================================
    // Member invoke does not close team span
    // ================================================================

    @Test
    @DisplayName("member invoke does not close team span")
    void test_member_invoke_does_not_close_team_span() {
        ObservabilitySetup.startTeamTrace("invoke_team", "sess-invoke");

        // Verify team span exists and is valid.
        assertThat(OtelSpanContext.getTeamSpan()).isPresent();
        Span teamSpan = OtelSpanContext.getTeamSpan().get();
        assertThat(teamSpan.getSpanContext().isValid()).isTrue();

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // Simulate member events that should NOT close the team span.
        handler.accept(eventMessage(TeamEvent.MEMBER_SPAWNED, Map.of(
                "team_name", "invoke_team",
                "member_name", "worker"
        )));
        handler.accept(eventMessage(TeamEvent.MEMBER_STATUS_CHANGED, Map.of(
                "team_name", "invoke_team",
                "member_name", "worker",
                "old_status", "READY",
                "new_status", "BUSY"
        )));
        handler.accept(eventMessage(TeamEvent.MEMBER_SHUTDOWN, Map.of(
                "team_name", "invoke_team",
                "member_name", "worker"
        )));

        // Team span should still be valid after member events.
        assertThat(OtelSpanContext.getTeamSpan()).isPresent();
        assertThat(OtelSpanContext.getTeamSpan().get().getSpanContext().isValid())
                .as("team span should still be valid after member events")
                .isTrue();

        ObservabilitySetup.finalizeTeamTrace("invoke_team");

        // After finalize, team span should be ended.
        assertThat(OtelSpanContext.getTeamSpan()).isEmpty();
    }

    // ================================================================
    // Error handling
    // ================================================================

    @Test
    @DisplayName("handler does not throw on null payload")
    void test_handler_null_payload_does_not_throw() {
        ObservabilitySetup.startTeamTrace("null_team", "sess-null");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        EventMessage event = EventMessage.builder()
                .eventType(TeamEvent.TASK_CREATED)
                .payload(null)
                .build();

        // Should not throw.
        handler.accept(event);

        ObservabilitySetup.finalizeTeamTrace("null_team");
    }

    @Test
    @DisplayName("handler does not throw on missing team span")
    void test_handler_missing_team_span_does_not_throw() {
        // Do NOT call startTeamTrace — no team span exists.
        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // Should not throw even without a team span.
        handler.accept(eventMessage(TeamEvent.MEMBER_SPAWNED, Map.of(
                "team_name", "no_span_team",
                "member_name", "orphan"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "no_span_team",
                "task_id", "orphan-1",
                "status", "open"
        )));
    }

    // ================================================================
    // closeAllSpans / closeTeamSpans
    // ================================================================

    @Test
    @DisplayName("closeAllSpans closes all open task spans")
    void test_close_all_spans_closes_open_tasks() {
        ObservabilitySetup.startTeamTrace("close_team", "sess-close");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // Create tasks but don't complete them.
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "close_team",
                "task_id", "open-1",
                "status", "open"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", "close_team",
                "task_id", "open-2",
                "status", "open"
        )));

        // Close all spans.
        handler.closeAllSpans();

        ObservabilitySetup.finalizeTeamTrace("close_team");

        // Both task spans should be ended and have OK status.
        List<SpanData> task1Spans = spansByName("task.open-1");
        List<SpanData> task2Spans = spansByName("task.open-2");
        assertThat(task1Spans).as("task open-1 span missing").isNotEmpty();
        assertThat(task2Spans).as("task open-2 span missing").isNotEmpty();
        assertThat(task1Spans.get(0).getStatus().getStatusCode())
                .isEqualTo(StatusCode.OK);
        assertThat(task2Spans.get(0).getStatus().getStatusCode())
                .isEqualTo(StatusCode.OK);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private EventMessage eventMessage(String eventType, Map<String, Object> payload) {
        return EventMessage.builder()
                .eventType(eventType)
                .payload(new LinkedHashMap<>(payload))
                .build();
    }
}
