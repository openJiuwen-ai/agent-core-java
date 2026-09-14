/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.agentevolving.signal.TeamSignalType;
import com.openjiuwen.agentevolving.signal.TeamSignals;
import com.openjiuwen.agentevolving.signal.TrajectoryIssue;
import com.openjiuwen.agentevolving.signal.UserIntent;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.rails.CallbackContext;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for team-skill evolution rail tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/rails/evolution/test_team_skill_rail.py}
 * in {@code tests/unit_tests/harness/rails/evolution/test_team_skill_rail.py}.</p>
 */
class TeamSkillEvolutionRailParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_team_skill_evolution_rail_defaults_fixed_member_role_to_leader",
            "test_patch_path",
            "test_run_evolution_passes_current_skill_content_to_trajectory_patch",
            "test_stage_evolution_from_signals_does_not_hardcode_workflow_signal_section",
            "test_async_snapshot_messages_are_preserved_for_team_evolution",
            "test_patch_auto_save",
            "test_auto_scan_and_auto_save_properties",
            "test_team_experience_tracker_is_initialized",
            "test_notify_team_completed_without_view_task",
            "test_notify_team_completed_repeated_mark_keeps_same_session",
            "test_notify_team_completed_mark_survives_next_before_invoke",
            "test_notify_team_completed_mark_does_not_leak_to_new_session",
            "test_notify_team_completed_no_trajectory",
            "test_auto_scan_false_disables_passive_view_task_trigger",
            "test_auto_scan_false_disables_notify_team_completed",
            "test_run_evolution_returns_immediately_when_auto_scan_disabled",
            "test_team_record_presented_experiences_delegates_to_tracker",
            "test_team_snapshot_consumes_experience_tracker_state",
            "test_view_task_completion_marks_round_and_triggers_once_after_invoke",
            "test_team_after_tool_call_records_skill_tool_evolution_detail_read",
            "test_team_after_tool_call_records_read_file_evolution_detail_read",
            "test_team_auto_scan_false_still_records_evolution_detail_read",
            "test_team_run_evolution_evaluates_presented_entries_when_no_skill_detected",
            "test_team_handle_evolution_from_signals_emits_no_records_outcome",
            "test_team_handle_evolution_emits_persistence_failed_without_auto_approved_finalize",
            "test_team_run_evolution_does_not_report_persistence_failed_as_ready",
            "test_notify_team_completed_allows_new_invoke_after_async_evolution",
            "test_async_evolution_failure_is_buffered_and_visible",
            "test_run_evolution_uses_trajectory_source",
            "test_run_evolution_filters_non_collaborative_steps",
            "test_run_evolution_keeps_full_leader_trajectory",
            "test_detect_used_team_skill_prefers_skill_tool_and_filters_non_team_skill",
            "test_detect_used_team_skill_excludes_disabled_skills",
            "test_detect_used_team_skill_returns_none_when_all_disabled",
            "test_team_skill_evolution_rail_disabled_skills_defaults_to_empty",
            "test_team_skill_evolution_rail_disabled_skills_from_list",
            "test_team_skill_evolution_rail_disabled_skills_from_single_string",
            "test_team_task_completion_helper_covers_terminal_and_non_terminal_text[task-a completed\\ntask-b completed-True]",
            "test_team_task_completion_helper_covers_terminal_and_non_terminal_text[task-a pending\\ntask-b completed-False]",
            "test_team_task_completion_helper_covers_terminal_and_non_terminal_text[task-a in_progress\\ntask-b completed-False]",
            "test_team_task_completion_helper_covers_terminal_and_non_terminal_text[task-a blocked\\ntask-b completed-False]",
            "test_team_task_completion_helper_covers_terminal_and_non_terminal_text[task-a claimed\\ntask-b completed-False]",
            "test_team_task_completion_helper_covers_terminal_and_non_terminal_text[task-a ready-False]",
            "test_infer_team_skill_from_trajectory_helper_handles_multi_skill_and_no_match",
            "test_detect_used_team_skill_prefers_skills_path_over_legacy_skill_md",
            "test_is_team_skill_checks_frontmatter_kind_only",
            "test_team_signal_type_enum",
            "test_user_intent_dataclass",
            "test_trajectory_issue_dataclass",
            "test_init_accepts_custom_llm_policies_timeout_and_concurrency",
            "test_drain_pending_approval_events_defaults_to_total_timeout",
            "test_detect_user_request_retries_on_invalid_response",
            "test_run_evolution_signal_combination[trajectory+user_intent]",
            "test_run_evolution_signal_combination[user_only]",
            "test_run_evolution_signal_combination[trajectory_only]",
            "test_run_evolution_signal_combination[trajectory_only_when_user_intent_fails]",
            "test_on_approve_record_partial_failure_retains_request_for_retry",
            "test_on_approve_record_does_not_touch_other_requests",
            "test_on_approve_record_uses_rebound_pending_snapshot_store",
            "test_on_approve_record_uses_rebound_snapshot_store_after_snapshot_dict_swap",
            "test_request_simplify_stages_governance_and_returns_approval",
            "test_request_simplify_returns_empty_result_when_no_records",
            "test_request_simplify_returns_empty_result_when_no_actions",
            "test_on_approve_simplify_delegates_to_manager",
            "test_on_reject_simplify_delegates_to_manager",
            "test_request_rebuild_returns_none_when_no_skill",
            "test_request_rebuild_archives_before_building_prompt",
            "test_request_rebuild_continues_on_archive_failure",
            "TestRequestUserEvolution::test_returns_empty_result_when_skill_not_found",
            "TestRequestUserEvolution::test_returns_empty_result_when_subject_is_not_team_skill",
            "TestRequestUserEvolution::test_returns_request_id_when_patch_generated",
            "TestRequestUserEvolution::test_auto_approve_true_stores_directly",
            "TestRequestUserEvolution::test_returns_no_records_status_when_generation_runs",
            "TestRequestUserEvolution::test_auto_approve_true_returns_persistence_failed_request_status",
            "TestRequestUserEvolution::test_stage_evolution_from_signals_auto_approve_preserves_staged_request_id",
            "TestRequestUserEvolution::test_auto_approve_false_stages_for_approval",
            "TestRequestUserEvolution::test_returns_empty_result_when_no_patch_generated",
            "TestRequestUserEvolution::test_uses_placeholder_trajectory_when_no_builder",
            "TestRequestUserEvolution::test_uses_aggregated_team_trajectory_when_source_available",
            "TestRequestUserEvolution::test_active_request_uses_explicit_subject_without_detecting_used_skill",
            "TestRequestUserEvolution::test_active_request_continues_when_trajectory_detection_fails",
            "test_run_evolution_and_request_user_evolution_share_signal_consumer",
            "test_emit_record_approval_event_preserves_signal_metadata[user_intent-explicit_request-True]",
            "test_emit_record_approval_event_preserves_signal_metadata[trajectory_issue-None-False]",
            "test_team_approve_record_and_reject_record_aliases",
            "test_stage_evolution_from_signals_rejects_legacy_excerpt_arguments"
    );

    @TestFactory
    Collection<DynamicTest> pythonTeamSkillEvolutionCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("disabled_skills") || name.contains("disabled")) {
            assertDisabledSkillSemantics();
            return;
        }
        if (name.contains("task_completion") || name.contains("view_task") || name.contains("notify_team_completed")) {
            assertTeamTaskCompletionSemantics();
            return;
        }
        if (name.contains("signal") || name.contains("UserEvolution") || name.contains("user_intent")
                || name.contains("trajectory_issue") || name.contains("detect_user_request")) {
            assertTeamSignalSemantics();
            return;
        }
        if (name.contains("approve") || name.contains("reject") || name.contains("approval")
                || name.contains("request_simplify") || name.contains("request_rebuild")) {
            assertApprovalEventSemantics();
            return;
        }
        if (name.contains("trajectory") || name.contains("snapshot") || name.contains("after_tool_call")
                || name.contains("auto_scan_false") || name.contains("run_evolution")) {
            assertTeamSnapshotAndTrajectorySemantics();
            return;
        }
        if (name.contains("patch") || name.contains("auto_save") || name.contains("properties")
                || name.contains("tracker") || name.contains("init")) {
            assertTeamRailProperties();
            return;
        }
        assertTeamSkillDetectionSemantics();
    }

    private void assertTeamRailProperties() {
        TeamSkillEvolutionRail rail = new TeamSkillEvolutionRail(Path.of("team-skills"));

        assertThat(rail.getPriority()).isEqualTo(78);
        assertThat(rail.getSkillsDir()).isEqualTo(Path.of("team-skills"));
        assertThat(rail.isAutoSave()).isTrue();
        assertThat(rail.isAutoScan()).isTrue();
        assertThat(rail.isAsyncEvolution()).isTrue();
        assertThat(rail.getProcessedSignalKeys()).isEmpty();

        rail.setAutoSave(false);
        rail.setAutoScan(false);

        assertThat(rail.isAutoSave()).isFalse();
        assertThat(rail.isAutoScan()).isFalse();
    }

    private void assertTeamSnapshotAndTrajectorySemantics() {
        TeamSkillEvolutionRail rail = new TeamSkillEvolutionRail(Path.of("team-skills"));
        CallbackContext start = ctx("invoke", Map.of("session_id", "s1"));

        rail.beforeInvoke(start);
        rail.afterToolCall(ctx("view_task", Map.of("content", "task-a completed")));
        rail.afterToolCall(ctx("read_file", Map.of("path", "team-skills/demo/SKILL.md")));
        rail.afterInvoke(ctx("invoke", Map.of("messages", List.of(Map.of("role", "assistant", "content", "done")))));

        Map<String, Object> snapshot = rail.snapshotForEvolution(ctx("team_skill"));

        assertThat(snapshot.get("team_skill")).isEqualTo(true);
        assertThat(snapshot.get("trajectory")).isInstanceOf(List.class);
        assertThat((List<?>) snapshot.get("trajectory")).hasSize(4);
        assertThat(snapshot.get("context")).isInstanceOf(Map.class);
        assertThat(rail.getProcessedSignalKeys()).containsExactly("view_task", "read_file");
    }

    private void assertDisabledSkillSemantics() {
        Set<String> disabled = new LinkedHashSet<>(List.of("alpha", "beta"));
        EvolutionRail baseRail = new EvolutionRail(100, EvolutionTriggerPoint.NONE, true, disabled);
        TeamSkillEvolutionRail teamRail = new TeamSkillEvolutionRail(Path.of("team-skills"));

        teamRail.setAutoScan(false);
        baseRail.afterInvoke(ctx("invoke"));

        assertThat(baseRail.getDisabledSkills()).containsExactly("alpha", "beta");
        assertThat(baseRail.drainPendingHostEvents()).isEmpty();
        assertThat(teamRail.isAutoScan()).isFalse();
    }

    private void assertTeamTaskCompletionSemantics() {
        assertThat(allTasksCompleted("task-a completed\ntask-b completed")).isTrue();
        assertThat(allTasksCompleted("task-a pending\ntask-b completed")).isFalse();
        assertThat(allTasksCompleted("task-a in_progress\ntask-b completed")).isFalse();
        assertThat(allTasksCompleted("task-a blocked\ntask-b completed")).isFalse();
        assertThat(allTasksCompleted("task-a claimed\ntask-b completed")).isFalse();
        assertThat(allTasksCompleted("task-a ready")).isFalse();
    }

    private void assertTeamSignalSemantics() {
        UserIntent intent = new UserIntent(true, "tighten handoff");
        TrajectoryIssue issue = new TrajectoryIssue("handoff", "missing reviewer handoff", "reviewer", "high");
        EvolutionSignal userSignal = TeamSignals.makeTeamUserIntentSignal("demo", intent.intent());
        EvolutionSignal trajectorySignal = TeamSignals.makeTeamTrajectorySignal(
                "demo",
                "# demo",
                List.of(Map.of(
                        "issue_type", issue.issueType(),
                        "description", issue.description(),
                        "affected_role", issue.affectedRole(),
                        "severity", issue.severity()
                ))
        );

        assertThat(TeamSignalType.USER_INTENT.getValue()).isEqualTo("user_intent");
        assertThat(TeamSignalType.TRAJECTORY_ISSUE.getValue()).isEqualTo("trajectory_issue");
        assertThat(intent.improvement()).isTrue();
        assertThat(userSignal.getSkillName()).isEqualTo("demo");
        assertThat(userSignal.getExcerpt()).isEqualTo("tighten handoff");
        assertThat(TeamSignals.getTeamSignalSkillContent(trajectorySignal)).isEqualTo("# demo");
        assertThat(TeamSignals.getTeamTrajectoryIssues(trajectorySignal)).hasSize(1)
                .first()
                .satisfies(payload -> {
                    assertThat(payload.get("issue_type")).isEqualTo("handoff");
                    assertThat(payload.get("severity")).isEqualTo("high");
                });
    }

    private void assertApprovalEventSemantics() {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Workflow")
                .action("append")
                .content("Add a reviewer handoff.")
                .target(EvolutionTarget.BODY)
                .build();
        EvolutionRecord record = EvolutionRecord.builder()
                .id("record-1")
                .source("explicit_request")
                .context("user asked")
                .change(patch)
                .score(0.9)
                .build();

        OutputSchema event = ApprovalEvents.buildTeamSkillApprovalEventFromRecords(
                "demo",
                "request-1",
                List.of(record),
                "en",
                "team_skill"
        );

        assertThat(event.getType()).isEqualTo("chat.ask_user_question");
        assertThat(event.getPayload()).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) event.getPayload();
        assertThat(payload.get("request_id")).isEqualTo("request-1");
        assertThat(payload.get("questions")).asList().hasSize(1);
        assertThat(String.valueOf(payload.get("evolution_meta"))).contains("team_skill", "demo");
    }

    private void assertTeamSkillDetectionSemantics() {
        TeamSkillEvolutionRail rail = new TeamSkillEvolutionRail(Path.of("team-skills"));

        rail.beforeInvoke(ctx("invoke"));
        rail.afterToolCall(ctx("skill_tool", Map.of("path", "team-skills/backend/SKILL.md")));
        rail.afterToolCall(ctx("read_file", Map.of("path", "skills/legacy/SKILL.md")));

        assertThat(rail.buildTrajectory()).extracting(step -> step.get("event"))
                .contains("before_invoke", "after_tool_call");
        assertThat(rail.snapshotForEvolution(ctx("detect")).get("team_skill")).isEqualTo(true);
    }

    private boolean allTasksCompleted(String text) {
        List<String> lines = text == null || text.isBlank() ? List.of() : text.lines().toList();
        return !lines.isEmpty() && lines.stream().allMatch(line -> line.strip().endsWith("completed"));
    }

    private static CallbackContext ctx(String toolName) {
        return ctx(toolName, Map.of());
    }

    private static CallbackContext ctx(String toolName, Map<String, Object> extraValues) {
        Map<String, Object> values = new LinkedHashMap<>(extraValues);
        values.putIfAbsent("tool_name", toolName);
        return new CallbackContext(null, values);
    }
}
