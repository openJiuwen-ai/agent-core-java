// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.evaluator;

/**
 * Evaluation templates.
 * <p>
 * Mirrors Python's {@code templates.py} from
 * {@code openjiuwen.agent_evolving.evaluator.templates}.
 */
public final class EvalTemplates {
    
    private EvalTemplates() {
        // Utility class
    }
    
    /**
     * Get evaluation prompt template.
     */
    public static String getEvaluationPrompt(String question, String answer) {
        return String.format(
            "Question: %s\n\nAnswer: %s\n\nPlease evaluate the answer quality on a scale of 0-1.",
            question, answer
        );
    }
    
    /**
     * Get judge prompt template.
     */
    public static String getJudgePrompt(String question, String answer, String expected) {
        return String.format(
            "Question: %s\n\nExpected Answer: %s\n\nActual Answer: %s\n\n" +
            "Please evaluate how well the actual answer matches the expected answer.",
            question, expected, answer
        );
    }
}