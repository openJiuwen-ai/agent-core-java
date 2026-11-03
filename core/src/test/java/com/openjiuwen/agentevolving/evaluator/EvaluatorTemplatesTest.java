/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.templates} in
 * {@code openjiuwen/agent_evolving/evaluator/templates.py}.
 */
class EvaluatorTemplatesTest {

    @Test
    void metricTemplatePreservesPlaceholdersAndJsonFence() {
        String content = EvaluatorTemplates.LLM_METRIC_TEMPLATE_CONTENT;

        assertThat(content).contains(
                "{{user_metrics}}",
                "{{question}}",
                "{{expected_answer}}",
                "{{model_answer}}",
                "```json",
                "\"result\": true/false",
                "Please verify and return the result:"
        );
        assertThat(EvaluatorTemplates.LLM_METRIC_TEMPLATE.getContent()).isEqualTo(content);
    }

    @Test
    void retryTemplatePreservesNonstandardResultPlaceholder() {
        String content = EvaluatorTemplates.LLM_METRIC_RETRY_TEMPLATE_CONTENT;

        assertThat(content).contains(
                "{{question}}",
                "{{expected_answer}}",
                "{{model_answer}}",
                "{{nonstandard_evaluated_result}}",
                "<EVALUATED_RESULT>",
                "</EVALUATED_RESULT>",
                "The generated JSON must be wrapped with ```json```"
        );
        assertThat(EvaluatorTemplates.LLM_METRIC_RETRY_TEMPLATE.getContent()).isEqualTo(content);
    }

    @Test
    void promptTemplateFormattingMatchesPythonUsage() {
        PromptTemplate formatted = EvaluatorTemplates.LLM_METRIC_TEMPLATE.format(Map.of(
                "user_metrics", "custom_metric",
                "question", "What is 1+1?",
                "expected_answer", "2",
                "model_answer", "two"
        ));

        assertThat(formatted.getContent()).isInstanceOf(String.class);
        String content = (String) formatted.getContent();
        assertThat(content).contains("custom_metric", "[Question]: What is 1+1?", "[Expected Answer]: 2");
        assertThat(content).doesNotContain("{{user_metrics}}", "{{question}}", "{{expected_answer}}", "{{model_answer}}");
    }
}
