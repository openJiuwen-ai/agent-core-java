/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.evaluator.EvaluatorTemplates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * Uses Model as judge to perform semantic consistency check.
 *
 * <p>Returns 1.0 if consistent, 0.0 otherwise.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.metrics.llm_as_judge.LLMAsJudgeMetric}.
 */
public class LLMAsJudgeMetric extends Metric {

    private final Model model;
    private final PromptTemplate template;

    /**
     * Create LLM-as-Judge metric.
     *
     * @param modelConfig       Model request configuration
     * @param modelClientConfig Model client configuration
     * @param userMetrics       Custom user metrics
     */
    public LLMAsJudgeMetric(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            String userMetrics
    ) {
        this.model = new Model(modelClientConfig, modelConfig);
        this.template = PromptTemplate.builder()
                .content(EvaluatorTemplates.LLM_METRIC_TEMPLATE)
                .build()
                .format(Map.of("user_metrics", userMetrics != null ? userMetrics : ""));
    }

    /**
     * Create with default user metrics.
     *
     * @param modelConfig       Model request configuration
     * @param modelClientConfig Model client configuration
     */
    public LLMAsJudgeMetric(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, "");
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
    public Double compute(Object prediction, Object label, Map<String, Object> kwargs) {
        List<?> messages = formatMessages(prediction, label, kwargs);
        try {
            AssistantMessage response = invokeModel(messages);
            return parseResult(response != null ? response.getContentAsString() : "");
        } catch (Exception e) {
            return 0.0;
        }
    }

    protected List<?> formatMessages(Object prediction, Object label, Map<String, Object> kwargs) {
        Object question = kwargs != null ? kwargs.get("question") : null;
        return template.format(Map.of(
                "question", pythonStringOrEmpty(question),
                "expected_answer", pythonString(label),
                "model_answer", pythonString(prediction)
        )).toMessages();
    }

    protected AssistantMessage invokeModel(List<?> messages) throws Exception {
        return model.invoke(messages, null, null, null, null, null, null, null, null, null);
    }

    private double parseResult(String response) {
        Map<String, Object> data = TuneUtils.parseJsonObjectFromLlmResponse(response);
        if (data == null) {
            return 0.0;
        }

        Object result = data.get("result");
        if (Boolean.TRUE.equals(result)) {
            return 1.0;
        }
        if (result instanceof String) {
            return "true".equalsIgnoreCase(((String) result).trim()) ? 1.0 : 0.0;
        }
        return 0.0;
    }

    private static String pythonStringOrEmpty(Object value) {
        return isPythonTruthy(value) ? pythonString(value) : "";
    }

    private static boolean isPythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String pythonString(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        return String.valueOf(value);
    }
}
