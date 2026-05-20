/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import com.openjiuwen.agentevolving.TuneUtils;
import com.openjiuwen.agentevolving.evaluator.EvaluatorTemplates;
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "llm_as_judge";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHigherIsBetter() {
        return true;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Double compute(Object prediction, Object label, Map<String, Object> kwargs) {
        Object question = kwargs != null ? kwargs.get("question") : null;
        List<?> messages = template.format(Map.of(
                "question", String.valueOf(question != null ? question : ""),
                "expected_answer", String.valueOf(label),
                "model_answer", String.valueOf(prediction)
        )).toMessages();
        try {
            AssistantMessage response = invokeModel(messages);
            return parseResult(response != null ? response.getContentAsString() : "");
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
}
