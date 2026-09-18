/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import com.openjiuwen.agentevolving.TuneUtils;
import com.openjiuwen.agentevolving.evaluator.EvaluatorTemplates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Uses an LLM judge to score semantic consistency between prediction and label.
 *
 * <p>Mirrors Python's {@code LLMAsJudgeMetric} in
 * {@code openjiuwen/agent_evolving/evaluator/metrics/llm_as_judge.py}.</p>
 */
public class LLMAsJudgeMetric extends Metric {

    private final Model model;
    private final PromptTemplate template;

    public LLMAsJudgeMetric(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, "");
    }

    public LLMAsJudgeMetric(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            String userMetrics
    ) {
        this(new Model(modelClientConfig, modelConfig), userMetrics);
    }

    LLMAsJudgeMetric(Model model, String userMetrics) {
        this.model = Objects.requireNonNull(model, "model");
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("user_metrics", userMetrics == null ? "" : userMetrics);
        this.template = EvaluatorTemplates.LLM_METRIC_TEMPLATE.format(keywords);
    }

    @Override
    public String getName() {
        return "llm_as_judge";
    }

    @Override
    public boolean isHigherIsBetter() {
        return true;
    }

    @Override
    public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
        List<BaseMessage> messages = template.format(metricKeywords(prediction, label, kwargs)).toMessages();
        String response;
        try {
            response = invokeModel(messages);
        } catch (RuntimeException exception) {
            return 0.0d;
        }
        return parseResult(response);
    }

    double parseResult(String response) {
        if (response == null) {
            return 0.0d;
        }
        Object parsed = TuneUtils.parseJsonFromLlmResponse(response);
        if (!(parsed instanceof Map<?, ?> data)) {
            return 0.0d;
        }
        Object result = data.get("result");
        if (Boolean.TRUE.equals(result)) {
            return 1.0d;
        }
        if (result instanceof String text) {
            return "true".equalsIgnoreCase(text.strip()) ? 1.0d : 0.0d;
        }
        return 0.0d;
    }

    private String invokeModel(List<BaseMessage> messages) {
        CompletionStage<AssistantMessage> stage = model.invoke(messages);
        AssistantMessage assistantMessage = stage.toCompletableFuture().join();
        return assistantMessage == null ? "" : assistantMessage.getContentAsString();
    }

    private static Map<String, Object> metricKeywords(Object prediction, Object label, Map<String, Object> kwargs) {
        Object question = kwargs == null ? null : kwargs.get("question");
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("question", pythonTruthy(question) ? pythonString(question) : "");
        keywords.put("expected_answer", pythonString(label));
        keywords.put("model_answer", pythonString(prediction));
        return keywords;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private static String pythonString(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> pythonRepr(entry.getKey()) + ": " + pythonRepr(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                parts.add(pythonRepr(iterator.next()));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<String> parts = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                parts.add(pythonRepr(Array.get(value, index)));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        return String.valueOf(value);
    }

    private static String pythonRepr(Object value) {
        if (value instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Character character) {
            String text = character.toString();
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        return pythonString(value);
    }
}
