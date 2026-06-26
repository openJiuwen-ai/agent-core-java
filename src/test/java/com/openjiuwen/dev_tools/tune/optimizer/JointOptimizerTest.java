/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Mirrors Python's {@code JointOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/joint_optimizer.py}.
 */
class JointOptimizerTest {

    private static final Queue<String> MODEL_RESPONSES = new ArrayDeque<>();

    @BeforeAll
    static void registerModelInvoker() {
        Model.registerInvoker(ProviderType.OPEN_ROUTER.getValue(), (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(nextModelResponse())));
    }

    @BeforeEach
    void resetModelResponses() {
        MODEL_RESPONSES.clear();
    }

    @Test
    void constructorAndBindParameterSynchronizeParentAndChildOptimizers() {
        LLMCall first = llmCall("first", false, true);
        JointOptimizer optimizer = new JointOptimizer(
                modelConfig(),
                modelClientConfig(),
                Map.of("first", first),
                1
        );

        LLMCall second = llmCall("second", false, true);
        optimizer.bindParameter(Map.of("second", second));

        assertThat(optimizer.parameters()).containsKeys("first", "second");
        assertThat(optimizer.getInstructionOptimizer().parameters()).containsKeys("first", "second");
        assertThat(optimizer.getExampleOptimizer().parameters()).containsKeys("first", "second");
        assertThat(optimizer.getInstructionOptimizer().parameters().get("second"))
                .isNotSameAs(optimizer.parameters().get("second"));
    }

    @Test
    void backwardInstructionStrategyCopiesInstructionGradientsToParentParameters() {
        MODEL_RESPONSES.add("<INS>instruction gradient</INS>");
        LLMCall llmCall = llmCall("instruction", false, false);
        JointOptimizer optimizer = optimizer(Map.of("main", llmCall), 1, true);

        optimizer.backward(evaluatedCases(2, 1));

        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(optimizer.isOptimizeInstruction()).isTrue();
        assertThat(parameter.getGradient("system_prompt")).isEqualTo("<INS>instruction gradient</INS>");
        assertThat(parameter.getGradient("user_prompt")).isEqualTo("<INS>instruction gradient</INS>");
    }

    @Test
    void backwardExampleStrategyCopiesExampleGradientsToParentParameters() {
        LLMCall llmCall = llmCall("example", false, false);
        JointOptimizer optimizer = optimizer(Map.of("main", llmCall), 1, false);

        optimizer.backward(evaluatedCases(2, 1));

        TextualParameter parameter = optimizer.parameters().get("main");
        assertThat(optimizer.isOptimizeInstruction()).isFalse();
        assertThat(parameter.getGradient("system_prompt")).contains("example 1:");
        assertThat(parameter.getGradient("system_prompt")).contains("q0");
        assertThat(parameter.getGradient("user_prompt")).isEqualTo(parameter.getGradient("system_prompt"));
    }

    @Test
    void updateInstructionStrategyMergesInstructionPromptsAndExampleGradient() {
        MODEL_RESPONSES.add("<INS>instruction gradient</INS>");
        MODEL_RESPONSES.add("<SYSTEM_PROMPT_OPTIMIZED>Better system {{query}}</SYSTEM_PROMPT_OPTIMIZED>"
                + "<USER_PROMPT_OPTIMIZED>Better user {{query}}</USER_PROMPT_OPTIMIZED>");
        LLMCall llmCall = llmCall("update-instruction", false, false);
        JointOptimizer optimizer = optimizer(Map.of("main", llmCall), 1, true);
        List<EvaluatedCase> cases = evaluatedCases(2, 1);

        optimizer.backward(cases);
        optimizer.update();

        String expectedExamples = TuneUtils.convertCasesToExamples(List.of(cases.get(0).getCase()));
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("Better system {{query}}");
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()))
                .isEqualTo("Better user {{query}}\n" + expectedExamples);
    }

    @Test
    void updateExampleStrategyAddsExamplesToSystemWhenUserPromptFrozen() {
        LLMCall llmCall = llmCall("update-example", false, true);
        JointOptimizer optimizer = optimizer(Map.of("main", llmCall), 1, false);
        List<EvaluatedCase> cases = evaluatedCases(2, 1);

        optimizer.backward(cases);
        optimizer.update();

        String expectedExamples = TuneUtils.convertCasesToExamples(List.of(cases.get(0).getCase()));
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()))
                .isEqualTo("system {{query}}\n" + expectedExamples);
        assertThat(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()))
                .isEqualTo("user {{query}}");
    }

    @Test
    void selectOptimizeStrategyForcesInstructionWhenNoExamplesAreRequested() {
        JointOptimizer optimizer = optimizer(Map.of("main", llmCall("no-examples", false, false)), 0, false);

        optimizer.selectOptimizeStrategy();

        assertThat(optimizer.isOptimizeInstruction()).isTrue();
    }

    private static JointOptimizer optimizer(Map<String, LLMCall> parameters,
                                            int numExamples,
                                            boolean chooseInstruction) {
        return new JointOptimizer(
                modelConfig(),
                modelClientConfig(),
                parameters,
                numExamples,
                () -> chooseInstruction
        );
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
        return new LLMCall("unit-model", model, "system {{query}}", "user {{query}}",
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

    private static String nextModelResponse() {
        return MODEL_RESPONSES.isEmpty() ? "<INS>default gradient</INS>" : MODEL_RESPONSES.remove();
    }
}
