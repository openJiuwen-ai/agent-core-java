// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;
import java.util.function.Function;

/**
 * Simple evaluation wrapper for tool optimization.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_eval.SimpleEval}.
 */
public class SimpleEval {

    private final Object apiWrapper;
    private final double fnCallWeight;
    private final double outputEffectivenessWeight;
    private final Map<String, Object> config;

    /**
     * Create simple evaluation instance.
     *
     * @param apiWrapper                 API wrapper for execution
     * @param config                     Configuration map
     * @param fnCallWeight               Weight for function call accuracy
     * @param outputEffectivenessWeight  Weight for output effectiveness
     */
    public SimpleEval(
            Object apiWrapper,
            Map<String, Object> config,
            double fnCallWeight,
            double outputEffectivenessWeight
    ) {
        this.apiWrapper = apiWrapper;
        this.config = config;
        this.fnCallWeight = fnCallWeight;
        this.outputEffectivenessWeight = outputEffectivenessWeight;

        if (Math.abs(fnCallWeight + outputEffectivenessWeight - 1.0) > 1e-6) {
            throw new IllegalArgumentException("fnCallWeight and outputEffectivenessWeight must sum to 1.0");
        }
    }

    /**
     * Create simple evaluation with default weights.
     *
     * @param apiWrapper API wrapper
     * @param config     Configuration
     */
    public SimpleEval(Object apiWrapper, Map<String, Object> config) {
        this(apiWrapper, config, 0.4, 0.6);
    }

    /**
     * Evaluate a tool with given examples.
     *
     * @param tool        Tool definition
     * @param description Tool description
     * @param examples    Example cases
     * @param runs        Number of evaluation runs
     * @return Evaluation results
     */
    public Map<String, Object> evaluate(
            Map<String, Object> tool,
            String description,
            List<Object[]> examples,
            int runs
    ) {
        List<Double> allScores = new ArrayList<>();
        List<Double> allFnCallScores = new ArrayList<>();
        List<Double> allOutputScores = new ArrayList<>();
        List<List<Map<String, Object>>> allResults = new ArrayList<>();

        for (int run = 0; run < runs; run++) {
            List<Map<String, Object>> runResults = new ArrayList<>();
            double totalFnCallScore = 0.0;
            double totalOutputScore = 0.0;
            int totalCount = examples != null ? examples.size() : 0;

            if (examples != null) {
                for (Object[] example : examples) {
                    Map<String, Object> result = evaluateSingleExample(tool, description, example);
                    runResults.add(result);
                    totalFnCallScore += getDoubleValue(result, "fn_call_score", 0.0);
                    totalOutputScore += getDoubleValue(result, "output_effectiveness_score", 0.0);
                }
            }

            double avgFnCallScore = totalCount > 0 ? totalFnCallScore / totalCount : 0.0;
            double avgOutputScore = totalCount > 0 ? totalOutputScore / totalCount : 0.0;
            double totalScore = fnCallWeight * avgFnCallScore + outputEffectivenessWeight * avgOutputScore;

            allScores.add(totalScore);
            allFnCallScores.add(avgFnCallScore);
            allOutputScores.add(avgOutputScore);
            allResults.add(runResults);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("score_avg", calculateAverage(allScores) * 100.0);
        result.put("score_std", calculateStdDev(allScores) * 100.0);
        result.put("fn_call_accuracy", calculateAverage(allFnCallScores) * 100.0);
        result.put("output_effectiveness", calculateAverage(allOutputScores) * 100.0);
        result.put("results", runs == 1 && !allResults.isEmpty() ? allResults.get(0) : allResults);

        return result;
    }

    private Map<String, Object> evaluateSingleExample(
            Map<String, Object> tool,
            String description,
            Object[] example
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Extract example components
            String instruction = example != null && example.length > 0 ? String.valueOf(example[0]) : "";
            Object expectedFnCall = example != null && example.length > 1 ? example[1] : null;
            Object fnOutput = example != null && example.length > 2 ? example[2] : null;
            String answer = example != null && example.length > 3 ? String.valueOf(example[3]) : "";

            // Generate function call
            Map<String, Object> generatedFnCall = generateFunctionCall(tool, description, instruction);

            // Evaluate function call accuracy
            double fnCallScore = evaluateFunctionCallAccuracy(generatedFnCall, expectedFnCall);

            // Execute and evaluate output effectiveness
            Map<String, Object> executionResult = executeFunctionCall(tool, generatedFnCall);
            double outputScore = evaluateOutputEffectiveness(
                    instruction,
                    executionResult.get("result"),
                    executionResult.get("error"),
                    answer
            );

            double weightedScore = fnCallWeight * fnCallScore + outputEffectivenessWeight * outputScore;

            result.put("instruction", instruction);
            result.put("expected_fn_call", expectedFnCall);
            result.put("generated_fn_call", generatedFnCall);
            result.put("fn_call_score", fnCallScore);
            result.put("execution_result", executionResult.get("result"));
            result.put("execution_error", executionResult.get("error"));
            result.put("output_effectiveness_score", outputScore);
            result.put("weighted_score", weightedScore);
            result.put("answer", answer);
            result.put("errors", new ArrayList<>());

        } catch (Exception e) {
            Loggers.AGENT.error("Error evaluating example: {}", e.getMessage());
            result.put("fn_call_score", 0.0);
            result.put("output_effectiveness_score", 0.0);
            result.put("weighted_score", 0.0);
            result.put("errors", List.of(Map.of(
                    "function_name", tool.getOrDefault("name", "unknown"),
                    "arguments", new HashMap<>(),
                    "error_msg", e.getMessage()
            )));
        }

        return result;
    }

    private Map<String, Object> generateFunctionCall(
            Map<String, Object> tool,
            String description,
            String instruction
    ) {
        // Simplified - would use LLM to generate function call
        Map<String, Object> fnCall = new HashMap<>();
        fnCall.put("name", tool.get("name"));
        fnCall.put("arguments", new HashMap<>());
        return fnCall;
    }

    private double evaluateFunctionCallAccuracy(
            Map<String, Object> generated,
            Object expected
    ) {
        if (expected == null || generated == null) {
            return 0.0;
        }

        double score = 0.0;
        double maxScore = 1.0;

        // Check function name (30% weight)
        if (generated.get("name") != null && expected instanceof Map) {
            Map<?, ?> expectedMap = (Map<?, ?>) expected;
            if (generated.get("name").equals(expectedMap.get("name"))) {
                score += 0.3;
            }
        }

        // Check parameters (70% weight)
        // Simplified - would do detailed parameter comparison
        score += 0.7; // Placeholder

        return score / maxScore;
    }

    private Map<String, Object> executeFunctionCall(
            Map<String, Object> tool,
            Map<String, Object> fnCall
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("result", null);
        result.put("error", null);

        // Simplified - would actually execute the function
        return result;
    }

    private double evaluateOutputEffectiveness(
            String instruction,
            Object executionResult,
            Object executionError,
            String expectedAnswer
    ) {
        if (executionError != null) {
            return 0.0;
        }

        // Simplified - would use LLM to evaluate
        return 1.0;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double calculateStdDev(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        double mean = calculateAverage(values);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}