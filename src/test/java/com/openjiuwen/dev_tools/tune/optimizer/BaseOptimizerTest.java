/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code BaseOptimizer}, {@code TextualParameter}, {@code TraceNode}, and
 * {@code OptimizeHistory} in {@code openjiuwen/dev_tools/tune/optimizer/base.py}.
 */
class BaseOptimizerTest {

    @Test
    void bindParameterWrapsLegacyLlmCallsAndResetsHistory() {
        TestOptimizer optimizer = new TestOptimizer(Map.of("main", llmCall("main")));
        optimizer.traceCallback("main", Map.of("query", "old"), new AssistantMessage("old"), new SessionStub("case-1"));

        optimizer.bindParameter(Map.of("extra", llmCall("extra")));

        assertThat(optimizer.parameters()).containsKeys("main", "extra");
        assertThat(optimizer.getHistory().getHistory("case-1")).isNull();
        assertThat(optimizer.getBadCases()).isEmpty();
    }

    @Test
    void bindParameterRejectsNullLlmCallWithPythonStatus() {
        TestOptimizer optimizer = new TestOptimizer();
        Map<String, LLMCall> parameters = new LinkedHashMap<>();
        parameters.put("bad", null);

        assertThatThrownBy(() -> optimizer.bindParameter(parameters))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR))
                .hasMessageContaining("cannot bind a None parameter of bad");
    }

    @Test
    void contextScopeSetsAndClearsOptimizerCallbacks() {
        LLMCall first = llmCall("first");
        LLMCall second = llmCall("second");
        TestOptimizer optimizer = new TestOptimizer(Map.of("first", first, "second", second));

        assertThat(optimizer.enter()).isSameAs(optimizer);
        assertThat(first.getOptimizerCallback()).isNotNull();
        assertThat(second.getOptimizerCallback()).isNotNull();

        optimizer.close();

        assertThat(first.getOptimizerCallback()).isNull();
        assertThat(second.getOptimizerCallback()).isNull();
    }

    @Test
    void asyncContextCompatibilitySetsAndClearsCallbacks() {
        LLMCall llmCall = llmCall("async");
        TestOptimizer optimizer = new TestOptimizer(Map.of("async", llmCall));

        assertThat(optimizer.aenter().toCompletableFuture().join()).isSameAs(optimizer);
        assertThat(llmCall.getOptimizerCallback()).isNotNull();

        optimizer.aexit(null, null, null).toCompletableFuture().join();

        assertThat(llmCall.getOptimizerCallback()).isNull();
    }

    @Test
    void traceCallbackAddsHistoryUsingSessionIdAndMessageOutput() {
        TestOptimizer optimizer = new TestOptimizer(Map.of("main", llmCall("main")));
        AssistantMessage output = new AssistantMessage("assistant answer");

        optimizer.traceCallback("main", Map.of("query", "hello"), output, new SessionStub("case-42"));

        List<TraceNode> history = optimizer.getHistory().getHistory("case-42");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getLlmCallId()).isEqualTo("main");
        assertThat(history.get(0).getInputs()).containsEntry("query", "hello");
        assertThat(history.get(0).getOutputs()).isEqualTo("assistant answer");
    }

    @Test
    void optimizeHistoryFiltersByLlmCallIdAndReturnsNullForMissingCase() {
        OptimizeHistory history = new OptimizeHistory();
        history.addHistory("case-1", new TraceNode("case-1", "a", Map.of("x", 1), "out-a"));
        history.addHistory("case-1", new TraceNode("case-1", "b", Map.of("x", 2), "out-b"));

        assertThat(history.getHistory("missing")).isNull();
        assertThat(history.getLlmCallHistory("case-1", "b"))
                .hasSize(1)
                .extracting(TraceNode::getOutputs)
                .containsExactly("out-b");
    }

    @Test
    void backwardValidatesParametersAndWrapsSubclassErrors() {
        TestOptimizer empty = new TestOptimizer();
        TestOptimizer failing = new TestOptimizer(Map.of("main", llmCall("main")));
        failing.failBackward = true;

        assertThatThrownBy(() -> empty.backward(List.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR));
        assertThatThrownBy(() -> failing.backward(List.of(evaluated(0.0d), evaluated(1.0d))))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR));
        assertThat(failing.getBadCases()).hasSize(1);
    }

    @Test
    void updateDelegatesLogsAndClearsHistoryOnSuccessOrFailure() {
        TestOptimizer success = new TestOptimizer(Map.of("main", llmCall("main")));
        success.traceCallback("main", Map.of("query", "hello"), "answer", "case-1");

        success.update();

        assertThat(success.updateCount).isEqualTo(1);
        assertThat(success.getHistory().getHistory("case-1")).isNull();

        TestOptimizer failing = new TestOptimizer(Map.of("main", llmCall("main")));
        failing.failUpdate = true;
        failing.traceCallback("main", Map.of("query", "hello"), "answer", "case-2");

        assertThatThrownBy(failing::update)
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_OPTIMIZER_UPDATE_EXECUTION_ERROR));
        assertThat(failing.getHistory().getHistory("case-2")).isNull();
    }

    @Test
    void textualParameterStoresGradientsAndDescription() {
        TextualParameter parameter = new TextualParameter(llmCall("main"));

        parameter.setGradient("system", "gradient");
        parameter.setDescription("desc");

        assertThat(parameter.getGradient("system")).isEqualTo("gradient");
        assertThat(parameter.getDescription()).isEqualTo("desc");
        assertThat(parameter.getGradients()).containsEntry("system", "gradient");
    }

    private static LLMCall llmCall(String id) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        return new LLMCall("unit-model", model, "system", "{{query}}", false, false, id);
    }

    private static EvaluatedCase evaluated(double score) {
        Case caseValue = new Case(
                new LinkedHashMap<>(Map.of("query", "q")),
                new LinkedHashMap<>(Map.of("answer", "a"))
        );
        return new EvaluatedCase(caseValue, Map.of("answer", "p"), score, "");
    }

    private static final class TestOptimizer extends BaseOptimizer {
        private boolean failBackward;
        private boolean failUpdate;
        private int updateCount;

        private TestOptimizer() {
            super();
        }

        private TestOptimizer(Map<String, LLMCall> parameters) {
            super(parameters);
        }

        @Override
        protected void doUpdate() {
            updateCount++;
            if (failUpdate) {
                throw new IllegalStateException("update failed");
            }
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
            if (failBackward) {
                throw new IllegalStateException("backward failed");
            }
        }
    }

    private record SessionStub(String sessionId) {
        public String getSessionId() {
            return sessionId;
        }
    }
}
