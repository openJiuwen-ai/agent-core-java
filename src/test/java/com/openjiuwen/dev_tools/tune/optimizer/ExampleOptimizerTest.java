/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ExampleOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/example_optimizer.py}.
 */
class ExampleOptimizerTest {

    private static final AtomicReference<String> SELECTOR_RESPONSE = new AtomicReference<>("```list\n[0]\n```");

    @BeforeAll
    static void registerModelInvoker() {
        Model.registerInvoker(ProviderType.OPEN_ROUTER.getValue(), (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(SELECTOR_RESPONSE.get())));
    }

    @BeforeEach
    void resetSelectorResponse() {
        SELECTOR_RESPONSE.set("```list\n[0]\n```");
    }

    @Test
    void constructorValidatesNumExamplesWithPythonStatusCode() {
        assertThatThrownBy(() -> new ExampleOptimizer(modelConfig(), modelClientConfig(), Map.of(), 21))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR))
                .hasMessageContaining("num_examples should be between");
    }

    @Test
    void initExamplesWritesGradientsOnlyForUnfrozenPrompts() {
        LLMCall frozenUser = llmCall("frozen-user", false, true);
        ExampleOptimizer optimizer = new ExampleOptimizer(
                modelConfig(),
                modelClientConfig(),
                Map.of("main", frozenUser),
                2
        );

        optimizer.initExamples(evaluatedCases(3, 1));

        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(parameter.getGradient("system_prompt")).contains("example 1:");
        assertThat(parameter.getGradient("user_prompt")).isNull();
    }

    @Test
    void backwardUsesModelSelectedExamplesAndUpdatePrefersUserPromptWhenBothMutable() {
        SELECTOR_RESPONSE.set("```list\n[1, 0]\n```");
        LLMCall llmCall = llmCall("both", false, false);
        ExampleOptimizer optimizer = new ExampleOptimizer(
                modelConfig(),
                modelClientConfig(),
                Map.of("main", llmCall),
                2
        );
        List<EvaluatedCase> cases = evaluatedCases(5, 4);

        optimizer.backward(cases);

        String expectedGradient = TuneUtils.convertCasesToExamples(List.of(cases.get(1).getCase(), cases.get(0).getCase()));
        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(parameter.getGradient("system_prompt")).isEqualTo(expectedGradient);
        assertThat(parameter.getGradient("user_prompt")).isEqualTo(expectedGradient);

        optimizer.update();

        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()))
                .isEqualTo("user {{query}}\n" + expectedGradient);
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("system");
    }

    @Test
    void updateFallsBackToSystemPromptWhenUserPromptFrozen() {
        LLMCall llmCall = llmCall("system-only", false, true);
        ExampleOptimizer optimizer = new ExampleOptimizer(
                modelConfig(),
                modelClientConfig(),
                Map.of("main", llmCall),
                1
        );
        optimizer.parameters().get("main").setGradient("system_prompt", "system gradient");

        optimizer.update();

        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("system\nsystem gradient");
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()))
                .isEqualTo("user {{query}}");
    }

    @Test
    void invalidSelectorResponseFallsBackToLocalSampling() {
        SELECTOR_RESPONSE.set("invalid response");
        LLMCall llmCall = llmCall("fallback", false, false);
        ExampleOptimizer optimizer = new ExampleOptimizer(
                modelConfig(),
                modelClientConfig(),
                Map.of("main", llmCall),
                2
        );
        List<EvaluatedCase> cases = evaluatedCases(4, 1);

        optimizer.backward(cases);

        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(parameter.getGradient("user_prompt")).contains("example 1:");
        assertThat(parameter.getGradient("user_prompt")).contains("q0");
    }

    @Test
    void samplingIncludesBadCasesBeforeFillingFromRemainingDataset() {
        ExampleOptimizer optimizer = new ExampleOptimizer(modelConfig(), modelClientConfig(), Map.of(), 2);
        List<EvaluatedCase> cases = evaluatedCases(4, 1);

        List<Case> sampled = optimizer.sampleExample(2, cases);

        assertThat(sampled).hasSize(2);
        assertThat(sampled).extracting(Case::getCaseId).contains("case-0");
    }

    private static ModelRequestConfig modelConfig() {
        return ModelRequestConfig.builder().modelName("unit-model").build();
    }

    private static ModelClientConfig modelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_ROUTER)
                .apiKey("test")
                .build();
    }

    private static LLMCall llmCall(String id, boolean freezeSystemPrompt, boolean freezeUserPrompt) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        return new LLMCall("unit-model", model, "system", "user {{query}}",
                freezeSystemPrompt, freezeUserPrompt, id);
    }

    private static List<EvaluatedCase> evaluatedCases(int count, int badCount) {
        List<EvaluatedCase> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", "q" + index);
            Map<String, Object> label = new LinkedHashMap<>();
            label.put("answer", "a" + index);
            Case caseValue = new Case(inputs, label, null, "case-" + index);
            double score = index < badCount ? 0.0d : 1.0d;
            result.add(new EvaluatedCase(caseValue, Map.of("answer", "actual" + index), score, ""));
        }
        return result;
    }
}
