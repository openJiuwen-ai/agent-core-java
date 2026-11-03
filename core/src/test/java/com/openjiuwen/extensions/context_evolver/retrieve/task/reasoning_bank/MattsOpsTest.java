/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's MaTTS operation behavior in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.
 */
class MattsOpsTest {

    private final ServiceContext serviceContext = new ServiceContext();

    @BeforeEach
    void setUp() {
        serviceContext.clear();
    }

    @AfterEach
    void tearDown() {
        serviceContext.clear();
    }

    @Test
    void parallelScalingGeneratesTrajectoriesAndRestoresTemperature() {
        RecordingLlm llm = new RecordingLlm("unused");
        llm.setTemperature(0.4D);
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("user_id", "user-1");
        context.set("retrieved_memories", List.of("memory"));
        context.set("agent_flow", (MattsAgentFlow) trajectory -> {
            trajectory.set("answer", "answer-" + trajectory.get("query"));
            trajectory.set("steps", List.of("step"));
            trajectory.set("success", true);
            assertThat(trajectory.get("retrieved_memories")).isEqualTo(List.of("memory"));
            return CompletableFuture.completedFuture(null);
        });

        new ParallelScalingOp(2, 0.95D).asyncExecute(context).join();

        assertThat(llm.getTemperature()).isEqualTo(0.4D);
        assertThat(context.get("scaling_factor")).isEqualTo(2);
        assertThat(trajectories(context)).hasSize(2)
                .allSatisfy(trajectory -> {
                    assertThat(trajectory.get("answer")).isEqualTo("answer-query");
                    assertThat(trajectory.get("success")).isEqualTo(true);
                });
    }

    @Test
    void parallelScalingWithoutAgentFlowStoresEmptyTrajectories() {
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("user_id", "user-1");

        new ParallelScalingOp(2, 0.9D).asyncExecute(context).join();

        assertThat(trajectories(context)).isEmpty();
        assertThat(context.get("scaling_factor")).isEqualTo(2);
    }

    @Test
    void sequentialScalingRefinesAnswerAcrossRounds() {
        RecordingLlm llm = new RecordingLlm("first", "second");
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("user_id", "user-1");
        context.set("answer", "initial");

        new SequentialScalingOp(2).asyncExecute(context).join();

        assertThat(context.get("answer")).isEqualTo("second");
        assertThat(context.get("refined_answer")).isEqualTo("second");
        assertThat(context.get("scaling_factor")).isEqualTo(2);
        assertThat(history(context)).extracting(item -> item.get("response")).containsExactly("first", "second");
        assertThat(llm.getPrompts().get(0)).contains("Previous answer: initial");
        assertThat(llm.getPrompts().get(1)).contains("Previous answer: first");
    }

    @Test
    void bestOfNSelectsTrajectoryAndCalculatesPassAtK() {
        RecordingLlm llm = new RecordingLlm("Choose index 1");
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("parallel_trajectories", List.of(
                trajectory(0, "bad", false),
                trajectory(1, "good", true),
                trajectory(2, "also bad", false)));

        new BestOfNOp().asyncExecute(context).join();

        assertThat(context.get("answer")).isEqualTo("good");
        assertThat(context.get("best_trajectory_index")).isEqualTo(1);
        assertThat((Double) context.get("pass_at_k")).isEqualTo(1.0D / 3.0D);
        assertThat(llm.getTemperatureCalls()).containsExactly(0.0D);
    }

    @Test
    void bestOfNFallsBackToFirstTrajectoryOnLlmError() {
        RecordingLlm llm = RecordingLlm.failing();
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("parallel_trajectories", List.of(
                trajectory(0, "first", true),
                trajectory(1, "second", true)));

        new BestOfNOp().asyncExecute(context).join();

        assertThat(context.get("answer")).isEqualTo("first");
        assertThat(context.get("best_trajectory_index")).isEqualTo(0);
    }

    @Test
    void bestOfNSkipsWhenNoParallelTrajectories() {
        RuntimeContext context = new RuntimeContext();

        new BestOfNOp().asyncExecute(context).join();

        assertThat(context.get("answer")).isNull();
    }

