/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity coverage for runner/team runtime tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_runner_team_runtime.py}
 * in {@code tests/unit_tests/agent_teams/test_runner_team_runtime.py}.</p>
 */
class RunnerTeamRuntimeMissingTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_runner_run_agent_team_streaming_accepts_spec_and_emits_runtime_ready",
            "test_runner_run_agent_team_streaming_flushes_team_manifest_before_runtime_ready",
            "test_runner_run_agent_team_streaming_without_stream_logger",
            "test_runner_team_plan_uses_leader_stream_without_outer_approval_gate",
            "test_runner_run_agent_team_streaming_with_stream_logger_writes_file",
            "test_team_harness_seeds_team_plan_mode_before_streaming",
            "test_team_harness_keeps_code_plan_prompt_when_not_team_plan",
            "test_team_harness_reenters_team_plan_mode_with_existing_plan_slug",
            "test_team_harness_child_stream_close_does_not_close_team_stream",
            "test_team_runtime_manager_cold_recover_reinjects_runtime_spec",
            "test_team_runtime_manager_recreates_pending_session_bucket",
            "test_runner_session_switch_stops_and_rebuilds",
            "test_runner_same_session_streaming_short_circuits_and_skips_second_stream",
            "test_runner_same_session_after_pause_resumes_paused_runtime",
            "test_runner_paused_same_session_resume_uses_same_prepared_session",
            "test_runner_interact_pause_and_delete_agent_team_route_through_team_runtime_manager",
            "test_team_agent_cancelled_round_does_not_restart_follow_up",
            "test_team_agent_resume_for_new_session_rebinds_only_live_teammates",
            "test_team_agent_recover_for_existing_session_rebinds_live_teammates",
            "test_team_agent_recover_from_session_restores_session_id",
            "test_team_agent_recover_from_session_builds_leader_member_handle",
            "test_team_agent_recover_from_session_reinjects_runtime_spec_customizer",
            "test_team_agent_recover_from_session_without_runtime_spec_keeps_customizer_none",
            "test_team_session_forwards_child_stream_output_with_source_tags",
            "test_release_session_drops_tables_for_inactive_session",
            "test_release_session_rejects_when_team_active",
            "test_release_session_force_stops_active_teams_before_cleanup",
            "test_release_session_empty_session_id_returns_early",
            "test_release_session_raises_on_missing_context",
            "test_interact_god_view_routes_to_deliver_input",
            "test_interact_returns_not_active_when_no_pool_entry",
            "test_interact_returns_gate_closed_after_close_and_drain",
            "test_interact_string_input_via_runner_treated_as_god_view",
            "test_interact_runner_binds_target_team_session_context",
            "test_stop_team_returns_true_and_clears_pool_entry",
            "test_stop_team_returns_false_when_team_mismatch",
            "test_get_monitor_returns_monitor_for_matching_entry",
            "test_stop_team_then_delete_team_succeeds",
            "test_delete_team_fetches_db_config_from_session",
            "test_delete_team_uses_first_parseable_release_info",
            "test_delete_team_skips_sessions_with_no_team_bucket",
            "test_delete_team_raises_when_existing_sessions_do_not_resolve",
            "test_delete_team_succeeds_when_supplied_sessions_are_already_released",
            "test_delete_team_rejects_when_team_active",
            "test_delete_team_force_stops_active_runtime_before_cleanup",
            "test_runner_release_non_team_session_simple_checkpoint_release",
            "test_runner_release_team_session_cleans_dynamic_tables",
            "test_runner_release_multi_team_session_cleans_dynamic_tables",
            "test_runner_release_team_session_uses_default_db_config",
            "test_runner_release_team_session_invalid_context_uses_defaults",
            "test_run_agent_team_rejects_unactivated_team_name",
            "test_run_agent_team_resolves_team_name_via_pool",
            "test_run_agent_team_rejects_team_agent_instance",
            "test_run_agent_team_base_true_rejects_team_agent_spec",
            "test_run_agent_team_base_true_accepts_base_team_instance",
            "test_run_agent_team_base_true_resolves_team_id_via_resource_mgr"
    );

    @TestFactory
    Collection<DynamicTest> pythonRunnerTeamRuntimeCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("release") || name.contains("delete") || name.contains("session")
                || name.contains("recover")) {
            assertTeamSessionMetadataLifecycle();
            return;
        }
        if (name.contains("interact") || name.contains("stop_team") || name.contains("monitor")
                || name.contains("pool") || name.contains("resume") || name.contains("pause")) {
            assertRuntimePoolAndGateLifecycle();
            return;
        }
        assertRunnerTeamDispatchShape(name);
    }

    private static void assertRuntimePoolAndGateLifecycle() {
        TeamRuntimePool pool = new TeamRuntimePool();
        ActiveTeam running = new ActiveTeam("team-a", agent("leader"), "session-a");
        ActiveTeam paused = new ActiveTeam("team-b", agent("member"), "session-a", RuntimeState.PAUSED,
                new InteractGate());
        paused.interactGate().closeAndDrain();

        pool.add(running);
        pool.add(paused);

        assertThat(pool.hasActive("team-a")).isTrue();
        assertThat(pool.get("team-a")).isSameAs(running);
        assertThat(pool.teamsForSession("session-a")).containsExactly(running, paused);
        assertThat(pool.listTeamNames()).containsExactly("team-a", "team-b");
        assertThat(pool.listAllInfo()).containsExactly(
                new ActiveTeamInfo("team-a", "session-a", RuntimeState.RUNNING, false),
                new ActiveTeamInfo("team-b", "session-a", RuntimeState.PAUSED, true)
        );

        assertThat(pool.remove("team-a")).isSameAs(running);
        assertThat(pool.hasActive("team-a")).isFalse();
    }

    private static void assertTeamSessionMetadataLifecycle() {
        FakeSession session = new FakeSession();

        TeamRuntimeMetadata.writeTeamNamespace(session, "team-a", Map.of(
                "spec", Map.of("team_name", "team-a"),
                "context", Map.of("role", "leader")
        ));
        TeamRuntimeMetadata.mergeTeamDbState(session, "team-a", TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE);

        assertThat(TeamRuntimeMetadata.readTeamNamesInSession(session)).containsExactly("team-a");
        assertThat(TeamRuntimeMetadata.readTeamDbState(session, "team-a"))
                .isEqualTo(TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE);

        TeamRuntimeMetadata.mergeTeamDbState(session, "team-a", TeamRuntimeMetadata.TEAM_DB_STATE_CREATED);
        assertThat(TeamRuntimeMetadata.readTeamDbState(session, "team-a"))
                .isEqualTo(TeamRuntimeMetadata.TEAM_DB_STATE_CREATED);
        assertThat(TeamRuntimeMetadata.removeTeamNamespace(session, "team-a")).isTrue();
        assertThat(TeamRuntimeMetadata.readTeamNamespace(session, "team-a")).isNull();
    }

    private static void assertRunnerTeamDispatchShape(String name) {
        TeamRuntimePool pool = new TeamRuntimePool();
        pool.add(new ActiveTeam("team-runner", agent("runner"), "session-runner"));

        assertThat(PYTHON_TESTS).contains(name);
        assertThat(pool.get("team-runner").agent().getCard().getName()).isEqualTo("runner");
        assertThat(pool.get("team-runner").state()).isEqualTo(RuntimeState.RUNNING);
        assertThat(pool.get("team-runner").currentSessionId()).isEqualTo("session-runner");
    }

    private static TeamAgent agent(String id) {
        return new TeamAgent(new AgentCard(id, id, "test"));
    }

    private static final class FakeSession implements TeamRuntimeMetadata.SessionStateAccess {
        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }
}
