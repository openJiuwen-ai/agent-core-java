/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.optimizer.TextualParameter;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for instruction optimizer behavior.
 *
 * <p>Mirrors Python's {@code InstructionOptimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/instruction_optimizer.py}.</p>
 */
class InstructionOptimizerTest {

    @Test
    void selectSignalsConsumesOnlyFailureDrivenSignals() {
        TestableInstructionOptimizer optimizer = new TestableInstructionOptimizer(new FakeModel());
        EvolutionSignal success = EvolutionSignal.builder()
                .signalType("trace")
                .context(Map.of("score", 1))
                .build();
        EvolutionSignal scoreZero = EvolutionSignal.builder()
                .signalType("trace")
                .context(Map.of("score", 0))
                .build();
        EvolutionSignal failureType = EvolutionSignal.builder()
                .signalType("execution_failure")
                .context(Map.of("score", 1))
                .build();

        List<EvolutionSignal> selected = optimizer.exposeSelectSignals(List.of(success, scoreZero, failureType));

        assertEquals(List.of(scoreZero, failureType), selected);
    }

    @Test
    void backwardClearsStaleOptimizedPromptsWhenNoSelectedSignals() {
        FakeModel fakeModel = new FakeModel();
        InstructionOptimizer optimizer = new InstructionOptimizer(fakeModel);
        FakeOperator operator = new FakeOperator(
                "op1",
                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")),
                Map.of("system_prompt", "Prompt")
        );
        optimizer.bind(Map.of("op1", operator), List.of("system_prompt"), Map.of());
        TextualParameter parameter = optimizer.parameters().get("op1");
        parameter.setGradient("system_prompt_optimized", "stale");

        optimizer.backward(List.of(EvolutionSignal.builder().signalType("trace").context(Map.of("score", 1)).build()))
                .toCompletableFuture()
                .join();

        assertNull(parameter.getGradient("system_prompt_optimized"));
        assertEquals(0, fakeModel.invocations().size());
    }

    @Test
    void backwardPrecomputesBothPromptsAndStepReturnsUpdates() {
        FakeModel fakeModel = new FakeModel(
                "<INS>tighten instructions</INS>",
                "<SYSTEM_PROMPT_OPTIMIZED>Better system</SYSTEM_PROMPT_OPTIMIZED>"
                        + "<USER_PROMPT_OPTIMIZED>Better user</USER_PROMPT_OPTIMIZED>"
        );
        InstructionOptimizer optimizer = new InstructionOptimizer(fakeModel);
        FakeOperator operator = new FakeOperator(
                "op1",
                Map.of(
                        "system_prompt", new TunableSpec("system_prompt", "prompt", "system"),
                        "user_prompt", new TunableSpec("user_prompt", "prompt", "user")
                ),
                Map.of("system_prompt", "System prompt", "user_prompt", "User prompt")
        );
        optimizer.bind(Map.of("op1", operator), List.of("system_prompt", "user_prompt"), Map.of());

        optimizer.backward(List.of(failureSignal())).toCompletableFuture().join();
        Updates updates = optimizer.step();

        assertEquals("Better system", updates.get("op1", "system_prompt"));
        assertEquals("Better user", updates.get("op1", "user_prompt"));
        assertEquals(2, fakeModel.invocations().size());
    }

    @Test
    void extractTagRemovesPromptBaseWrapper() {
        FakeModel fakeModel = new FakeModel(
                "<INS>gradient</INS>",
                "<PROMPT_OPTIMIZED><prompt_base>Better prompt</prompt_base></PROMPT_OPTIMIZED>"
        );
        InstructionOptimizer optimizer = new InstructionOptimizer(fakeModel);
        optimizer.bind(Map.of("op1", systemOnlyOperator("Plain prompt")), List.of("system_prompt"), Map.of());

        optimizer.backward(List.of(failureSignal())).toCompletableFuture().join();

        assertEquals("Better prompt", optimizer.step().get("op1", "system_prompt"));
    }

    @Test
    void restorePlaceholdersAppendsStillMissingKeysAfterFallbackResponse() {
        FakeModel fakeModel = new FakeModel(
                "<INS>gradient</INS>",
                "<PROMPT_OPTIMIZED>Hello there</PROMPT_OPTIMIZED>",
                "Hello there"
        );
        InstructionOptimizer optimizer = new InstructionOptimizer(fakeModel);
        optimizer.bind(Map.of("op1", systemOnlyOperator("Hello {{name}}")), List.of("system_prompt"), Map.of());

        optimizer.backward(List.of(failureSignal())).toCompletableFuture().join();

        assertEquals("Hello there\n{{name}}", optimizer.step().get("op1", "system_prompt"));
    }

    private static EvolutionSignal failureSignal() {
        return EvolutionSignal.builder()
                .signalType("low_score")
                .context(Map.of(
                        "question", "q",
                        "label", "expected",
                        "answer", "actual",
                        "reason", "missed intent"
                ))
                .build();
    }

    private static FakeOperator systemOnlyOperator(String systemPrompt) {
        return new FakeOperator(
                "op1",
                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")),
                Map.of("system_prompt", systemPrompt)
        );
    }

    private static final class TestableInstructionOptimizer extends InstructionOptimizer {

        private TestableInstructionOptimizer(Model model) {
            super(model);
        }

        private List<EvolutionSignal> exposeSelectSignals(List<EvolutionSignal> signals) {
            return selectSignals(signals);
        }
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
