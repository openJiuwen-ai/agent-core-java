/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Improved evaluation wrapper for tool optimization.
 *
 * <p>Mirrors Python's {@code SimpleEval} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_eval.py}.</p>
 */
public class SimpleEval {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object apiWrapper;
    private final double fnCallWeight;
    private final double outputEffectivenessWeight;
    private final Map<String, Object> config;

    public SimpleEval(Object apiWrapper, Map<String, Object> config) {
        this(apiWrapper, config, 0.4d, 0.6d);
    }

    public SimpleEval(
            Object apiWrapper,
            Map<String, Object> config,
            double fnCallWeight,
            double outputEffectivenessWeight
    ) {
        this.apiWrapper = apiWrapper;
        this.config = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
        this.fnCallWeight = fnCallWeight;
        this.outputEffectivenessWeight = outputEffectivenessWeight;
        if (Math.abs(fnCallWeight + outputEffectivenessWeight - 1.0d) > 1e-6d) {
            throw new IllegalArgumentException("fn_call_weight and output_effectiveness_weight must sum to 1.0");
        }
    }

    public Map<String, Object> call(
            Map<String, Object> tool,
            String description,
            List<Object[]> examples,
            int runs
    ) {
        return evaluate(tool, description, examples, runs);
    }

    /**
     * Evaluate a tool with given examples.
     *
     * @param tool tool definition
     * @param description tool description
     * @param examples tuples of instruction, expected function call, output, answer
     * @param runs number of evaluation runs
     * @return evaluation summary
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
        List<Object[]> safeExamples = examples == null ? List.of() : examples;

        for (int run = 0; run < runs; run++) {
            List<Map<String, Object>> runResults = new ArrayList<>();
            double totalFnCallScore = 0.0d;
            double totalOutputScore = 0.0d;

            for (int i = 0; i < safeExamples.size(); i++) {
                Map<String, Object> result = evaluateSingleExample(tool, description, safeExamples.get(i), i);
                runResults.add(result);
                totalFnCallScore += doubleValue(result.get("fn_call_score"));
                totalOutputScore += doubleValue(result.get("output_effectiveness_score"));
            }

            double totalCount = safeExamples.size();
            double avgFnCallScore = totalCount > 0 ? totalFnCallScore / totalCount : 0.0d;
            double avgOutputScore = totalCount > 0 ? totalOutputScore / totalCount : 0.0d;
            double totalScore = fnCallWeight * avgFnCallScore + outputEffectivenessWeight * avgOutputScore;
            allScores.add(totalScore);
            allFnCallScores.add(avgFnCallScore);
            allOutputScores.add(avgOutputScore);
            allResults.add(runResults);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score_avg", mean(allScores) * 100.0d);
        result.put("score_std", std(allScores) * 100.0d);
        result.put("fn_call_accuracy", mean(allFnCallScores) * 100.0d);
        result.put("output_effectiveness", mean(allOutputScores) * 100.0d);
        result.put("results", runs == 1 && !allResults.isEmpty() ? allResults.get(0) : allResults);
        return result;
    }

    protected Map<String, Object> evaluateSingleExample(
            Map<String, Object> tool,
            String description,
            Object[] example,
            int exampleId
    ) {
        String instruction = tupleString(example, 0);
        Object expectedFnCall = tupleValue(example, 1);
        Object expectedOutput = tupleValue(example, 2);
        String answer = tupleString(example, 3);

        try {
            Map<String, Object> generatedFnCall = generateFunctionCall(tool, description, instruction);
            double fnCallScore = evaluateFunctionCallAccuracy(generatedFnCall, expectedFnCall);
            Object executionResult = null;
            Object executionError = null;
            List<Map<String, Object>> errors = new ArrayList<>();

            if (apiWrapper != null) {
                try {
                    Object[] actualOutput = invokeApiWrapper(tool, generatedFnCall);
                    String payload = actualOutput.length > 0 ? String.valueOf(actualOutput[0]) : "";
                    int statusCode = actualOutput.length > 1 ? toStatusCode(actualOutput[1]) : 12;
                    if (statusCode == 0) {
                        executionResult = parseJson(payload);
                    } else {
                        executionError = parseJson(payload);
                        errors.add(errorEntry(
                                stringValue(generatedFnCall.getOrDefault("name", toolName(tool))),
                                generatedFnCall.getOrDefault("arguments", Map.of()),
                                String.valueOf(executionError)
                        ));
                    }
                } catch (Exception exception) {
                    executionError = Map.of("error", exception.getMessage());
                    errors.add(errorEntry(
                            stringValue(generatedFnCall.getOrDefault("name", toolName(tool))),
                            generatedFnCall.getOrDefault("arguments", Map.of()),
                            exception.getMessage()
                    ));
                }
            } else {
                String errorMessage = "Missing required input: api_wrapper";
                errors.add(errorEntry(toolName(tool), Map.of(), errorMessage));
                throw new IllegalArgumentException(errorMessage);
            }

            double outputEffectivenessScore = evaluateOutputEffectiveness(
                    instruction,
                    executionResult,
                    executionError,
                    answer
            );
            double weightedScore = fnCallWeight * fnCallScore + outputEffectivenessWeight * outputEffectivenessScore;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instruction", instruction);
            result.put("expected_fn_call", expectedFnCall);
            result.put("generated_fn_call", generatedFnCall);
            result.put("fn_call_score", fnCallScore);
            result.put("execution_result", executionResult);
            result.put("execution_error", executionError);
            result.put("output_effectiveness_score", outputEffectivenessScore);
            result.put("weighted_score", weightedScore);
            result.put("answer", answer);
            result.put("errors", errors);
            result.put("expected_output", expectedOutput);
            return result;
        } catch (Exception exception) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instruction", instruction);
            result.put("expected_fn_call", expectedFnCall);
            result.put("generated_fn_call", null);
            result.put("fn_call_score", 0.0d);
            result.put("execution_result", null);
            result.put("execution_error", Map.of("error", exception.getMessage()));
            result.put("output_effectiveness_score", 0.0d);
            result.put("weighted_score", 0.0d);
            result.put("answer", answer);
            result.put("errors", List.of(errorEntry(toolName(tool), Map.of(), exception.getMessage())));
            return result;
        }
    }

    protected Map<String, Object> generateFunctionCall(
            Map<String, Object> tool,
            String description,
            String instruction
    ) {
        Map<String, Object> normalizedTool = normalizeTool(tool);
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("name", normalizedTool.getOrDefault("name", toolName(tool)));
        fallback.put("arguments", new LinkedHashMap<>());
        return fallback;
    }

    public double evaluateFunctionCallAccuracy(Map<String, Object> generatedFnCall, Object expectedFnCall) {
        try {
            Map<String, Object> expectedMap = toStringMap(expectedFnCall);
            if (generatedFnCall == null || expectedMap == null) {
                return 0.0d;
            }
            double score = 0.0d;
            double maxScore = 0.0d;

            maxScore += 0.3d;
            if (Objects.equals(generatedFnCall.get("name"), expectedMap.get("name"))) {
                score += 0.3d;
            }

            Object generatedParams = parsePossibleJson(generatedFnCall.getOrDefault("arguments", Map.of()));
            Object expectedParams = parsePossibleJson(expectedMap.getOrDefault("arguments", Map.of()));

            if (isPythonFalsy(expectedParams) && isPythonFalsy(generatedParams)) {
                score += 0.7d;
                maxScore += 0.7d;
            } else if (!isPythonFalsy(expectedParams)) {
                if (!(expectedParams instanceof Map<?, ?> expectedParamMap)) {
                    return 0.0d;
                }
                int expectedSize = expectedParamMap.isEmpty() ? 1 : expectedParamMap.size();
                for (Map.Entry<?, ?> entry : expectedParamMap.entrySet()) {
                    maxScore += 0.7d / expectedSize;
                    if (generatedParams instanceof Map<?, ?> generatedParamMap
                            && generatedParamMap.containsKey(entry.getKey())
                            && compareParameterValues(generatedParamMap.get(entry.getKey()), entry.getValue())) {
                        score += 0.7d / expectedSize;
                    }
                }
            } else {
                maxScore += 0.7d;
            }

            return maxScore > 0.0d ? score / maxScore : 0.0d;
        } catch (Exception exception) {
            return 0.0d;
        }
    }

    public static boolean compareParameterValues(Object actual, Object expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        try {
            if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
                return Math.abs(actualNumber.doubleValue() - expectedNumber.doubleValue()) < 1e-6d;
            }
            return String.valueOf(actual).strip().equalsIgnoreCase(String.valueOf(expected).strip());
        } catch (Exception exception) {
            return false;
        }
    }

    protected double evaluateOutputEffectiveness(
            String instruction,
            Object executionResult,
            Object executionError,
            String expectedAnswer
    ) {
        if (executionError != null) {
            return 0.0d;
        }
        String prompt = """
Evaluate whether the function execution result effectively solves the user's problem.

User Instruction: %s

Function Execution Result: %s

Expected Answer/Goal: %s

Please evaluate on a scale of 0-100 how well the function execution result addresses the user's instruction and matches the expected answer. Consider:
1. Does the result provide the information requested in the instruction?
2. Is the result accurate and complete?
3. Does it align with the expected answer?

Respond with only a number between 0 and 100. Do not include explainations.
""".formatted(instruction, toJson(executionResult), expectedAnswer);

        try {
            String response = invokeRitsScore(stringValue(config.get("eval_model_id")), prompt);
            double score = Double.parseDouble(response.strip());
            return Math.max(0.0d, Math.min(score, 100.0d)) / 100.0d;
        } catch (Exception exception) {
            return simpleOutputComparison(executionResult, expectedAnswer);
        }
    }

    public static double simpleOutputComparison(Object executionResult, String expectedAnswer) {
        try {
            if (executionResult == null) {
                return 0.0d;
            }
            String resultText = executionResult instanceof String text ? text : toJson(executionResult);
            String result = resultText.toLowerCase(Locale.ROOT).strip();
            String expected = expectedAnswer == null ? "" : expectedAnswer.toLowerCase(Locale.ROOT).strip();
            if (!expected.isEmpty() && result.contains(expected)) {
                return 1.0d;
            }
            if (!result.isEmpty() && expected.contains(result)) {
                return 0.8d;
            }
            return 0.3d;
        } catch (Exception exception) {
            return 0.0d;
        }
    }

    protected String invokeRitsScore(String modelId, String prompt) {
        try {
            Class<?> ritsClass = Class.forName(
                    "com.openjiuwen.agent_evolving.optimizer.tool_call.utils.RitsUtils"
            );
            Method method = ritsClass.getMethod("getRitsResponse", String.class, String.class, String.class);
            return String.valueOf(method.invoke(null, modelId, prompt, stringValue(config.get("llm_api_key"))));
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("RitsUtils dependency is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("RitsUtils dependency could not be invoked", exception);
        }
    }

    private Object[] invokeApiWrapper(Map<String, Object> tool, Map<String, Object> generatedFnCall) throws Exception {
        if (apiWrapper instanceof ApiWrapper wrapper) {
            return wrapper.call(tool, generatedFnCall);
        }
        for (String methodName : List.of("call", "apply")) {
            for (Method method : apiWrapper.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 2) {
                    Object raw = method.invoke(apiWrapper, tool, generatedFnCall);
                    if (raw instanceof Object[] values) {
                        return values;
                    }
                    if (raw instanceof List<?> values) {
                        return values.toArray();
                    }
                }
            }
        }
        throw new NoSuchMethodException("Unsupported api_wrapper type: " + apiWrapper.getClass().getName());
    }

    private Map<String, Object> normalizeTool(Map<String, Object> tool) {
        Map<String, Object> normalized = tool == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tool);
        normalized.putIfAbsent("type", "function");
        Object description = normalized.get("description");
        if (description instanceof String text) {
            Object parsed = parsePossibleJson(text);
            if (parsed instanceof Map<?, ?> parsedMap && parsedMap.get("function") instanceof Map<?, ?> functionMap) {
                Map<String, Object> function = toStringMap(functionMap);
                Map<String, Object> flattened = new LinkedHashMap<>();
                flattened.put("name", function.getOrDefault("name", normalized.get("name")));
                flattened.put("type", normalized.getOrDefault("type", "tool"));
                flattened.put("description", function.getOrDefault("description", ""));
                flattened.put("parameters", function.getOrDefault("parameters", Map.of()));
                return flattened;
            }
        }
        return normalized;
    }

    private static Object parsePossibleJson(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return value;
        }
        try {
            return OBJECT_MAPPER.readValue(text, Object.class);
        } catch (Exception exception) {
            return value;
        }
    }

    private static Object parseJson(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, Object.class);
        } catch (Exception exception) {
            return value;
        }
    }

    private static Map<String, Object> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static boolean isPythonFalsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        if (value instanceof Number number) {
            return Double.compare(number.doubleValue(), 0.0d) == 0;
        }
        if (value instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return value instanceof Collection<?> collection && collection.isEmpty();
    }

    private static String tupleString(Object[] tuple, int index) {
        Object value = tupleValue(tuple, index);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object tupleValue(Object[] tuple, int index) {
        return tuple != null && tuple.length > index ? tuple[index] : null;
    }

    private static double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private static double std(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        double mean = mean(values);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0d);
        return Math.sqrt(variance);
    }

    private static double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static int toStatusCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 12;
        }
    }

    private static String toolName(Map<String, Object> tool) {
        return stringValue(tool == null ? null : tool.getOrDefault("name", "unknown"));
    }

    private static Map<String, Object> errorEntry(String functionName, Object arguments, String errorMessage) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("function_name", functionName);
        entry.put("arguments", arguments);
        entry.put("error_msg", errorMessage);
        return entry;
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    @FunctionalInterface
    public interface ApiWrapper {
        Object[] call(Map<String, Object> tool, Map<String, Object> generatedFnCall) throws Exception;
    }
}
