/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code InstructionOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/instruction_optimizer.py}.
 */
class InstructionOptimizerTest {

    @Test
    void backwardWritesTextualGradientForUnfrozenPromptsAndCapsBadCases() {
        FakeModel model = new FakeModel("<INS>tighten instruction</INS>");
        LLMCall llmCall = llmCall("both", false, true);
        InstructionOptimizer optimizer = new InstructionOptimizer(model, Map.of("main", llmCall));
        List<EvaluatedCase> cases = evaluatedCases(12);

        optimizer.backward(cases);

        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(parameter.getGradient("system_prompt")).isEqualTo("<INS>tighten instruction</INS>");
        assertThat(parameter.getGradient("user_prompt")).isNull();
        assertThat(optimizer.getBadCases()).hasSize(TuneConstant.DEFAULT_MAX_SAMPLED_EXAMPLE_NUM);
        assertThat(model.invocations()).hasSize(1);
        assertThat(model.invocations().get(0).get(0).getContentAsString()).contains("<bad_cases>");
    }

    @Test
    void updateOptimizesBothSystemAndUserPromptsWhenBothMutable() {
        FakeModel model = new FakeModel(
                "<SYSTEM_PROMPT_OPTIMIZED>Better system {{query}}</SYSTEM_PROMPT_OPTIMIZED>"
                        + "<USER_PROMPT_OPTIMIZED>Better user {{query}}</USER_PROMPT_OPTIMIZED>"
        );
        LLMCall llmCall = llmCall("both", false, false);
        InstructionOptimizer optimizer = new InstructionOptimizer(model, Map.of("main", llmCall));
        optimizer.parameters().get("main").setGradient("system_prompt", "<INS>gradient</INS>");

        optimizer.update();

        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("Better system {{query}}");
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()))
                .isEqualTo("Better user {{query}}");
    }

    @Test
    void updateOptimizesUserPromptWithSystemGradientWhenSystemFrozen() {
        FakeModel model = new FakeModel("<PROMPT_OPTIMIZED>Better user {{query}}</PROMPT_OPTIMIZED>");
        LLMCall llmCall = llmCall("user-only", true, false);
        InstructionOptimizer optimizer = new InstructionOptimizer(model, Map.of("main", llmCall));
        optimizer.parameters().get("main").setGradient("system_prompt", "<INS>gradient</INS>");

        optimizer.update();

        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("system {{query}}");
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()))
                .isEqualTo("Better user {{query}}");
    }

    @Test
    void missingPlaceholderRestoreAppendsStillMissingPlaceholder() {
        FakeModel model = new FakeModel(
                "<PROMPT_OPTIMIZED>Hello there</PROMPT_OPTIMIZED>",
                "Hello there"
        );
        LLMCall llmCall = llmCall("system-only", false, true);
        InstructionOptimizer optimizer = new InstructionOptimizer(model, Map.of("main", llmCall));
        optimizer.parameters().get("main").setGradient("system_prompt", "<INS>gradient</INS>");

        optimizer.update();

        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("Hello there\n{{query}}");
    }

    @Test
    void extractOptimizedPromptRemovesPromptBaseWrapper() {
        String response = "<PROMPT_OPTIMIZED><prompt_base>Better prompt</prompt_base></PROMPT_OPTIMIZED>";

        String result = InstructionOptimizer.extractOptimizedPromptFromResponse(response, "PROMPT_OPTIMIZED");

        assertThat(result).isEqualTo("Better prompt");
        assertThat(InstructionOptimizer.extractOptimizedPromptFromResponse("missing", "PROMPT_OPTIMIZED")).isNull();
    }

    @Test
    void placeholderHelpersPreserveOriginalOrder() {
        List<String> original = InstructionOptimizer.findPlaceholdersFromPrompt("{{a}} {{b}}");
        List<String> updated = InstructionOptimizer.findPlaceholdersFromPrompt("{{b}}");

        assertThat(InstructionOptimizer.findMissingPlaceholders(original, updated)).containsExactly("a");
    }

    private static LLMCall llmCall(String id, boolean freezeSystemPrompt, boolean freezeUserPrompt) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        return new LLMCall("unit-model", model, "system {{query}}", "user {{query}}",
                freezeSystemPrompt, freezeUserPrompt, id);
    }

    private static List<EvaluatedCase> evaluatedCases(int count) {
        List<EvaluatedCase> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", "q" + index);
            Map<String, Object> label = new LinkedHashMap<>();
            label.put("answer", "a" + index);
            Case caseValue = new Case(inputs, label, null, "case-" + index);
            result.add(new EvaluatedCase(caseValue, Map.of("answer", "actual" + index), 0.0d, "reason" + index));
        }
        return result;
    }

    private static final class FakeModel extends Model {
        private final Queue<String> responses = new ArrayDeque<>();
        private final List<List<BaseMessage>> invocations = new ArrayList<>();

        private FakeModel(String... responses) {
            super((messages, modelConfig, modelClientConfig, options) ->
                    CompletableFuture.completedFuture(new AssistantMessage("")));
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
}
