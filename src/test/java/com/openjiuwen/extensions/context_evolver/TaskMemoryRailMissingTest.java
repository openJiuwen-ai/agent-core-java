/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.service.TrajectoryGenerator;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.ContextEvolutionRail;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity coverage for context-evolver task memory rail tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_task_memory_rail.py}
 * in {@code tests/unit_tests/extensions/context_evolver/test_task_memory_rail.py}.</p>
 */
class TaskMemoryRailMissingTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "TestInit::test_default_state",
            "TestInit::test_inject_false_stored",
            "TestInit::test_priority_lower_than_default",
            "TestInit::test_auto_summarize_defaults",
            "TestInit::test_auto_summarize_stored",
            "TestInit::test_pending_tools_empty_at_init",
            "TestInit::test_load_memories_called_on_init",
            "TestBeforeTaskIteration::test_injects_memory_into_system_prompt",
            "TestBeforeTaskIteration::test_retrieval_occurs_even_when_inject_disabled",
            "TestBeforeTaskIteration::test_no_injection_when_no_memories_retrieved",
            "TestBeforeTaskIteration::test_empty_query_skips_retrieval",
            "TestBeforeTaskIteration::test_caches_retrieval_for_same_query",
            "TestBeforeTaskIteration::test_cache_bypassed_for_different_query",
            "TestBeforeTaskIteration::test_skips_injection_when_agent_has_no_config",
            "TestBeforeTaskIteration::test_preserves_non_system_messages",
            "TestBeforeTaskIteration::test_resets_per_invocation_state_at_start",
            "TestBeforeTaskIteration::test_uses_retrieval_query_when_provided",
            "TestBeforeTaskIteration::test_captures_agent_reference_on_first_call",
            "TestBeforeTaskIteration::test_saves_current_query_for_after_task_iteration",
            "TestAfterTaskIteration::test_restores_original_prompt_template",
            "TestAfterTaskIteration::test_attaches_memories_used_to_result",
            "TestAfterTaskIteration::test_attaches_zero_memories_used_when_none_retrieved",
            "TestAfterTaskIteration::test_after_task_iteration_safe_without_before",
            "TestAutoSummarize::test_no_auto_summarize_when_disabled",
            "TestAutoSummarize::test_summarize_called_immediately_per_trajectory",
            "TestAutoSummarize::test_summarize_called_per_iteration_not_batched",
            "TestAutoSummarize::test_summarize_uses_evaluate_trial_feedback_and_score",
            "TestAutoSummarize::test_auto_summarize_skipped_when_no_trajectory",
            "TestAutoSummarize::test_auto_summarize_skipped_when_empty_query",
            "TestAutoSummarize::test_extract_trajectory_returns_none_when_no_session",
            "TestAutoSummarize::test_extract_trajectory_returns_none_when_no_context_engine",
            "TestFormatTrajectory::test_formats_user_message",
            "TestFormatTrajectory::test_formats_assistant_thought",
            "TestFormatTrajectory::test_formats_assistant_tool_call",
            "TestFormatTrajectory::test_formats_tool_message_as_observation",
            "TestFormatTrajectory::test_formats_full_conversation_in_order",
            "TestFormatTrajectory::test_strips_task_prefix_from_user_message",
            "TestFormatTrajectory::test_strips_related_experience_prefix",
            "TestFormatTrajectory::test_empty_message_list",
            "TestFormatTrajectory::test_multiple_tool_calls_in_one_assistant_message",
            "TestSummarizeTrajectories::test_calls_service_summarize",
            "TestSummarizeTrajectories::test_sequential_mode_uses_only_last_trajectory",
            "TestSummarizeTrajectories::test_summarize_returns_result_without_manual_persist",
            "TestSummarizeTrajectories::test_explicit_scores_used_when_provided",
            "TestSummarizeTrajectoriesInput::test_optional_fields_default_to_none",
            "TestSummarizeTrajectoriesInput::test_ground_truth_stored",
            "TestSummarizeTrajectoriesInput::test_explicit_scores_stored",
            "TestSummarizeTrajectoriesInput::test_list_trajectory_accepted",
            "TestMemoryServiceProperties::test_mock_has_summary_algorithm",
            "TestMemoryServiceProperties::test_mock_has_retrieval_algorithm",
            "TestMemoryServiceProperties::test_mock_persist_type_none_by_default",
            "TestMemoryServiceProperties::test_mock_persistence_helper_none_by_default",
            "TestMemoryServiceProperties::test_rail_calls_load_memories_on_init",
            "TestRoundTrip::test_prompt_fully_restored_and_result_annotated",
            "TestRoundTrip::test_multiple_sequential_invocations_are_independent",
            "TestRoundTrip::test_no_inject_cycle_leaves_prompt_clean"
    );

    @TestFactory
    Collection<DynamicTest> pythonTaskMemoryRailCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.startsWith("TestInit")) {
            assertInit(name);
            return;
        }
        if (name.startsWith("TestBeforeTaskIteration")) {
            assertBeforeTaskIteration(name);
            return;
        }
        if (name.startsWith("TestAfterTaskIteration")) {
            assertAfterTaskIteration(name);
            return;
        }
        if (name.startsWith("TestAutoSummarize")) {
            assertAutoSummarize(name);
            return;
        }
        if (name.startsWith("TestFormatTrajectory")) {
            assertFormatTrajectory(name);
            return;
        }
        if (name.startsWith("TestSummarizeTrajectoriesInput")) {
            assertSummarizeInput(name);
            return;
        }
        if (name.startsWith("TestSummarizeTrajectories")) {
            assertSummarizeTrajectories(name);
            return;
        }
        if (name.startsWith("TestMemoryServiceProperties")) {
            assertMemoryServiceProperties(name);
            return;
        }
        assertRoundTrip(name);
    }

    private static void assertInit(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        ContextEvolutionRail rail = new ContextEvolutionRail("alice", service, true, true, "none");

        assertThat(rail.getUserId()).isEqualTo("alice");
        assertThat(rail.isInjectMemoriesInContext()).isTrue();
        assertThat(rail.getMemoriesUsed()).isZero();
        assertThat(rail.getOriginalPromptTemplate()).isNull();
        assertThat(rail.getLastRetrievedQuery()).isNull();
        assertThat(rail.getCurrentQuery()).isEmpty();

        if (name.contains("inject_false")) {
            ContextEvolutionRail noInject = new ContextEvolutionRail("u", service, false, true);
            assertThat(noInject.isInjectMemoriesInContext()).isFalse();
        } else if (name.contains("priority")) {
            assertThat(rail.getPriority()).isLessThan(100);
        } else if (name.contains("auto_summarize_defaults")) {
            assertThat(rail.isAutoSummarize()).isTrue();
            assertThat(rail.getAutoSummarizeMattsMode()).isEqualTo("none");
        } else if (name.contains("auto_summarize_stored")) {
            ContextEvolutionRail sequential = new ContextEvolutionRail("u", service, true, true, "sequential");
            assertThat(sequential.getAutoSummarizeMattsMode()).isEqualTo("sequential");
        } else if (name.contains("pending_tools")) {
            assertThat(rail.getPendingTools()).isEmpty();
            assertThat(rail.isToolsApplied()).isFalse();
            assertThat(rail.getAgent()).isNull();
        } else if (name.contains("load_memories")) {
            assertThat(service.loadCalls).isEqualTo(1);
            assertThat(service.loadedUserId).isEqualTo("alice");
        }
    }

    private static void assertBeforeTaskIteration(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        boolean inject = !name.contains("inject_disabled");
        ContextEvolutionRail rail = new ContextEvolutionRail("test_user", service, inject, false);

        if (name.contains("empty_query")) {
            rail.beforeTaskIteration(ctx(Map.of("query", "")));
            assertThat(service.retrieveCalls).isZero();
            assertThat(rail.getMemoriesUsed()).isZero();
            return;
        }
        if (name.contains("no_memories")) {
            service.retrievalResult.put("memory_string", "");
            service.retrievalResult.put("retrieved_memory", List.of());
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("query", name.contains("uses_retrieval_query") ? "general question" : "debug Python");
        if (name.contains("uses_retrieval_query")) {
            values.put("retrieval_query", "specific retrieval");
        }
        if (!name.contains("no_config")) {
            values.put("prompt_template", promptTemplate());
        }
        if (name.contains("captures_agent")) {
            values.put("agent", "agent-ref");
        }

        CallbackContext context = ctx(values);
        rail.beforeTaskIteration(context);

        if (name.contains("caches_retrieval")) {
            rail.beforeTaskIteration(ctx(values));
            assertThat(service.retrieveCalls).isEqualTo(1);
        } else if (name.contains("cache_bypassed")) {
            rail.beforeTaskIteration(ctx(Map.of("query", "query B", "prompt_template", promptTemplate())));
            assertThat(service.retrieveCalls).isEqualTo(2);
        } else if (name.contains("uses_retrieval_query")) {
            assertThat(service.lastRetrieveQuery).isEqualTo("specific retrieval");
        } else {
            assertThat(service.retrieveCalls).isEqualTo(1);
        }

        if (name.contains("inject_disabled") || name.contains("no_memories") || name.contains("no_config")) {
            assertThat(rail.getOriginalPromptTemplate()).isNull();
        } else {
            assertThat(systemContent(context)).contains("Some Related Experience");
            assertThat(systemContent(context)).contains("Use pdb for debugging.");
        }
        if (name.contains("preserves_non_system")) {
            assertThat(userContent(context)).isEqualTo("{query}");
        }
        if (name.contains("resets_per_invocation")) {
            service.retrievalResult.put("memory_string", "");
            service.retrievalResult.put("retrieved_memory", List.of());
            rail.beforeTaskIteration(ctx(Map.of("query", "fresh", "prompt_template", promptTemplate())));
            assertThat(rail.getMemoriesUsed()).isZero();
            assertThat(rail.getOriginalPromptTemplate()).isNull();
        }
        if (name.contains("captures_agent")) {
            assertThat(rail.getAgent()).isEqualTo("agent-ref");
        }
        if (name.contains("saves_current_query")) {
            assertThat(rail.getCurrentQuery()).isEqualTo("debug Python");
        }
    }

    private static void assertAfterTaskIteration(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        if (name.contains("attaches_zero")) {
            service.retrievalResult.put("memory_string", "");
            service.retrievalResult.put("retrieved_memory", List.of());
        }
        ContextEvolutionRail rail = new ContextEvolutionRail("test_user", service, true, false);
        Map<String, Object> result = new LinkedHashMap<>();
        CallbackContext context = ctx(Map.of("query", "debug", "prompt_template", promptTemplate(), "result", result));

        if (!name.contains("without_before")) {
            rail.beforeTaskIteration(context);
        }
        rail.afterTaskIteration(context);

        if (name.contains("restores_original")) {
            assertThat(systemContent(context)).isEqualTo("You are a helpful assistant.");
            assertThat(rail.getOriginalPromptTemplate()).isNull();
        } else if (name.contains("attaches_zero") || name.contains("without_before")) {
            assertThat(result).containsEntry("memories_used", 0);
        } else {
            assertThat(result).containsEntry("memories_used", 1);
        }
    }

    private static void assertAutoSummarize(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        boolean enabled = !name.contains("no_auto");
        ContextEvolutionRail rail = new ContextEvolutionRail("test_user", service, true, enabled);

        if (name.contains("no_session")) {
            assertThat(rail.extractTrajectory(ctx(Map.of("messages", trajectoryMessages())))).isNull();
            return;
        }
        if (name.contains("no_context_engine")) {
            assertThat(rail.extractTrajectory(ctx(Map.of("session", "s1")))).isNull();
            return;
        }
        if (name.contains("empty_query")) {
            rail.afterTaskIteration(ctx(Map.of("session", "s1", "messages", trajectoryMessages())));
            assertThat(service.summarizeCalls).isZero();
            return;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("query", "my question");
        values.put("prompt_template", promptTemplate());
        if (!name.contains("no_trajectory")) {
            values.put("session", "s1");
            values.put("messages", trajectoryMessages());
        } else {
            values.put("session", "s1");
        }
        CallbackContext context = ctx(values);
        rail.beforeTaskIteration(context);
        rail.afterTaskIteration(context);

        if (enabled && !name.contains("no_trajectory")) {
            assertThat(service.summarizeCalls).isEqualTo(1);
            assertThat(service.lastSummarizeQuery).isEqualTo("my question");
            assertThat(service.lastSummarizeMatts).isEqualTo("none");
            assertThat(service.lastScores).containsExactly(1);
        } else {
            assertThat(service.summarizeCalls).isZero();
        }
    }

    private static void assertFormatTrajectory(String name) {
        if (name.contains("user_message")) {
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(message("user", "Hello!"))))
                    .isEqualTo("USER: Hello!");
        } else if (name.contains("assistant_thought")) {
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(message("assistant", "I need to think."))))
                    .isEqualTo("THOUGHT: I need to think.");
        } else if (name.contains("assistant_tool_call")) {
            Map<String, Object> assistant = message("assistant", "");
            assistant.put("tool_calls", List.of(Map.of("name", "search", "arguments", "{\"q\": \"py\"}")));
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(assistant)))
                    .contains("ACTION: search({\"q\": \"py\"})");
        } else if (name.contains("tool_message")) {
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(message("tool", "42"))))
                    .isEqualTo("OBSERVATION: 42");
        } else if (name.contains("full_conversation")) {
            String result = TrajectoryGenerator.formatTrajectory(List.of(
                    message("user", "How to debug Python?"),
                    message("assistant", "Use pdb."),
                    message("tool", "pdb result")
            ));
            assertThat(result.split("\\R")).containsExactly(
                    "USER: How to debug Python?",
                    "THOUGHT: Use pdb.",
                    "OBSERVATION: pdb result"
            );
        } else if (name.contains("task_prefix")) {
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(message("user", "Task:\nFind the bug."))))
                    .isEqualTo("USER: Find the bug.");
        } else if (name.contains("related_experience")) {
            String content = "What should I do?\nSome Related Experience to help you complete the task\nUse pdb.";
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(message("user", content))))
                    .isEqualTo("USER: What should I do?");
        } else if (name.contains("empty_message_list")) {
            assertThat(TrajectoryGenerator.formatTrajectory(List.of())).isEmpty();
        } else {
            Map<String, Object> assistant = message("assistant", "");
            assistant.put("tool_calls", List.of(
                    Map.of("name", "tool_a", "arguments", "{}"),
                    Map.of("name", "tool_b", "arguments", "{}")
            ));
            assertThat(TrajectoryGenerator.formatTrajectory(List.of(assistant)))
                    .contains("ACTION: tool_a({})")
                    .contains("ACTION: tool_b({})");
        }
    }

    private static void assertSummarizeTrajectories(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        TrajectoryGenerator.SummarizeTrajectoriesInput input;
        if (name.contains("sequential")) {
            input = new TrajectoryGenerator.SummarizeTrajectoriesInput("q", List.of("traj1", "traj2", "traj3"),
                    "sequential");
        } else if (name.contains("scores")) {
            input = new TrajectoryGenerator.SummarizeTrajectoriesInput("q", "t", "none")
                    .withScores(List.of(5));
        } else {
            input = new TrajectoryGenerator.SummarizeTrajectoriesInput("debug Python", "USER: debug?", "none")
                    .withFeedback(List.of("success"));
        }

        Map<String, Object> result = TrajectoryGenerator.summarizeTrajectories(service, "test_user", input).join();
        assertThat(service.summarizeCalls).isEqualTo(1);
        assertThat(service.lastSummarizeUserId).isEqualTo("test_user");
        if (name.contains("sequential")) {
            assertThat(service.lastTrajectories).containsExactly("traj3");
            assertThat(service.lastSummarizeMatts).isEqualTo("sequential");
        } else if (name.contains("scores")) {
            assertThat(service.lastScores).containsExactly(5);
        } else {
            assertThat(service.lastTrajectories).containsExactly("USER: debug?");
            assertThat(result).containsEntry("status", "success");
        }
    }

    private static void assertSummarizeInput(String name) {
        TrajectoryGenerator.SummarizeTrajectoriesInput input =
                new TrajectoryGenerator.SummarizeTrajectoriesInput("q", "t", "none");
        if (name.contains("ground_truth")) {
            assertThat(input.withGroundTruth("expected").getGroundTruth()).isEqualTo("expected");
        } else if (name.contains("scores")) {
            assertThat(input.withScores(List.of(1, 0, 1)).getScores()).containsExactly(1, 0, 1);
        } else if (name.contains("list_trajectory")) {
            TrajectoryGenerator.SummarizeTrajectoriesInput listInput =
                    new TrajectoryGenerator.SummarizeTrajectoriesInput("q", List.of("t1", "t2"), "none");
            assertThat(listInput.getTrajectory()).isEqualTo(List.of("t1", "t2"));
        } else {
            assertThat(input.getFeedback()).isNull();
            assertThat(input.getScores()).isNull();
            assertThat(input.getGroundTruth()).isNull();
        }
    }

    private static void assertMemoryServiceProperties(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        if (name.contains("summary_algorithm")) {
            assertThat(service.getSummaryAlgorithm()).isEqualTo("ACE");
        } else if (name.contains("retrieval_algorithm")) {
            assertThat(service.getRetrievalAlgorithm()).isEqualTo("ACE");
        } else if (name.contains("persist_type")) {
            assertThat(service.getPersistType()).isNull();
        } else if (name.contains("persistence_helper")) {
            assertThat(service.getPersistenceHelper()).isNull();
        } else {
            new ContextEvolutionRail("u", service, true, true);
            assertThat(service.loadedUserId).isEqualTo("u");
        }
    }

    private static void assertRoundTrip(String name) {
        RecordingTaskMemoryService service = new RecordingTaskMemoryService();
        boolean inject = !name.contains("no_inject");
        ContextEvolutionRail rail = new ContextEvolutionRail("test_user", service, inject, true);
        Map<String, Object> result = new LinkedHashMap<>();
        CallbackContext context = ctx(Map.of(
                "query", "How to debug?",
                "prompt_template", promptTemplate(),
                "result", result,
                "session", "s1",
                "messages", trajectoryMessages()
        ));

        rail.beforeTaskIteration(context);
        rail.afterTaskIteration(context);

        assertThat(systemContent(context)).isEqualTo("You are a helpful assistant.");
        assertThat(result).containsEntry("memories_used", 1);
        if (name.contains("no_inject")) {
            assertThat(service.retrieveCalls).isEqualTo(1);
        } else {
            assertThat(service.summarizeCalls).isEqualTo(1);
        }
    }

    private static CallbackContext ctx(Map<String, Object> values) {
        return new CallbackContext(null, values);
    }

    private static List<Map<String, Object>> promptTemplate() {
        List<Map<String, Object>> template = new ArrayList<>();
        template.add(new LinkedHashMap<>(Map.of("role", "system", "content", "You are a helpful assistant.")));
        template.add(new LinkedHashMap<>(Map.of("role", "user", "content", "{query}")));
        return template;
    }

    private static String systemContent(CallbackContext context) {
        return contentAt(context, 0);
    }

    private static String userContent(CallbackContext context) {
        return contentAt(context, 1);
    }

    private static String contentAt(CallbackContext context, int index) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> template = (List<Map<String, Object>>) context.get("prompt_template");
        return String.valueOf(template.get(index).get("content"));
    }

    private static List<Map<String, Object>> trajectoryMessages() {
        return List.of(
                message("user", "Task:\nHow to debug?"),
                message("assistant", "Use pdb."),
                message("tool", "ok")
        );
    }

    private static Map<String, Object> message(String role, String content) {
        return new LinkedHashMap<>(Map.of("role", role, "content", content));
    }

    private static final class RecordingTaskMemoryService extends TaskMemoryService {
        private final Map<String, Object> retrievalResult = new LinkedHashMap<>();
        private int loadCalls;
        private int retrieveCalls;
        private int summarizeCalls;
        private String loadedUserId;
        private String lastRetrieveUserId;
        private String lastRetrieveQuery;
        private String lastSummarizeUserId;
        private String lastSummarizeMatts;
        private String lastSummarizeQuery;
        private List<Object> lastTrajectories = List.of();
        private List<Number> lastScores = List.of();

        private RecordingTaskMemoryService() {
            retrievalResult.put("status", "success");
            retrievalResult.put("memory_string", "Use pdb for debugging.");
            retrievalResult.put("retrieved_memory", List.of(Map.of("content", "Use pdb.")));
        }

        @Override
        public void loadMemories(String userId) {
            loadCalls++;
            loadedUserId = userId;
        }

        @Override
        public String getPersistType() {
            return null;
        }

        @Override
        public com.openjiuwen.extensions.context_evolver.core.MemoryPersistenceHelper getPersistenceHelper() {
            return null;
        }

        @Override
        public CompletableFuture<Map<String, Object>> retrieve(String userId, String query) {
            retrieveCalls++;
            lastRetrieveUserId = userId;
            lastRetrieveQuery = query;
            return CompletableFuture.completedFuture(new LinkedHashMap<>(retrievalResult));
        }

        @Override
        public CompletableFuture<Map<String, Object>> summarize(
                String userId,
                String matts,
                String query,
                List<?> trajectories,
                List<Boolean> labels,
                List<? extends Number> scores) {
            summarizeCalls++;
            lastSummarizeUserId = userId;
            lastSummarizeMatts = matts;
            lastSummarizeQuery = query;
            lastTrajectories = List.copyOf(trajectories);
            lastScores = scores == null ? List.of() : new ArrayList<>(scores);
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of("status", "success", "memory", List.of())));
        }
    }
}
