/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;
import com.openjiuwen.dev_tools.tune.optimizer.TraceNode;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code AdoptOptimizer} and {@code PartialOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/adopt/adopt_optimizer.py}.
 */
class AdoptOptimizerTest {

    @Test
    void backwardConvertsTraceHistoryIntoPartialGradients() {
        FakeModel optimizerModel = new FakeModel(
                "node-description",
                "workflow-difference",
                "deep-reflection",
                "<REVISED_NODE_OUTPUT>better node output</REVISED_NODE_OUTPUT>",
                "local-gradient",
                "reduced-gradient"
        );
        optimizerModel.failuresBeforeNextSuccess(1);
        LLMCall llmCall = llmCall("main");
        AdoptOptimizer optimizer = new AdoptOptimizer(
                optimizerModel,
                "adopt-model",
                Map.of("main", llmCall),
                Map.of("agent_description", "workflow description",
                        "constrain", "keep concise",
                        "external_knowledge", "domain facts")
        );
        optimizer.getHistory().addHistory(
                "bad-case",
                new TraceNode("bad-case", "main", Map.of("question", "q0"), "wrong node output")
        );

        optimizer.backward(List.of(
                evaluatedCase("bad-case", 0.0d),
                evaluatedCase("good-case", 1.0d)
        ));

        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(parameter.getDescription()).isEqualTo("node-description");
        assertThat(parameter.getGradient("system_prompt")).isEqualTo("reduced-gradient");
        assertThat(parameter.getGradient("user_prompt")).isNull();
        assertThat(optimizerModel.invocations()).hasSize(7);
        assertThat(optimizerModel.options()).allSatisfy(options -> {
            assertThat(options.getModel()).isEqualTo("adopt-model");
            assertThat(options.getTemperature()).isEqualTo(0.3f);
            assertThat(options.getTopP()).isEqualTo(0.7f);
        });
        assertThat(messageText(optimizerModel.invocations().get(4))).contains("REVISED_NODE_OUTPUT");
    }

    @Test
    void extractContentFromResponseReturnsTaggedContentOnly() {
        String response = "prefix<REVISED_NODE_OUTPUT>answer</REVISED_NODE_OUTPUT>suffix";

        assertThat(AdoptOptimizer.extractContentFromResponse(response, "REVISED_NODE_OUTPUT"))
                .isEqualTo("answer");
        assertThat(AdoptOptimizer.extractContentFromResponse(response, "MISSING")).isNull();
    }

    private static LLMCall llmCall(String id) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        return new LLMCall("unit-model", model, "system {{query}}", "user {{query}}",
                false, true, id);
    }

    private static EvaluatedCase evaluatedCase(String caseId, double score) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "q-" + caseId);
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("answer", "expected-" + caseId);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("answer", "actual-" + caseId);
        return new EvaluatedCase(new Case(inputs, label, null, caseId), answer, score, "metric reason");
    }

    private static String messageText(List<BaseMessage> messages) {
        List<String> contents = new ArrayList<>();
        for (BaseMessage message : messages) {
            contents.add(message.getContentAsString());
        }
        return String.join("\n", contents);
    }

    private static final class FakeModel extends Model {
        private final Queue<String> responses = new ArrayDeque<>();
        private final List<List<BaseMessage>> invocations = new ArrayList<>();
        private final List<ModelInvokeOptions> options = new ArrayList<>();
        private int failuresBeforeNextSuccess;

        private FakeModel(String... responses) {
            super((messages, modelConfig, modelClientConfig, options) ->
                    CompletableFuture.completedFuture(new AssistantMessage("")));
            this.responses.addAll(List.of(responses));
        }

        private void failuresBeforeNextSuccess(int count) {
            failuresBeforeNextSuccess = count;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invocations.add(List.copyOf(messages));
            this.options.add(options);
            if (failuresBeforeNextSuccess > 0) {
                failuresBeforeNextSuccess -= 1;
                throw new IllegalStateException("planned failure");
            }
            String response = responses.isEmpty() ? "" : responses.remove();
            return CompletableFuture.completedFuture(new AssistantMessage(response));
        }

        private List<List<BaseMessage>> invocations() {
            return invocations;
        }

        private List<ModelInvokeOptions> options() {
            return options;
        }
    }
}
