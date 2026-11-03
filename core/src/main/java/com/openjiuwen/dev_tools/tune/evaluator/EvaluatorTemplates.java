/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.evaluator;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

/**
 * Prompt templates used by tune evaluator LLM-as-judge flows.
 *
 * <p>Mirrors Python's {@code LLM_METRIC_TEMPLATE} and {@code LLM_METRIC_RETRY_TEMPLATE} in
 * {@code openjiuwen/dev_tools/tune/evaluator/evaluator.py}.</p>
 */
public final class EvaluatorTemplates {

    public static final String LLM_METRIC_TEMPLATE_CONTENT = """
            你是一个答案校验专家，负责校验给定的模型回答和标准答案之间的含义和结论一致性。请根据以下标准判断模型回答是否与标准答案的含义和结论一致。

            - 如果模型回答和标准答案含义一致，返回`true`。
            - 如果模型回答和标准答案含义不一致，返回`false`。
            - 注意区分对话和工具调用，两者通常不能按语意判断为一致
            - 结合用户问题和标准答案，简要分析模型回答和标准答案不一致的理由

            以下是用户补充的自定义校验规则，如果与上述规则冲突，则优先遵从用户自定义规则，请严格遵守：
            {{user_metrics}}

            输出JSON格式：
            ```json
            {
            “result”: true/false,
            "reason": "校验理由"
            }
            ```

            [问题]：{{question}}

            以下是需要比对的模型回答和标准答案：
            [标准答案]：{{expected_answer}}

            [模型回答]：{{model_answer}}

            请校验并返回结果：
            """;

    public static final String LLM_METRIC_RETRY_TEMPLATE_CONTENT = """
            你是一个答案校验专家，负责修复不规范的评估结果。

            ## 原始待评估结果评估
            [问题]：{{question}}
            以下是需要比对的模型回答和标准答案：
            [标准答案]：{{expected_answer}}
            [模型回答]：{{model_answer}}

            ## 格式不规范的评估结果
            但是当前收到了不规范的评估结果，导致无法正确解析成json格式：
            <EVALUATED_RESULT>
            {{nonstandard_evaluated_result}}
            </EVALUATED_RESULT>

            ## 格式修复
            请修正当前评估结果的格式，推理为什么上面的评估结果没有被json解析出来，修正并返回正确的评估格式，如下
            输出JSON格式：
            ```json
            {
            “result”: true/false,
            "reason": "校验理由"
            }
            ```
            ## 要求
            - 生成的json必须被```json```包裹
            - 注意评估结果中是否存在不规范的引号使用，例如双引号与单引号生成错误、引号嵌套等问题

            请校验并返回结果：
            """;

    public static final PromptTemplate LLM_METRIC_TEMPLATE = PromptTemplate.builder()
            .content(LLM_METRIC_TEMPLATE_CONTENT)
            .build();

    public static final PromptTemplate LLM_METRIC_RETRY_TEMPLATE = PromptTemplate.builder()
            .content(LLM_METRIC_RETRY_TEMPLATE_CONTENT)
            .build();

    private EvaluatorTemplates() {
    }
}
