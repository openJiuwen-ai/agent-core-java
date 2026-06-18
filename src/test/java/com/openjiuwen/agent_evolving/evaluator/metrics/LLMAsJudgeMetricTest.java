/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code LLMAsJudgeMetric} in
 * {@code openjiuwen/agent_evolving/evaluator/metrics/llm_as_judge.py}, with parity cases from
 * {@code tests/unit_tests/agent_evolving/evaluator/test_metrics/test_llm_as_judge.py}.
 */
class LLMAsJudgeMetricTest {

    @Test
    void computeReturnsZeroOnModelInvocationError() {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> failed("model error"));
        LLMAsJudgeMetric metric = new LLMAsJudgeMetric(model, "");

        assertEquals(0.0d, metric.compute("prediction", "label", Map.of("question", "question")));
    }

    @Test
    void computeReturnsOneWhenLlmResultIsTrue() {
        LLMAsJudgeMetric metric = metricReturning(makeResponse("true"));

        assertEquals(1.0d, metric.compute("prediction", "label", Map.of("question", "question")));
    }

    @Test
    void computeReturnsZeroWhenLlmResultIsFalse() {
        LLMAsJudgeMetric metric = metricReturning(makeResponse("false"));

        assertEquals(0.0d, metric.compute("prediction", "label", Map.of("question", "question")));
    }

    @Test
    void computeUsesEmptyQuestionWhenQuestionIsNull() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LLMAsJudgeMetric metric = metricReturning(makeResponse("true"), prompt);
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("question", null);

        assertEquals(1.0d, metric.compute("prediction", "label", kwargs));
        String content = prompt.get();
        assertNotNull(content);
        assertTrue(content.contains("[Question]: \n"));
        assertFalse(content.contains("[Question]: None"));
    }

    @Test
    void computeParsesStringTrueResult() {
        LLMAsJudgeMetric metric = metricReturning(makeResponse("\"true\""));

        assertEquals(1.0d, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeReturnsZeroOnInvalidJsonResponse() {
        LLMAsJudgeMetric metric = metricReturning("not json");

        assertEquals(0.0d, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeParsesStringFalseResultAsZero() {
        LLMAsJudgeMetric metric = metricReturning(makeResponse("\"false\""));

        assertEquals(0.0d, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeHandlesWhitespaceInStringResult() {
        LLMAsJudgeMetric metric = metricReturning(makeResponse("\"  true  \""));

        assertEquals(1.0d, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void namePropertyMatchesPythonContract() {
        assertEquals("llm_as_judge", metricReturning(makeResponse("true")).getName());
    }

    @Test
    void higherIsBetterReturnsTrue() {
        assertTrue(metricReturning(makeResponse("true")).isHigherIsBetter());
    }

    @Test
    void constructorWithEmptyMetricsBuildsTemplateAndModel() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LLMAsJudgeMetric metric = metricReturning(makeResponse("true"), prompt);

        assertNotNull(metric);
        assertEquals(1.0d, metric.compute("prediction", "label", Map.of("question", "question")));
        assertNotNull(prompt.get());
        assertTrue(prompt.get().contains("The following are custom verification rules added by the user"));
        assertTrue(prompt.get().contains("[Expected Answer]: label"));
        assertTrue(prompt.get().contains("[Model Response]: prediction"));
    }

    @Test
    void constructorWithCustomMetricsFormatsPrompt() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LLMAsJudgeMetric metric = metricReturning(makeResponse("true"), prompt, "custom_metric");

        assertEquals(1.0d, metric.compute("prediction", "label", Map.of("question", "question")));
        assertNotNull(prompt.get());
        assertTrue(prompt.get().contains("custom_metric"));
    }

    private static LLMAsJudgeMetric metricReturning(String response) {
        return metricReturning(response, new AtomicReference<>());
    }

    private static LLMAsJudgeMetric metricReturning(String response, AtomicReference<String> capturedPrompt) {
        return metricReturning(response, capturedPrompt, "");
    }

    private static LLMAsJudgeMetric metricReturning(
            String response,
            AtomicReference<String> capturedPrompt,
            String userMetrics
    ) {
        List<List<BaseMessage>> capturedMessages = new ArrayList<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(messages);
            if (!messages.isEmpty()) {
                capturedPrompt.set(messages.get(0).getContentAsString());
            }
            return CompletableFuture.completedFuture(new AssistantMessage(response));
        });
        return new LLMAsJudgeMetric(model, userMetrics);
    }

    private static CompletableFuture<AssistantMessage> failed(String message) {
        CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException(message));
        return failed;
    }

    private static String makeResponse(String resultValue) {
        return "```json\n{\"result\": " + resultValue + "}\n```";
    }
}
