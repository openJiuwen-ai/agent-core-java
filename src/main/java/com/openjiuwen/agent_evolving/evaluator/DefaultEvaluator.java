/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
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
 * LLM-as-judge evaluator for answer consistency.
 *
 * <p>Mirrors Python's {@code DefaultEvaluator} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator.py}.</p>
 */
public class DefaultEvaluator extends BaseEvaluator {

    private final Model model;
    private final PromptTemplate metricTemplate;

    public DefaultEvaluator(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, "");
    }

    public DefaultEvaluator(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig, String metric) {
        this(new Model(modelClientConfig, modelConfig), metric);
    }

    DefaultEvaluator(Model model, String metric) {
        this.model = Objects.requireNonNull(model, "model");
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("user_metrics", metric == null ? "" : metric);
        this.metricTemplate = EvaluatorTemplates.LLM_METRIC_TEMPLATE.format(keyword);
    }

    @Override
    public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
        List<BaseMessage> messages = metricTemplate.format(metricKeywords(caseValue, predict)).toMessages();
        EvaluatedCase evaluatedCase = new EvaluatedCase(caseValue, predict, 0.0d, "", null);
        String response;
        try {
            response = invokeModel(messages);
        } catch (RuntimeException exception) {
            evaluatedCase.setReason("Failed to evaluate case due to model error");
            return evaluatedCase;
        }

        Map<String, Object> evaluatedResult = extractEvaluateResult(response, caseValue, predict);
        if (evaluatedResult == null || evaluatedResult.isEmpty()) {
            evaluatedCase.setReason("Failed to evaluate case due to parsing error");
            return evaluatedCase;
        }
        evaluatedCase.setScore(isPassResult(evaluatedResult.get("result")) ? 1.0d : 0.0d);
        Object reason = evaluatedResult.get("reason");
        evaluatedCase.setReason(reason == null ? "" : String.valueOf(reason));
        return evaluatedCase;
    }

    Map<String, Object> extractEvaluateResult(String response, Case caseValue, Map<String, Object> predict) {
        Object parsed = TuneUtils.parseJsonFromLlmResponse(response);
        if (parsed instanceof Map<?, ?> map && map.containsKey("result") && map.containsKey("reason")) {
            return toStringObjectMap(map);
        }

        List<BaseMessage> messages = EvaluatorTemplates.LLM_METRIC_RETRY_TEMPLATE
                .format(retryKeywords(caseValue, predict, response))
                .toMessages();
        String retryResponse;
        try {
            retryResponse = invokeModel(messages);
        } catch (RuntimeException exception) {
            return null;
        }
        Object retryParsed = TuneUtils.parseJsonFromLlmResponse(retryResponse);
        if (retryParsed instanceof Map<?, ?> retryMap) {
            return toStringObjectMap(retryMap);
        }
        return null;
    }

    boolean isPassResult(Object result) {
        if (Boolean.TRUE.equals(result)) {
            return true;
        }
        if (result instanceof String text) {
            return "true".equalsIgnoreCase(text.strip());
        }
        return false;
    }

    private String invokeModel(List<BaseMessage> messages) {
        CompletionStage<AssistantMessage> stage = model.invoke(messages);
        AssistantMessage assistantMessage = stage.toCompletableFuture().join();
        return assistantMessage == null ? "" : assistantMessage.getContentAsString();
    }

    private static Map<String, Object> metricKeywords(Case caseValue, Map<String, Object> predict) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("question", pythonString(caseValue.getInputs()));
        keywords.put("expected_answer", pythonString(caseValue.getLabel()));
        keywords.put("model_answer", pythonString(predict));
        return keywords;
    }

    private static Map<String, Object> retryKeywords(Case caseValue, Map<String, Object> predict, String response) {
        Map<String, Object> keywords = metricKeywords(caseValue, predict);
        keywords.put("nonstandard_evaluated_result", response);
        return keywords;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
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
