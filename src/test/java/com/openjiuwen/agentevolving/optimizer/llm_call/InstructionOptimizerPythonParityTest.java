/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.optimizer.TextualParameter;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for instruction optimizer public API behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.llm_call.test_instruction_optimizer} in
 * {@code tests/unit_tests/agent_evolving/optimizer/llm_call/test_instruction_optimizer.py}.</p>
 */
class InstructionOptimizerPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/agent_evolving/optimizer/llm_call/test_instruction_optimizer.py";

    @TestFactory
    Collection<DynamicTest> pythonInstructionOptimizerCases() {
        return List.of(
                caseOf("TestInstructionOptimizerInit::test_init_with_params",
                        InstructionOptimizerPythonParityTest::initWithParams),
                caseOf("TestInstructionOptimizerBackward::test_backward_no_operators_raises",
                        InstructionOptimizerPythonParityTest::backwardNoOperatorsRaises),
                caseOf("TestInstructionOptimizerBackward::test_backward_skips_missing_operator",
                        InstructionOptimizerPythonParityTest::backwardSkipsMissingOperator),
                caseOf("TestInstructionOptimizerBackward::test_backward_generates_gradients",
                        InstructionOptimizerPythonParityTest::backwardGeneratesGradients),
                caseOf("TestInstructionOptimizerStep::test_step_empty_operators_raises",
                        InstructionOptimizerPythonParityTest::stepEmptyOperatorsRaises),
                caseOf("TestInstructionOptimizerStep::test_step_single_operator",
                        InstructionOptimizerPythonParityTest::stepSingleOperator),
                caseOf("TestInstructionOptimizerBadCasesBehavior::test_backward_with_mixed_scores",
                        InstructionOptimizerPythonParityTest::backwardWithMixedScores),
                caseOf("TestInstructionOptimizerBadCasesBehavior::test_backward_ignores_non_failure_signals_explicitly",
                        InstructionOptimizerPythonParityTest::backwardIgnoresNonFailureSignalsExplicitly),
                caseOf("TestInstructionOptimizerFullPipeline::test_full_pipeline_no_cases",
                        InstructionOptimizerPythonParityTest::fullPipelineNoCases),
                caseOf("TestInstructionOptimizerFullPipeline::test_full_pipeline_with_bad_cases",
                        InstructionOptimizerPythonParityTest::fullPipelineWithBadCases),
                caseOf("TestInstructionOptimizerStepPaths::test_step_system_prompt_only",
                        InstructionOptimizerPythonParityTest::stepSystemPromptOnly),
                caseOf("TestInstructionOptimizerStepPaths::test_step_user_prompt_only",
                        InstructionOptimizerPythonParityTest::stepUserPromptOnly),
                caseOf("TestInstructionOptimizerStepPaths::test_step_returns_none_when_empty",
                        InstructionOptimizerPythonParityTest::stepReturnsNoneWhenEmpty)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void initWithParams() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel());

        assertThat(optimizer.parameters()).isEmpty();
    }

    private static void backwardNoOperatorsRaises() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel());
        optimizer.bind(Map.of(), List.of(), Map.of());

        assertThatThrownBy(() -> optimizer.backward(List.of()))
                .isInstanceOf(BaseError.class);
    }

    private static void backwardSkipsMissingOperator() {
        FakeModel model = new FakeModel();
        InstructionOptimizer optimizer = new InstructionOptimizer(model);

        optimizer.bind(Map.of(), List.of(LLMCallOptimizerBase.SYSTEM_PROMPT), Map.of());

        assertThat(model.invocations()).isEmpty();
        assertThat(optimizer.parameters()).isEmpty();
    }

    private static void backwardGeneratesGradients() {
        FakeModel model = new FakeModel("Gradient text", "");
        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", mockOperator("op1")), List.of(), Map.of());

        optimizer.backward(List.of(badSignal(0.0d))).toCompletableFuture().join();
        TextualParameter parameter = optimizer.parameters().get("op1");

        assertThat(parameter.getGradient(LLMCallOptimizerBase.SYSTEM_PROMPT) != null
                || parameter.getGradient(LLMCallOptimizerBase.USER_PROMPT) != null).isTrue();
    }

    private static void stepEmptyOperatorsRaises() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel());
        optimizer.bind(Map.of(), List.of(), Map.of());

        assertThatThrownBy(optimizer::step).isInstanceOf(BaseError.class);
    }

    private static void stepSingleOperator() {
        FakeModel model = new FakeModel(
                "Gradient text",
                "<SYSTEM_PROMPT_OPTIMIZED>new system</SYSTEM_PROMPT_OPTIMIZED>"
                        + "<USER_PROMPT_OPTIMIZED>new user</USER_PROMPT_OPTIMIZED>"
        );
        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", mockOperator("op1")), List.of(), Map.of());

        optimizer.backward(List.of(badSignal(0.0d))).toCompletableFuture().join();
        Updates result = optimizer.step();

        assertThat(result).isNotNull();
    }

    private static void backwardWithMixedScores() {
        FakeModel model = new FakeModel("Gradient text", "");
        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", mockOperator("op1")), List.of(), Map.of());

        optimizer.backward(List.of(badSignal(0.0d), badSignal(1.0d))).toCompletableFuture().join();

        assertThat(model.invocations()).isNotEmpty();
    }

    private static void backwardIgnoresNonFailureSignalsExplicitly() {
        FakeModel model = new FakeModel();
        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", mockOperator("op1")), List.of(), Map.of());

        optimizer.backward(List.of(
                EvolutionSignal.builder()
                        .signalType("conversation_review")
                        .section("Examples")
                        .excerpt("useful but not failure-driven")
                        .build(),
                badSignal(1.0d)
        )).toCompletableFuture().join();

        assertThat(model.invocations()).isEmpty();
    }

    private static void fullPipelineNoCases() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel(
                "Gradient text",
                "<SYSTEM_PROMPT_OPTIMIZED>optimized</SYSTEM_PROMPT_OPTIMIZED>"
                        + "<USER_PROMPT_OPTIMIZED>optimized</USER_PROMPT_OPTIMIZED>"
        ));
        optimizer.bind(Map.of("op1", mockOperator("op1")), List.of(), Map.of());

        optimizer.backward(List.of()).toCompletableFuture().join();
        Updates result = optimizer.step();

        assertThat(result).isNotNull();
    }

    private static void fullPipelineWithBadCases() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel(
                "Gradient text",
                "<SYSTEM_PROMPT_OPTIMIZED>optimized system</SYSTEM_PROMPT_OPTIMIZED>"
                        + "<USER_PROMPT_OPTIMIZED>optimized user</USER_PROMPT_OPTIMIZED>"
        ));
        optimizer.bind(Map.of("op1", mockOperator("op1")), List.of(), Map.of());

        optimizer.backward(List.of(badSignal(0.0d))).toCompletableFuture().join();
        Updates result = optimizer.step();

        assertThat(result).isNotNull();
    }

    private static void stepSystemPromptOnly() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel(
                "Gradient text",
                "<PROMPT_OPTIMIZED>new sys</PROMPT_OPTIMIZED>"
        ));
        optimizer.bind(Map.of("op1", mockOperator("op1")),
                List.of(LLMCallOptimizerBase.SYSTEM_PROMPT), Map.of());

        optimizer.backward(List.of(badSignal(0.0d))).toCompletableFuture().join();
        Updates result = optimizer.step();

        assertThat(result).isNotEmpty();
        assertThat(result.get("op1", LLMCallOptimizerBase.SYSTEM_PROMPT)).isEqualTo("new sys");
    }

    private static void stepUserPromptOnly() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel(
                "Gradient text",
                "<PROMPT_OPTIMIZED>new usr</PROMPT_OPTIMIZED>"
        ));
        optimizer.bind(Map.of("op1", mockOperator("op1")),
                List.of(LLMCallOptimizerBase.USER_PROMPT), Map.of());

        optimizer.backward(List.of(badSignal(0.0d))).toCompletableFuture().join();
        Updates result = optimizer.step();

        assertThat(result).isNotEmpty();
        assertThat(result.get("op1", LLMCallOptimizerBase.USER_PROMPT)).isNotNull();
    }

    private static void stepReturnsNoneWhenEmpty() {
        InstructionOptimizer optimizer = new InstructionOptimizer(new FakeModel());
        optimizer.bind(Map.of("op1", mockOperator("op1")),
                List.of(LLMCallOptimizerBase.SYSTEM_PROMPT), Map.of());

        optimizer.backward(List.of()).toCompletableFuture().join();
        Updates result = optimizer.step();

        assertThat(result).isEmpty();
    }

    private static EvolutionSignal badSignal(double score) {
        return EvolutionSignal.builder()
                .signalType("evaluated")
                .section("Troubleshooting")
                .excerpt(String.format("score=%.2f", score))
                .context(Map.of(
                        "question", "test question",
                        "label", "expected answer",
                        "answer", "wrong",
                        "reason", "incorrect",
                        "score", score
                ))
                .build();
    }

    private static FakeOperator mockOperator(String operatorId) {
        return new FakeOperator(
                operatorId,
                Map.of(
                        LLMCallOptimizerBase.SYSTEM_PROMPT,
                        new TunableSpec(LLMCallOptimizerBase.SYSTEM_PROMPT, "prompt", "system"),
                        LLMCallOptimizerBase.USER_PROMPT,
                        new TunableSpec(LLMCallOptimizerBase.USER_PROMPT, "prompt", "user")
                ),
                Map.of(
                        LLMCallOptimizerBase.SYSTEM_PROMPT, "You are helpful.",
                        LLMCallOptimizerBase.USER_PROMPT, "{{query}}"
                )
        );
    }

    private static final class FakeModel extends Model {
        private final Queue<String> responses = new ArrayDeque<>();
        private final List<List<BaseMessage>> invocations = new ArrayList<>();

        private FakeModel(String... responses) {
            super((messages, modelConfig, modelClientConfig, options) ->
                    CompletableFuture.completedFuture(new AssistantMessage(""))
            );
            this.responses.addAll(List.of(responses));
        }

        @Override
        public java.util.concurrent.CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages) {
            invocations.add(List.copyOf(messages));
            String response = responses.isEmpty() ? "" : responses.remove();
            return CompletableFuture.completedFuture(new AssistantMessage(response));
        }

        private List<List<BaseMessage>> invocations() {
            return invocations;
        }
    }

    private static final class FakeOperator extends Operator {
        private final String operatorId;
        private final Map<String, TunableSpec> tunables;
        private final Map<String, Object> state;

        private FakeOperator(String operatorId, Map<String, TunableSpec> tunables, Map<String, Object> state) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>(tunables);
            this.state = new LinkedHashMap<>(state);
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return new LinkedHashMap<>(tunables);
        }

        @Override
        public Map<String, Object> getState() {
            return new LinkedHashMap<>(state);
        }

        @Override
        public void setParameter(String target, Object value) {
            state.put(target, value);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.state.clear();
            if (state != null) {
                this.state.putAll(state);
            }
        }
    }
}
