/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for plan request/response status transitions in
 * {@link OtelTeamMonitorHandler}.
 *
 * <p>Translates the following Python tests from
 * {@code test_observability.py} into JUnit 5:</p>
 * <ul>
 *   <li>{@code test_plan_request_advances_task_status_to_claimed} —
 *       TaskCreated followed by TaskPlanRequest advances
 *       {@code AT_TASK_STATUS} to {@code claimed} on the task span,
 *       and creates a {@code task.{id}.plan_request} child span.</li>
 *   <li>{@code test_plan_response_approved_and_rejected_paths} —
 *       Approved TaskPlanResponse sets status to {@code plan_approved};
 *       rejected response reverts status to {@code claimed}.</li>
 *   <li>{@code test_plan_event_span_io_split_on_semantic_boundary} —
 *       Plan request and response child spans carry correct
 *       event type, task ID, and status attributes.</li>
 * </ul>
 *
 * <p>Span tree exercised:</p>
 * <pre>
 * team.{name}                           [startTeamTrace / finalizeTeamTrace]
 * └── task.{taskId}                     [TASK_CREATED → closeAllSpans]
 *     ├── task.{taskId}.plan_request    [TASK_PLAN_REQUEST]
 *     └── task.{taskId}.plan_response   [TASK_PLAN_RESPONSE]
 * </pre>
 *
 * @since 0.1.7
 */
@DisplayName("Plan Request/Response status transition tests")
class PlanRequestResponseTest extends ObservabilityTestBase {

    // ================================================================
    // Plan request advances task status to "claimed"
    // Mirrors: test_plan_request_advances_task_status_to_claimed
    // ================================================================

