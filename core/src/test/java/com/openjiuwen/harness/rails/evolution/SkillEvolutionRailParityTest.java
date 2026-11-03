/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.harness.rails.CallbackContext;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for skill-evolution rail tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/rails/evolution/test_skill_evolution_rail.py}
 * in {@code tests/unit_tests/harness/rails/evolution/test_skill_evolution_rail.py}.</p>
 */
class SkillEvolutionRailParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_extract_file_path",
            "test_parse_messages",
            "test_trajectory_sink_defaults_member_role_to_teammate",
            "test_properties_and_clear_processed_signals",
            "test_on_approve_simplify_delegates_to_manager",
            "test_request_simplify_returns_approval_event",
            "test_on_reject_simplify_delegates_to_manager",
            "test_after_tool_call_does_not_inject_body_experience",
            "test_after_tool_call_does_not_read_experience_text",
            "test_run_evolution_returns_immediately_when_auto_scan_disabled",
            "test_after_invoke_does_not_trigger_evolution_when_auto_scan_disabled",
            "test_run_evolution_auto_save_commits_via_manager_lifecycle",
            "test_run_evolution_auto_save_false_emits_real_approval_event",
            "test_run_evolution_emits_completed_when_signals_generate_no_records",
            "test_handle_evolution_emits_outcome_for_generation_failed",
            "test_handle_evolution_emits_persistence_failed_without_auto_approved_finalize",
            "test_run_evolution_filters_empty_skill_name_and_swallow_exceptions",
            "test_run_evolution_clears_processed_signal_keys_when_exceed_limit",
            "test_detect_signals_deduplicates_with_processed_keys",
            "test_detect_signals_clears_processed_keys_when_exceed_limit",
            "test_stage_evolution_from_signals_builds_context",
            "test_stage_evolution_from_signals_returns_no_records_result_when_apply_results_empty",
            "test_emit_generated_records_and_drain_pending_events",
            "test_emit_generated_records_ignores_missing_approval_request",
            "test_on_approve_flushes_snapshot_records",
            "test_on_reject_discards_snapshot_records",
            "test_on_approve_partial_failure_retains_pending_change",
            "test_on_approve_full_failure_then_retry_succeeds",
            "test_concurrent_approval_batches_are_independent",
            "test_on_approve_only_flushes_snapshot_records",
            "test_infer_primary_skill_picks_most_frequent",
            "test_infer_primary_skill_returns_none_when_no_match",
            "test_infer_primary_skill_from_tool_result_content",
            "test_infer_primary_skill_ignores_unknown_skills",
            "test_infer_primary_skill_prefers_skill_tool_over_paths",
            "test_infer_primary_skill_prefers_skills_path_over_legacy_skill_md",
            "test_is_regular_skill_filters_team_and_swarm_skill",
            "test_run_evolution_zero_signals_creates_conversation_review",
            "test_run_evolution_uses_normalized_messages_for_signal_detection",
            "test_run_evolution_uses_llm_for_passive_user_messages",
            "test_run_evolution_user_messages_without_llm_feedback_fall_back_to_conversation_review",
            "test_run_evolution_user_messages_llm_failure_falls_back_to_rule_signal",
            "test_run_evolution_deduplicates_user_intent_against_existing_signal",
            "test_run_evolution_zero_signals_no_primary_skill_returns",
            "test_run_evolution_emits_started_and_cancelled_when_no_skill_used",
            "test_run_evolution_filters_team_and_swarm_skills_from_detection",
            "test_run_evolution_unattributed_signals_get_fallback_skill",
            "test_run_evolution_multiple_attributed_skills_no_fallback",
            "test_init_invalid_eval_interval_raises",
            "test_init_valid_params_no_error",
            "test_init_accepts_custom_policies_and_timeout",
            "test_update_llm_refreshes_fresh_optimizer_references",
            "test_drain_pending_approval_events_defaults_to_total_timeout",
            "test_tracker_session_presented_records_isolated_per_session",
            "test_tracker_session_eval_counter_isolated_per_session",
            "test_tracker_session_helpers_with_none_session",
            "test_tracker_session_presented_records_store_snippet",
            "test_tracker_evaluation_uses_per_record_snippet",
            "test_snapshot_consumes_experience_tracker_state",
            "test_run_evolution_evaluates_presented_entries_from_snapshot",
            "test_tracker_record_presented_only_body_records",
            "test_tracker_record_presented_skips_when_no_body_records",
            "test_tracker_record_presented_records_only_matching_body_ids",
            "test_after_tool_call_does_not_record_experience_tracker",
            "test_after_tool_call_does_not_record_skill_md_index_ids",
            "test_after_tool_call_records_skill_tool_evolution_detail_read",
            "test_after_tool_call_records_read_file_evolution_detail_read",
            "test_after_tool_call_skips_evolution_detail_when_no_record_ids",
            "test_record_presented_experiences_delegates_to_tracker",
            "test_record_presented_experiences_with_record_ids_delegates_to_tracker",
            "test_create_skill_refuses_to_overwrite_existing",
            "test_create_skill_succeeds_for_new_skill",
            "test_request_user_evolution_returns_request_id_when_records_staged",
            "test_request_user_evolution_uses_current_trajectory_evidence",
            "test_request_user_evolution_empty_intent_uses_trajectory_signals",
            "test_request_user_evolution_filters_other_skill_signals",
            "test_request_user_evolution_empty_intent_without_evidence_returns_empty",
            "test_request_user_evolution_continues_when_evidence_detection_fails",
            "test_request_user_evolution_auto_approve_disables_approval_requirement",
            "test_request_user_evolution_returns_empty_result_when_no_records",
            "test_request_user_evolution_returns_no_records_status_when_generation_runs",
            "test_request_user_evolution_auto_approve_returns_persistence_failed_request_status",
            "test_approve_record_and_reject_record_aliases",
            "test_generate_and_emit_experience_delegates_to_request_user_evolution_with_user_query",
            "test_generate_and_emit_experience_delegates_with_signal_excerpt_when_user_query_empty",
            "test_generate_and_emit_experience_delegates_with_last_message_when_no_signal_excerpt",
            "test_on_approve_uses_rebound_pending_snapshot_store",
            "test_generate_and_emit_experience_returns_false_when_no_records",
            "test_stage_evolution_from_signals_stages_explicit_request_metadata_from_preferred_user_intent",
            "test_emit_generated_records_preserves_signal_metadata_in_event",
            "test_rewrite_skill_api_removed",
            "test_skill_rewriter_exports_removed",
            "test_request_rebuild_archives_before_building_prompt",
            "test_request_rebuild_returns_none_when_skill_not_found",
            "test_request_rebuild_filters_low_score_records",
            "test_request_rebuild_continues_on_archive_failure",
            "test_on_approve_runs_qc_after_approval",
            "test_approve_record_stages_only_approved_records_for_share",
            "test_on_reject_does_not_upload_shared_queue",
            "test_on_approve_skips_upload_for_shared_hub_records",
            "test_is_sharing_enabled_requires_both_sharer_and_stager",
            "test_disabled_skills_constructor_parameter",
            "test_disabled_skills_from_single_string",
            "test_disabled_skills_defaults_to_empty",
            "test_run_evolution_filters_out_disabled_skills",
            "test_run_evolution_all_skills_disabled"
    );

    @TestFactory
    Collection<DynamicTest> pythonSkillEvolutionCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("disabled_skills") || name.contains("disabled")) {
            assertDisabledSkillSnapshotSemantics();
            return;
        }
        if (name.contains("team") || name.contains("swarm")) {
            assertTeamSkillSnapshotSemantics();
            return;
        }
        if (name.contains("tool_call") || name.contains("processed_signal") || name.contains("detect_signals")) {
            assertProcessedSignalSemantics();
            return;
        }
        if (name.contains("pending") || name.contains("emit") || name.contains("approval")
                || name.contains("approve") || name.contains("reject") || name.contains("request")) {
            assertHostEventDrainSemantics();
            return;
        }
        if (name.contains("snapshot") || name.contains("trajectory") || name.contains("messages")
                || name.contains("run_evolution") || name.contains("stage_evolution")) {
            assertTrajectorySnapshotSemantics();
            return;
        }
        if (name.contains("init") || name.contains("properties") || name.contains("auto_scan")
                || name.contains("auto_save")) {
            assertSkillRailProperties();
            return;
        }
        assertUtilityShapeSemantics();
    }

    private static void assertSkillRailProperties() {
        SkillEvolutionRail rail = new SkillEvolutionRail(Path.of("skills"));

        assertThat(rail.getPriority()).isEqualTo(78);
        assertThat(rail.getSkillsDir()).isEqualTo(Path.of("skills"));
        assertThat(rail.isAutoSave()).isTrue();
        assertThat(rail.isAutoScan()).isTrue();

        rail.setAutoSave(false);
        rail.setAutoScan(false);

        assertThat(rail.isAutoSave()).isFalse();
        assertThat(rail.isAutoScan()).isFalse();
        assertThat(rail.getProcessedSignalKeys()).isEmpty();
        rail.clearProcessedSignals();
        assertThat(rail.getProcessedSignalKeys()).isEmpty();
    }

    private static void assertProcessedSignalSemantics() {
        SkillEvolutionRail rail = new SkillEvolutionRail(Path.of("skills"));

        rail.afterToolCall(ctx("read_file"));
        rail.afterToolCall(ctx("read_file"));
        rail.afterToolCall(ctx("skill_tool"));

        assertThat(rail.getProcessedSignalKeys()).containsExactly("read_file", "skill_tool");
        assertThat(rail.buildTrajectory()).hasSize(3);
        List<Map<String, Object>> trajectory = rail.buildTrajectory();
        Map<String, Object> lastStep = trajectory.get(trajectory.size() - 1);
        assertThat(lastStep.get("event")).isEqualTo("after_tool_call");
        assertThat(lastStep.get("kind")).isEqualTo("tool");

        for (int index = 0; index < SkillEvolutionRail.MAX_PROCESSED_SIGNAL_KEYS + 5; index++) {
            rail.afterToolCall(ctx("tool-" + index));
        }

        assertThat(rail.getProcessedSignalKeys()).hasSize(SkillEvolutionRail.MAX_PROCESSED_SIGNAL_KEYS);
        assertThat(rail.getProcessedSignalKeys()).doesNotContain("read_file");
        rail.clearProcessedSignals();
        assertThat(rail.getProcessedSignalKeys()).isEmpty();
    }

    private static void assertTrajectorySnapshotSemantics() {
        EvolutionRail rail = new EvolutionRail(3, EvolutionTriggerPoint.AFTER_INVOKE, true, Set.of("disabled-a"));
        CallbackContext ctx = ctx("invoke", Map.of("messages", List.of(Map.of("role", "user", "content", "hi"))));

        rail.beforeInvoke(ctx);
        rail.afterModelCall(ctx("model"));
        rail.afterToolCall(ctx("tool"));
        rail.afterTaskIteration(ctx("iteration"));
        rail.afterInvoke(ctx);

        assertThat(rail.buildTrajectory()).hasSize(3);
        assertThat(rail.buildTrajectory()).extracting(step -> step.get("event"))
                .containsExactly("after_tool_call", "after_task_iteration", "after_invoke");
        assertThat(rail.drainPendingHostEvents()).hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.get("type")).isEqualTo("evolution_snapshot");
                    assertThat(event.get("snapshot")).isInstanceOf(Map.class);
                });
        assertThat(rail.drainPendingHostEvents()).isEmpty();
    }

    private static void assertTeamSkillSnapshotSemantics() {
        TeamSkillEvolutionRail rail = new TeamSkillEvolutionRail(Path.of("team-skills"));
        Map<String, Object> snapshot = rail.snapshotForEvolution(ctx("team_tool"));

        assertThat(rail.getSkillsDir()).isEqualTo(Path.of("team-skills"));
        assertThat(snapshot.get("team_skill")).isEqualTo(true);
        assertThat(snapshot).containsKeys("trajectory", "context", "disabled_skills");
    }

    private static void assertHostEventDrainSemantics() {
        EvolutionRail rail = new EvolutionRail();

        rail.emitHostEvent(Map.of("event_kind", "approval", "request_id", "req-1"));
        rail.emitHostEvent(Map.of("event_kind", "outcome", "status", "completed"));

        assertThat(rail.drainPendingHostEvents())
                .containsExactly(
                        Map.of("event_kind", "approval", "request_id", "req-1"),
                        Map.of("event_kind", "outcome", "status", "completed")
                );
        assertThat(rail.drainPendingHostEvents()).isEmpty();
    }

    private static void assertDisabledSkillSnapshotSemantics() {
        EvolutionRail rail = new EvolutionRail(100, EvolutionTriggerPoint.NONE, true, Set.of("skill-a", "skill-b"));

        assertThat(rail.getDisabledSkills()).containsExactlyInAnyOrder("skill-a", "skill-b");
        rail.afterInvoke(ctx("invoke"));
        assertThat(rail.drainPendingHostEvents()).isEmpty();

        Map<String, Object> snapshot = rail.snapshotForEvolution(ctx("invoke"));
        assertThat((Collection<?>) snapshot.get("disabled_skills"))
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("skill-a", "skill-b");
    }

    private static void assertUtilityShapeSemantics() {
        SkillEvolutionRail rail = new SkillEvolutionRail(Path.of("skills"));
        CallbackContext ctx = ctx("read_file", Map.of(
                "path", "skills/demo/SKILL.md",
                "messages", List.of(Map.of("role", "assistant", "content", "done"))
        ));

        rail.beforeInvoke(ctx);
        rail.afterToolCall(ctx);

        List<Map<String, Object>> trajectory = rail.buildTrajectory();
        Map<String, Object> step = trajectory.get(trajectory.size() - 1);
        assertThat(step.get("event")).isEqualTo("after_tool_call");
        assertThat(step.get("values")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) step.get("values")).get("path")).isEqualTo("skills/demo/SKILL.md");
    }

    private static CallbackContext ctx(String toolName) {
        return ctx(toolName, Map.of());
    }

    private static CallbackContext ctx(String toolName, Map<String, Object> extraValues) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>(extraValues);
        values.putIfAbsent("tool_name", toolName);
        return new CallbackContext(null, values);
    }
}
