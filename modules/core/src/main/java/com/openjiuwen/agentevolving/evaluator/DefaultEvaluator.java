/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.agentevolving.TuneUtils;
import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * Uses LLM as judge to evaluate model output consistency.
 * <p>
 * Determines pass/fail and reasoning based on question/expected answer/model answer.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator.DefaultEvaluator}.
 * 
 * @since 0.1.7
 */
public class DefaultEvaluator extends BaseEvaluator {
    private final Model model;
    private final PromptTemplate metricTemplate;
    private final PromptTemplate retryTemplate;

    /**
     * DefaultEvaluator.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @param metric metric
     * @since 0.1.7
     */
    public DefaultEvaluator(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig, String metric) {
        this.model = new Model(modelClientConfig, modelConfig);
        this.metricTemplate = PromptTemplate.builder().content(EvaluatorTemplates.LLM_METRIC_TEMPLATE).build()
                .format(Map.of("user_metrics", metric != null ? metric : ""));
        this.retryTemplate = PromptTemplate.builder().content(EvaluatorTemplates.LLM_METRIC_RETRY_TEMPLATE).build();
    }

    /**
     * DefaultEvaluator.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @since 0.1.7
     */
    public DefaultEvaluator(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, "");
    }

    /**
     * evaluate.
     * 
     * @param caseData caseData
     * @param predict predict
     * @return the result
     * @since 0.1.7
     */
    @Override
    public EvaluatedCase evaluate(Case caseData, Map<String, Object> predict) {
        EvaluatedCase evaluatedCase = EvaluatedCase.builder().caseData(caseData).answer(predict).build();
        try {
            AssistantMessage response = invokeModel(formatPrimaryMessages(caseData, predict));
            Map<String, Object> evaluatedResult =
                extractEvaluateResult(response != null ? response.getContentAsString() : "", caseData, predict);
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

    /**
     * invokeModel.
     * 
     * @param messages messages
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    protected AssistantMessage invokeModel(List<?> messages) throws Exception {
        return model.invoke(messages, null, null, null, null, null, null, null, null, null);
    }

    /**
     * formatPrimaryMessages.
     * 
     * @param caseData caseData
     * @param predict predict
     * @return the result
     * @since 0.1.7
     */
    protected List<?> formatPrimaryMessages(Case caseData, Map<String, Object> predict) {
        return metricTemplate.format(Map.of("question", String.valueOf(caseData.getInputs()), "expected_answer",
                String.valueOf(caseData.getLabel()), "model_answer", String.valueOf(predict))).toMessages();
    }

    /**
     * formatRetryMessages.
     * 
     * @param response response
     * @param caseData caseData
     * @param predict predict
     * @return the result
     * @since 0.1.7
     */
    protected List<?> formatRetryMessages(String response, Case caseData, Map<String, Object> predict) {
        return retryTemplate.format(Map.of("question", String.valueOf(caseData.getInputs()), "expected_answer",
                String.valueOf(caseData.getLabel()), "model_answer", String.valueOf(predict),
                "nonstandard_evaluated_result", response)).toMessages();
    }

    /**
     * extractEvaluateResult.
     * 
     * @param response response
     * @param caseData caseData
     * @param predict predict
     * @return the result
     * @since 0.1.7
     */
    protected Map<String, Object> extractEvaluateResult(String response, Case caseData, Map<String, Object> predict) {
        Map<String, Object> evaluatedResult = TuneUtils.parseJsonObjectFromLlmResponse(response);
        if (evaluatedResult != null && evaluatedResult.containsKey("result") && evaluatedResult.containsKey("reason")) {
            return evaluatedResult;
        }
        try {
            AssistantMessage retry = invokeModel(formatRetryMessages(response, caseData, predict));
            return TuneUtils.parseJsonObjectFromLlmResponse(retry != null ? retry.getContentAsString() : "");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * isPassResult.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
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
