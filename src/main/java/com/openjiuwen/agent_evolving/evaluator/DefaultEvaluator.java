// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * Uses LLM as judge to evaluate model output consistency.
 *
 * <p>Determines pass/fail and reasoning based on question/expected answer/model answer.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator.DefaultEvaluator}.
 */
public class DefaultEvaluator extends BaseEvaluator {

    private final Model model;
    private final PromptTemplate metricTemplate;
    private final PromptTemplate retryTemplate;

    public DefaultEvaluator(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            String metric
    ) {
        this.model = new Model(modelClientConfig, modelConfig);
        this.metricTemplate = PromptTemplate.builder()
                .content(EvaluatorTemplates.LLM_METRIC_TEMPLATE)
                .build()
                .format(Map.of("user_metrics", metric != null ? metric : ""));
        this.retryTemplate = PromptTemplate.builder()
                .content(EvaluatorTemplates.LLM_METRIC_RETRY_TEMPLATE)
                .build();
    }

    public DefaultEvaluator(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, "");
    }

    @Override
    public EvaluatedCase evaluate(Case caseData, Map<String, Object> predict) {
        EvaluatedCase evaluatedCase = EvaluatedCase.builder()
                .caseData(caseData)
                .answer(predict)
                .build();
        try {
            AssistantMessage response = invokeModel(formatPrimaryMessages(caseData, predict));
            Map<String, Object> evaluatedResult = extractEvaluateResult(
                    response != null ? response.getContentAsString() : "",
                    caseData,
                    predict
            );
            if (evaluatedResult == null) {
                evaluatedCase.setReason("Failed to evaluate case due to parsing error");
                return evaluatedCase;
            }
            evaluatedCase.setScore(isPassResult(evaluatedResult.get("result")) ? 1.0 : 0.0);
            evaluatedCase.setReason(String.valueOf(evaluatedResult.getOrDefault("reason", "")));
            return evaluatedCase;
        } catch (Exception e) {
            evaluatedCase.setReason("Failed to evaluate case due to model error");
            return evaluatedCase;
        }
    }

    protected AssistantMessage invokeModel(List<?> messages) throws Exception {
        return model.invoke(messages, null, null, null, null, null, null, null, null, null);
    }

    protected List<?> formatPrimaryMessages(Case caseData, Map<String, Object> predict) {
        return metricTemplate.format(Map.of(
                "question", String.valueOf(caseData.getInputs()),
                "expected_answer", String.valueOf(caseData.getLabel()),
                "model_answer", String.valueOf(predict)
        )).toMessages();
    }

    protected List<?> formatRetryMessages(String response, Case caseData, Map<String, Object> predict) {
        return retryTemplate.format(Map.of(
                "question", String.valueOf(caseData.getInputs()),
                "expected_answer", String.valueOf(caseData.getLabel()),
                "model_answer", String.valueOf(predict),
                "nonstandard_evaluated_result", response
        )).toMessages();
    }

    protected Map<String, Object> extractEvaluateResult(String response, Case caseData, Map<String, Object> predict) {
        Map<String, Object> evaluatedResult = TuneUtils.parseJsonObjectFromLlmResponse(response);
        if (evaluatedResult != null
                && evaluatedResult.containsKey("result")
                && evaluatedResult.containsKey("reason")) {
            return evaluatedResult;
        }
        try {
            AssistantMessage retry = invokeModel(formatRetryMessages(response, caseData, predict));
            return TuneUtils.parseJsonObjectFromLlmResponse(retry != null ? retry.getContentAsString() : "");
        } catch (Exception e) {
            return null;
        }
    }

    protected boolean isPassResult(Object result) {
        if (Boolean.TRUE.equals(result)) {
            return true;
        }
        if (result instanceof String) {
            return "true".equalsIgnoreCase(((String) result).trim());
        }
        return false;
    }
}