    @Test
    void selfContrastParsesMemoryItems() {
        RecordingLlm llm = new RecordingLlm("""
                # Memory Item 1
                ## Title Check assumptions
                ## Description Verify the premise
                ## Content Re-read the task before acting.
                Continue with concrete evidence.

                # Memory Item 2
                ## Title Compare failures
                ## Description Learn from unsuccessful attempts
                ## Content Identify repeated mistakes.
                """);
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("user_id", "user-1");
        context.set("parallel_trajectories", List.of(
                trajectory(0, "success answer", true),
                trajectory(1, "failed answer", false)));

        new SelfContrastMemoryOp().asyncExecute(context).join();

        List<ReasoningBankMemory> memories = contrastiveMemories(context);
        assertThat(memories).hasSize(2);
        assertThat(memories).allSatisfy(memory -> assertThat(memory.getWorkspaceId()).isEqualTo("user-1"));
        assertThat(memories.stream().map(memory -> memory.getMemory().get(0)).map(ReasoningBankMemoryItem::getTitle))
                .containsExactly("Check assumptions", "Compare failures");
        assertThat(memories.get(0).getMemory().get(0).getContent())
                .contains("Re-read the task before acting. Continue with concrete evidence.");
        assertThat(llm.getTemperatureCalls()).containsExactly(1.0D);
    }

    @Test
    void selfContrastStoresEmptyListOnLlmError() {
        RecordingLlm llm = RecordingLlm.failing();
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");
        context.set("parallel_trajectories", List.of(trajectory(0, "answer", true)));

        new SelfContrastMemoryOp().asyncExecute(context).join();

        assertThat(context.get("contrastive_memories")).isEqualTo(List.of());
    }

    @Test
    void selfContrastSkipsWhenNoTrajectories() {
        RuntimeContext context = new RuntimeContext();

        new SelfContrastMemoryOp().asyncExecute(context).join();

        assertThat(context.get("contrastive_memories")).isNull();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> trajectories(RuntimeContext context) {
        return (List<Map<String, Object>>) context.get("parallel_trajectories");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> history(RuntimeContext context) {
        return (List<Map<String, Object>>) context.get("refinement_history");
    }

    @SuppressWarnings("unchecked")
    private static List<ReasoningBankMemory> contrastiveMemories(RuntimeContext context) {
        return (List<ReasoningBankMemory>) context.get("contrastive_memories");
    }

    private static Map<String, Object> trajectory(int index, String answer, boolean success) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", index);
        map.put("answer", answer);
        map.put("success", success);
        map.put("steps", List.of("step-1", "step-2"));
        return map;
    }

    private static final class RecordingLlm implements MattsAsyncLlm, MattsTemperatureAware {
        private final List<String> responses;
        private final boolean failing;
        private final List<String> prompts = new ArrayList<>();
        private final List<Double> temperatureCalls = new ArrayList<>();
        private int index;
        private double temperature = 0.7D;

        private RecordingLlm(String... responses) {
            this(false, responses);
        }

        private RecordingLlm(boolean failing, String... responses) {
            this.failing = failing;
            this.responses = List.of(responses);
        }

        static RecordingLlm failing() {
            return new RecordingLlm(true, "unused");
        }

        @Override
        public CompletableFuture<String> asyncGenerate(String prompt) {
            prompts.add(prompt);
            if (failing) {
                return CompletableFuture.failedFuture(new IllegalStateException("llm failed"));
            }
            String response = responses.get(Math.min(index, responses.size() - 1));
            index += 1;
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<String> asyncGenerate(String prompt, double temperature) {
            temperatureCalls.add(temperature);
            return asyncGenerate(prompt);
        }

        @Override
        public double getTemperature() {
            return temperature;
        }

        @Override
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        private List<String> getPrompts() {
            return prompts;
        }

        private List<Double> getTemperatureCalls() {
            return temperatureCalls;
        }
    }
}
