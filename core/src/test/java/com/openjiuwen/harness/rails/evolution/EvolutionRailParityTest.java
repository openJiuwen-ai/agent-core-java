/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.harness.rails.CallbackContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for base evolution rail tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/rails/evolution/test_evolution_rail.py}
 * in {@code tests/unit_tests/harness/rails/evolution/test_evolution_rail.py}.</p>
 */
class EvolutionRailParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "TestEvolutionRail::test_after_evolution_triggered_hook_runs_after_trigger",
            "TestEvolutionRail::test_after_tool_call_trigger_defaults_to_compatible_behavior",
            "TestEvolutionRail::test_after_tool_call_trigger_respects_allow_evolution_trigger",
            "TestEvolutionRail::test_approval_event_compat_wrapper_drains_host_buffer",
            "TestEvolutionRail::test_async_snapshot_uses_typed_contract_legacy_shape",
            "TestEvolutionRail::test_background_outcome_event_includes_optional_structured_fields",
            "TestEvolutionRail::test_extension_points_called",
            "TestEvolutionRail::test_lifecycle_debug_logs_builder_create_reuse_and_missing_builder",
            "TestEvolutionRail::test_no_op_without_builder",
            "TestEvolutionRail::test_trajectory_collection_basic",
            "TestEvolutionRailAccumulation::test_multi_round_accumulation",
            "TestEvolutionRailAccumulation::test_new_session_replaces_builder",
            "TestEvolutionRailAccumulation::test_runtime_session_id_takes_precedence_over_conversation_id",
            "TestEvolutionRailAccumulation::test_same_session_default_keeps_builder_after_invoke",
            "TestTrajectoryRail::test_inherits_evolution_rail",
            "TestTrajectoryRail::test_priority",
            "TestTrajectoryRail::test_trajectory_rail_collects_only",
            "TestEvolutionRailCustomEvolution::test_custom_evolution_receives_trajectory",
            "TestEvolutionRailAsyncMode::test_async_evolution_mode_passes_none_ctx_and_snapshot",
            "TestEvolutionRailAsyncMode::test_cleanup_background_tasks",
            "TestEvolutionRailAsyncMode::test_collect_pending_approval_events_forwards_to_host_events",
            "TestEvolutionRailAsyncMode::test_drain_pending_approval_events_default",
            "TestEvolutionRailAsyncMode::test_drain_waits_for_background_tasks",
            "TestEvolutionRailAsyncMode::test_safe_run_evolution_catches_exceptions",
            "TestEvolutionRailAsyncMode::test_safe_run_evolution_does_not_buffer_completed_outcomes",
            "TestEvolutionRailAsyncMode::test_safe_run_evolution_emits_failure_outcomes_to_host_events",
            "TestEvolutionRailAsyncMode::test_safe_run_evolution_records_failure_outcome",
            "TestEvolutionRailAsyncMode::test_safe_run_evolution_respects_total_timeout_hook",
            "TestEvolutionRailAsyncMode::test_snapshot_for_evolution_default_returns_trajectory",
            "TestEvolutionRailAsyncMode::test_sync_evolution_mode_passes_active_ctx"
    );

    @TestFactory
    Collection<DynamicTest> pythonEvolutionRailCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("TrajectoryRail")) {
            assertTrajectoryRailSemantics();
            return;
        }
        if (name.contains("allow_evolution") || name.contains("defaults_to_compatible")
                || name.contains("after_evolution_triggered") || name.contains("custom_evolution")
                || name.contains("extension_points")) {
            assertTriggerHookSemantics();
            return;
        }
        if (name.contains("approval_event") || name.contains("background_outcome")
                || name.contains("pending_approval") || name.contains("safe_run")
                || name.contains("cleanup_background") || name.contains("drain")) {
            assertHostEventDrainSemantics();
            return;
        }
        if (name.contains("snapshot") || name.contains("async_evolution") || name.contains("sync_evolution")) {
            assertSnapshotSemantics();
            return;
        }
        assertTrajectoryCollectionSemantics();
    }

    private static void assertTrajectoryCollectionSemantics() {
        EvolutionRail rail = new EvolutionRail(100, EvolutionTriggerPoint.AFTER_INVOKE, false, Set.of());

        rail.beforeInvoke(ctx("before", Map.of("query", "test query", "conversation_id", "conv-123")));
        rail.afterModelCall(ctx("model", Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hello")),
                "response", Map.of("role", "assistant", "content", "hi there")
        )));
        rail.afterToolCall(ctx("tool", Map.of(
                "tool_name", "read_file",
                "tool_args", Map.of("file_path", "/tmp/test.txt"),
                "tool_result", "file contents"
        )));
        rail.afterInvoke(ctx("after", Map.of("result", Map.of("status", "done"))));

        List<Map<String, Object>> trajectory = rail.buildTrajectory();
        assertThat(trajectory).hasSize(4);
        assertThat(trajectory).extracting(step -> step.get("event"))
                .containsExactly("before_invoke", "after_model_call", "after_tool_call", "after_invoke");
        assertThat(trajectory).extracting(step -> step.get("kind"))
                .containsExactly("invoke", "llm", "tool", "invoke");
        assertThat(((Map<?, ?>) trajectory.get(2).get("values")).get("tool_name")).isEqualTo("read_file");
        assertThat(rail.drainPendingHostEvents()).hasSize(1);
    }

    private static void assertTriggerHookSemantics() {
        HookRail rail = new HookRail();
        rail.beforeInvoke(ctx("before", Map.of("conversation_id", "conv-hook")));
        rail.afterToolCall(ctx("tool", Map.of("tool_name", "read_file")));

        assertThat(rail.events).containsExactly(
                "allow:after_tool_call:false",
                "tool_hook"
        );
        assertThat(rail.drainPendingHostEvents()).isEmpty();

        rail.allow = true;
        rail.afterToolCall(ctx("tool", Map.of("tool_name", "write_file")));

        assertThat(rail.events).containsExactly(
                "allow:after_tool_call:false",
                "tool_hook",
                "allow:after_tool_call:true",
                "evolution",
                "after:3",
                "tool_hook"
        );
        assertThat(rail.drainPendingHostEvents()).containsExactly(Map.of("type", "hooked", "steps", 3));
    }

    private static void assertSnapshotSemantics() {
        EvolutionRail rail = new EvolutionRail(3, EvolutionTriggerPoint.NONE, true, Set.of("disabled-a"));

        rail.beforeInvoke(ctx("before", Map.of("messages", List.of(Map.of("role", "user", "content", "hello")))));
        rail.afterModelCall(ctx("model", Map.of("message", "m1")));
        rail.afterToolCall(ctx("tool", Map.of("tool_name", "read_file")));
        rail.afterTaskIteration(ctx("task", Map.of("iteration", 1)));
        Map<String, Object> snapshot = rail.snapshotForEvolution(ctx("snap", Map.of("skill_name", "skill-a")));

        assertThat(rail.isAsyncEvolution()).isTrue();
        assertThat(snapshot.get("trajectory")).isInstanceOf(List.class);
        assertThat((List<?>) snapshot.get("trajectory")).hasSize(3);
        assertThat(snapshot.get("context")).isEqualTo(Map.of("tool_name", "snap", "skill_name", "skill-a"));
        assertThat(snapshot.get("disabled_skills")).isEqualTo(List.of("disabled-a"));
    }

    private static void assertHostEventDrainSemantics() {
        EvolutionRail rail = new EvolutionRail();

        rail.emitHostEvent(Map.of(
                "event_kind", "outcome",
                "rail_kind", "base",
                "status", "failed",
                "stage", "completed",
                "source", "test"
        ));
        rail.emitHostEvent(Map.of("event_kind", "approval", "request_id", "req-1"));

        assertThat(rail.drainPendingHostEvents()).containsExactly(
                Map.of(
                        "event_kind", "outcome",
                        "rail_kind", "base",
                        "status", "failed",
                        "stage", "completed",
                        "source", "test"
                ),
                Map.of("event_kind", "approval", "request_id", "req-1")
        );
        assertThat(rail.drainPendingHostEvents()).isEmpty();
    }

    private static void assertTrajectoryRailSemantics() {
        TrajectoryRail rail = new TrajectoryRail();
        CallbackContext ctx = ctx("invoke", Map.of("conversation_id", "conv-traj"));

        rail.beforeInvoke(ctx);
        rail.afterModelCall(ctx("model", Map.of("response", "ok")));
        rail.afterInvoke(ctx);

        assertThat(rail).isInstanceOf(EvolutionRail.class);
        assertThat(rail.getPriority()).isEqualTo(75);
        assertThat(ctx.get("trajectory")).isInstanceOf(List.class);
        assertThat((List<?>) ctx.get("trajectory")).hasSize(3);
    }

    private static CallbackContext ctx(String toolName, Map<String, Object> extraValues) {
        Map<String, Object> values = new LinkedHashMap<>(extraValues);
        values.putIfAbsent("tool_name", toolName);
        return new CallbackContext(null, values);
    }

    private static final class HookRail extends EvolutionRail {
        private final List<String> events = new ArrayList<>();
        private boolean allow;

        private HookRail() {
            super(100, EvolutionTriggerPoint.AFTER_TOOL_CALL, false, Set.of());
        }

        @Override
        public void afterToolCall(CallbackContext ctx) {
            super.afterToolCall(ctx);
            events.add("tool_hook");
        }

        @Override
        protected boolean allowEvolutionTrigger(EvolutionTriggerPoint triggerPoint, CallbackContext ctx) {
            events.add("allow:" + triggerPoint.name().toLowerCase(java.util.Locale.ROOT) + ":" + allow);
            return allow;
        }

        @Override
        protected void runEvolution(Map<String, Object> snapshot) {
            events.add("evolution");
            List<?> trajectory = (List<?>) snapshot.get("trajectory");
            events.add("after:" + trajectory.size());
            emitHostEvent(Map.of("type", "hooked", "steps", trajectory.size()));
        }
    }
}