    @Test
    @DisplayName("TaskPlanRequest advances AT_TASK_STATUS to claimed on task span")
    void test_plan_request_advances_task_status_to_claimed() {
        String team = "plan_team";
        String taskId = "plan-task-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-1");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // 1. Create the task.
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));

        // 2. Send plan request — status should advance to "claimed".
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed",
                "plan_id", "plan-1"
        )));

        // Close open task spans so the task span appears in the exporter.
        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        // --- Verify task span has status "claimed" ---
        List<SpanData> taskSpans = spansByName("task." + taskId);
        assertThat(taskSpans).as("task span should exist after create + plan_request").isNotEmpty();
        SpanData taskSpan = taskSpans.get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("task span status should be 'claimed' after plan_request")
                .isEqualTo("claimed");
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_ASSIGNEE))
                .as("task span assignee should be set from plan_request")
                .isEqualTo("member-1");
    }

    @Test
    @DisplayName("TaskPlanRequest creates child span task.{id}.plan_request")
    void test_plan_request_creates_child_span() {
        String team = "plan_child_team";
        String taskId = "plan-child-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-child");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        // --- Verify plan_request child span ---
        List<SpanData> planReqSpans = spansByName("task." + taskId + ".plan_request");
        assertThat(planReqSpans).as("task.{id}.plan_request child span should exist").isNotEmpty();
        SpanData planReqSpan = planReqSpans.get(0);

        assertThat(attr(planReqSpan, ObservabilitySemConv.AT_EVENT_TYPE))
                .isEqualTo(TeamEvent.TASK_PLAN_REQUEST);
        assertThat(attr(planReqSpan, ObservabilitySemConv.AT_TASK_ID))
                .isEqualTo(taskId);
        assertThat(attr(planReqSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("claimed");
        assertThat(attr(planReqSpan, ObservabilitySemConv.AT_TASK_ASSIGNEE))
                .isEqualTo("member-1");
        assertThat(planReqSpan.getStatus().getStatusCode())
                .as("plan_request child span should have OK status")
                .isEqualTo(StatusCode.OK);
    }

    @Test
    @DisplayName("TaskPlanRequest without explicit status defaults to claimed")
    void test_plan_request_without_status_defaults_to_claimed() {
        String team = "plan_default_team";
        String taskId = "plan-default-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-default");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        // No "status" field in payload — should default to "claimed".
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("task span status should default to 'claimed' when no status in payload")
                .isEqualTo("claimed");

        SpanData planReqSpan = spansByName("task." + taskId + ".plan_request").get(0);
        assertThat(attr(planReqSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("claimed");
    }

    // ================================================================
    // Plan response approved path
    // Mirrors: test_plan_response_approved_and_rejected_paths (approved part)
    // ================================================================

    @Test
    @DisplayName("approved TaskPlanResponse sets status to plan_approved")
    void test_plan_response_approved_sets_status_plan_approved() {
        String team = "plan_resp_team";
        String taskId = "plan-resp-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-resp-1");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // Create task, then plan request.
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        // Approved plan response.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", true,
                "status", "plan_approved",
                "plan_id", "plan-1",
                "member_name", "member-1",
                "feedback", "plan is correct"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        // --- Verify task span status is "plan_approved" ---
        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("task span status should be 'plan_approved' after approved response")
                .isEqualTo("plan_approved");
    }

    @Test
    @DisplayName("approved TaskPlanResponse creates child span with plan_approved status")
    void test_plan_response_approved_creates_child_span() {
        String team = "plan_resp_child_team";
        String taskId = "plan-resp-child-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-resp-child");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", true,
                "status", "plan_approved",
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        List<SpanData> planRespSpans = spansByName("task." + taskId + ".plan_response");
        assertThat(planRespSpans).as("task.{id}.plan_response child span should exist").isNotEmpty();
        SpanData planRespSpan = planRespSpans.get(0);

        assertThat(attr(planRespSpan, ObservabilitySemConv.AT_EVENT_TYPE))
                .isEqualTo(TeamEvent.TASK_PLAN_RESPONSE);
        assertThat(attr(planRespSpan, ObservabilitySemConv.AT_TASK_ID))
                .isEqualTo(taskId);
        assertThat(attr(planRespSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("plan_approved");
    }

    // ================================================================
    // Plan response rejected path
    // Mirrors: test_plan_response_approved_and_rejected_paths (rejected part)
    // ================================================================

    @Test
    @DisplayName("rejected TaskPlanResponse reverts status to claimed")
    void test_plan_response_rejected_reverts_to_claimed() {
        String team = "plan_reject_team";
        String taskId = "plan-reject-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-reject");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // Create task, then plan request (status → claimed).
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        // Rejected plan response — status should revert to "claimed".
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", false,
                "status", "claimed",
                "plan_id", "plan-1",
                "member_name", "member-1",
                "feedback", "needs revision"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        // --- Verify task span status reverted to "claimed" ---
        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("task span status should revert to 'claimed' after rejected response")
                .isEqualTo("claimed");
    }

    @Test
    @DisplayName("rejected TaskPlanResponse creates child span with claimed status")
    void test_plan_response_rejected_creates_child_span() {
        String team = "plan_reject_child_team";
        String taskId = "plan-reject-child-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-reject-child");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", false,
                "status", "claimed",
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        SpanData planRespSpan = spansByName("task." + taskId + ".plan_response").get(0);
        assertThat(attr(planRespSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("claimed");
    }

    // ================================================================
    // Plan response without explicit status uses approved flag
    // ================================================================

    @Test
    @DisplayName("TaskPlanResponse without status defaults to plan_approved when approved=true")
    void test_plan_response_no_status_approved_defaults() {
        String team = "plan_default_approved_team";
        String taskId = "plan-da-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-da");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1"
        )));
        // No "status" field — should default to "plan_approved" because approved=true.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", true,
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("status should default to 'plan_approved' when approved=true and no explicit status")
                .isEqualTo("plan_approved");
    }

    @Test
    @DisplayName("TaskPlanResponse without status defaults to claimed when approved=false")
    void test_plan_response_no_status_rejected_defaults() {
        String team = "plan_default_rejected_team";
        String taskId = "plan-dr-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-dr");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1"
        )));
        // No "status" field — should default to "claimed" because approved=false.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", false,
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("status should default to 'claimed' when approved=false and no explicit status")
                .isEqualTo("claimed");
    }

    // ================================================================
    // Full plan lifecycle: create → request → response(approved) → complete
    // ================================================================

    @Test
    @DisplayName("full plan lifecycle: create → request → approved → complete")
    void test_full_plan_lifecycle_approved() {
        String team = "plan_lifecycle_team";
        String taskId = "plan-lifecycle-1";

        ObservabilitySetup.startTeamTrace(team, "sess-lifecycle");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // 1. Task created with "pending" status.
        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending",
                "assignee", "member-1"
        )));

        // 2. Plan request advances to "claimed".
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        // 3. Plan response approved → "plan_approved".
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", true,
                "status", "plan_approved",
                "member_name", "member-1"
        )));

        // 4. Task completed.
        handler.accept(eventMessage(TeamEvent.TASK_COMPLETED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "result", "task done"
        )));

        ObservabilitySetup.finalizeTeamTrace(team);

        // --- Verify task span ---
        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .as("final task status should be 'completed'")
                .isEqualTo("completed");
        assertThat(taskSpan.getStatus().getStatusCode())
                .as("completed task should have OK status")
                .isEqualTo(StatusCode.OK);

        // --- Verify plan_request child span exists ---
        List<SpanData> planReqSpans = spansByName("task." + taskId + ".plan_request");
        assertThat(planReqSpans).as("plan_request child span should exist").isNotEmpty();
        assertThat(attr(planReqSpans.get(0), ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("claimed");

        // --- Verify plan_response child span exists ---
        List<SpanData> planRespSpans = spansByName("task." + taskId + ".plan_response");
        assertThat(planRespSpans).as("plan_response child span should exist").isNotEmpty();
        assertThat(attr(planRespSpans.get(0), ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("plan_approved");
    }

    // ================================================================
    // Plan request/response child spans are children of task span
    // Mirrors: test_plan_event_span_io_split_on_semantic_boundary (parent-child)
    // ================================================================

    @Test
    @DisplayName("plan_request and plan_response child spans are children of task span")
    void test_plan_child_spans_parent_is_task_span() {
        String team = "plan_parent_team";
        String taskId = "plan-parent-1";

        ObservabilitySetup.startTeamTrace(team, "sess-plan-parent");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", true,
                "status", "plan_approved",
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        SpanData taskSpan = spansByName("task." + taskId).get(0);
        SpanData planReqSpan = spansByName("task." + taskId + ".plan_request").get(0);
        SpanData planRespSpan = spansByName("task." + taskId + ".plan_response").get(0);

        assertThat(planReqSpan.getParentSpanId())
                .as("plan_request span parent should be task span")
                .isEqualTo(taskSpan.getSpanId());
        assertThat(planRespSpan.getParentSpanId())
                .as("plan_response span parent should be task span")
                .isEqualTo(taskSpan.getSpanId());
    }

    // ================================================================
    // Plan request without existing task span falls back to team event
    // ================================================================

    @Test
    @DisplayName("plan_request without existing task creates team-level span")
    void test_plan_request_without_task_creates_team_span() {
        String team = "plan_no_task_team";
        String taskId = "plan-no-task-1";

        ObservabilitySetup.startTeamTrace(team, "sess-no-task");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        // Send TASK_PLAN_REQUEST without first sending TASK_CREATED.
        // The handler should fall back to creating a team-level span.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        ObservabilitySetup.finalizeTeamTrace(team);

        // A span with the plan_request name should still exist (as team-level).
        List<SpanData> planReqSpans = spansByName("task." + taskId + ".plan_request");
        assertThat(planReqSpans).as("plan_request span should exist even without task span").isNotEmpty();
    }

    // ================================================================
    // Multiple plan requests on same task
    // ================================================================

    @Test
    @DisplayName("multiple plan requests on same task create multiple child spans")
    void test_multiple_plan_requests_create_multiple_spans() {
        String team = "plan_multi_team";
        String taskId = "plan-multi-1";

        ObservabilitySetup.startTeamTrace(team, "sess-multi");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .sampleRate(1.0)
                .build();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(config);

        handler.accept(eventMessage(TeamEvent.TASK_CREATED, Map.of(
                "team_name", team,
                "task_id", taskId,
                "status", "pending"
        )));

        // First plan request.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        // Rejected response reverts to claimed.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", false,
                "status", "claimed",
                "member_name", "member-1"
        )));

        // Second plan request.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_REQUEST, Map.of(
                "team_name", team,
                "task_id", taskId,
                "member_name", "member-1",
                "status", "claimed"
        )));

        // Second response approved.
        handler.accept(eventMessage(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", team,
                "task_id", taskId,
                "approved", true,
                "status", "plan_approved",
                "member_name", "member-1"
        )));

        handler.closeAllSpans();
        ObservabilitySetup.finalizeTeamTrace(team);

        // Should have 2 plan_request child spans and 2 plan_response child spans.
        List<SpanData> planReqSpans = spansByName("task." + taskId + ".plan_request");
        List<SpanData> planRespSpans = spansByName("task." + taskId + ".plan_response");
        assertThat(planReqSpans)
                .as("should have 2 plan_request child spans")
                .hasSize(2);
        assertThat(planRespSpans)
                .as("should have 2 plan_response child spans")
                .hasSize(2);

        // Final task span status should be "plan_approved".
        SpanData taskSpan = spansByName("task." + taskId).get(0);
        assertThat(attr(taskSpan, ObservabilitySemConv.AT_TASK_STATUS))
                .isEqualTo("plan_approved");
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
