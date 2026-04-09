  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.dev_tools.tune.evaluator;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.Map;
import java.util.Optional;

/**
 * Default evaluator using an LLM to judge predictions.
 *
 * <p>Mirrors Python's {@code DefaultEvaluator} in
 * {@code openjiuwen.dev_tools.tune.evaluator.evaluator}.
 */
public class DefaultEvaluator extends BaseEvaluator {

    private final Model model;
    private final PromptTemplate metricTemplate;
    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final String metric;

    public DefaultEvaluator(ModelRequestConfig modelConfig,
                            ModelClientConfig modelClientConfig,
                            String metric) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.metric = metric != null ? metric : "";
        this.model = new Model(modelClientConfig, modelConfig);
        this.metricTemplate = buildMetricTemplate(this.metric);
    }

    public DefaultEvaluator(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, "");
    }

    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public String getMetric() {
        return metric;
    }

    @Override
    public EvaluatedCase evaluate(Case case_, Map<String, Object> predict) {
        EvaluatedCase evaluatedCase = EvaluatedCase.builder()
                .case_(case_)
                .answer(predict)
                .score(0.0f)
                .reason("")
                .build();

        try {
            var messages = metricTemplate.format(Map.of(
                    "question", String.valueOf(case_.getInputs()),
                    "expected_answer", String.valueOf(case_.getLabel()),
                    "model_answer", String.valueOf(predict)
            )).toMessages();
            AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
            Optional<Map<String, Object>> evaluatedResult = TuneUtils.parseJsonFromLlmResponse(
                    response != null ? response.getContentAsString() : ""
            );

            if (evaluatedResult.isEmpty()) {
                evaluatedCase.setReason("Failed to evaluate case due to parsing error");
                return evaluatedCase;
            }

            Object result = evaluatedResult.get().getOrDefault("result", false);
            evaluatedCase.setReason(String.valueOf(evaluatedResult.get().getOrDefault("reason", "")));
            if (Boolean.TRUE.equals(result)
                    || (result instanceof String resultText && "true".equalsIgnoreCase(resultText.trim()))) {
                evaluatedCase.setScore(1.0f);
            }
        } catch (Exception e) {
            evaluatedCase.setReason("Failed to evaluate case due to model error: " + e.getMessage());
        }

        return evaluatedCase;
    }

    private PromptTemplate buildMetricTemplate(String metric) {
        return PromptTemplate.builder()
                .content(METRIC_TEMPLATE.formatted(metric))
                .build();
    }

    private static final String METRIC_TEMPLATE = """
            浣犳槸涓€涓瓟妗堟牎楠屼笓瀹讹紝璐熻矗鏍￠獙缁欏畾鐨勬ā鍨嬪洖绛斿拰鏍囧噯绛旀涔嬮棿鐨勫惈涔夊拰缁撹涓€鑷存€с€?

            - 濡傛灉妯″瀷鍥炵瓟鍜屾爣鍑嗙瓟妗堝惈涔変竴鑷达紝杩斿洖`true`銆?
            - 濡傛灉妯″瀷鍥炵瓟鍜屾爣鍑嗙瓟妗堝惈涔変笉涓€鑷达紝杩斿洖`false`銆?

            鐢ㄦ埛鑷畾涔夋牎楠岃鍒欙細
            %s

            杈撳嚭JSON鏍煎紡锛?
            ```json
            {
              "result": true/false,
              "reason": "鏍￠獙鐞嗙敱"
            }
            ```

            [闂]锛歿{{question}}
            [鏍囧噯绛旀]锛歿{{expected_answer}}
            [妯″瀷鍥炵瓟]锛歿{{model_answer}}
            """;
}
