/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Simple evaluation wrapper for tool optimization.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_eval.SimpleEval}.
 * 
 * @since 0.1.7
 */
public class SimpleEval {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object apiWrapper;
    private final double fnCallWeight;
    private final double outputEffectivenessWeight;
    private final Map<String, Object> config;

    /**
     * Create simple evaluation instance.
     * 
     * @param apiWrapper API wrapper for execution
     * @param config Configuration map
     * @param fnCallWeight Weight for function call accuracy
     * @param outputEffectivenessWeight Weight for output effectiveness
     * @since 0.1.7
     */
    public SimpleEval(Object apiWrapper, Map<String, Object> config, double fnCallWeight,
            double outputEffectivenessWeight) {
        this.apiWrapper = apiWrapper;
        this.config = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
        this.fnCallWeight = fnCallWeight;
        this.outputEffectivenessWeight = outputEffectivenessWeight;

        if (Math.abs(fnCallWeight + outputEffectivenessWeight - 1.0d) > 1e-6d) {
            throw new IllegalArgumentException("fnCallWeight and outputEffectivenessWeight must sum to 1.0");
        }
    }

    /**
     * Create simple evaluation with default weights.
     * 
     * @param apiWrapper API wrapper
     * @param config Configuration
     * @since 0.1.7
     */
    public SimpleEval(Object apiWrapper, Map<String, Object> config) {
        this(apiWrapper, config, 0.4d, 0.6d);
    }

    /**
     * Evaluate a tool with given examples.
     * 
     * @param tool Tool definition
     * @param description Tool description
     * @param examples Example cases
     * @param runs Number of evaluation runs
     * @return Evaluation results
     * @since 0.1.7
     */
    public Map<String, Object> evaluate(Map<String, Object> tool, String description, List<Object[]> examples,
            int runs) {
        List<Double> allScores = new ArrayList<>();
        List<Double> allFnCallScores = new ArrayList<>();
        List<Double> allOutputScores = new ArrayList<>();
        List<List<Map<String, Object>>> allResults = new ArrayList<>();

        for (int run = 0; run < runs; run++) {
            List<Map<String, Object>> runResults = new ArrayList<>();
            double totalFnCallScore = 0.0d;
            double totalOutputScore = 0.0d;
            int totalCount = examples != null ? examples.size() : 0;

            if (examples != null) {
                for (Object[] example : examples) {
                    Map<String, Object> result = evaluateSingleExample(tool, description, example);
                    runResults.add(result);
                    totalFnCallScore += getDoubleValue(result, "fn_call_score", 0.0d);
                    totalOutputScore += getDoubleValue(result, "output_effectiveness_score", 0.0d);
                }
            }

            double avgFnCallScore = totalCount > 0 ? totalFnCallScore / totalCount : 0.0d;
            double avgOutputScore = totalCount > 0 ? totalOutputScore / totalCount : 0.0d;
            double totalScore = fnCallWeight * avgFnCallScore + outputEffectivenessWeight * avgOutputScore;

            allScores.add(totalScore);
            allFnCallScores.add(avgFnCallScore);
            allOutputScores.add(avgOutputScore);
            allResults.add(runResults);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score_avg", calculateAverage(allScores) * 100.0d);
        result.put("score_std", calculateStdDev(allScores) * 100.0d);
        result.put("fn_call_accuracy", calculateAverage(allFnCallScores) * 100.0d);
        result.put("output_effectiveness", calculateAverage(allOutputScores) * 100.0d);
        result.put("results", runs == 1 && !allResults.isEmpty() ? allResults.get(0) : allResults);
        return result;
    }

    /**
     * Java alias for Python's callable interface.
     * 
     * @param tool tool
     * @param description description
     * @param examples examples
     * @param runs runs
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> call(Map<String, Object> tool, String description, List<Object[]> examples, int runs) {
        return evaluate(tool, description, examples, runs);
    }

    /**
     * evaluateSingleExample.
     * 
     * @param tool tool
     * @param description description
     * @param example example
     * @return the result
     * @since 0.1.7
     */
    protected Map<String, Object> evaluateSingleExample(Map<String, Object> tool, String description,
            Object[] example) {
        Map<String, Object> result = new LinkedHashMap<>();
        String instruction = example != null && example.length > 0 ? String.valueOf(example[0]) : "";
        Object expectedFnCall = example != null && example.length > 1 ? example[1] : null;
        String answer = example != null && example.length > 3 ? String.valueOf(example[3]) : "";

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
                        executionResult = OBJECT_MAPPER.readValue(payload, Object.class);
                    } else {
                        executionError = OBJECT_MAPPER.readValue(payload, Object.class);
                        errors.add(buildErrorEntry(
                                generatedFnCall != null
                                        ? String.valueOf(generatedFnCall.getOrDefault("name",
                                                tool.getOrDefault("name", "unknown")))
                                        : String.valueOf(tool.getOrDefault("name", "unknown")),
                                generatedFnCall != null
                                        ? generatedFnCall.getOrDefault("arguments", Map.of())
                                        : Map.of(),
                                String.valueOf(executionError)));
                    }
                } catch (Exception e) {
                    executionError = Map.of("error", e.getMessage());
                    errors.add(
                            buildErrorEntry(
                                    generatedFnCall != null
                                            ? String.valueOf(generatedFnCall.getOrDefault("name",
                                                    tool.getOrDefault("name", "unknown")))
                                            : String.valueOf(tool.getOrDefault("name", "unknown")),
                                    generatedFnCall != null
                                            ? generatedFnCall.getOrDefault("arguments", Map.of())
                                            : Map.of(),
                                    e.getMessage()));
                }
            } else {
                String errorMessage = "Missing required input: apiWrapper";
                Loggers.AGENT.error(errorMessage);
                errors.add(
                        buildErrorEntry(String.valueOf(tool.getOrDefault("name", "unknown")), Map.of(), errorMessage));
                throw new IllegalStateException(errorMessage);
            }

            double outputScore = evaluateOutputEffectiveness(instruction, executionResult, executionError, answer);
            double weightedScore = fnCallWeight * fnCallScore + outputEffectivenessWeight * outputScore;

            result.put("instruction", instruction);
            result.put("expected_fn_call", expectedFnCall);
            result.put("generated_fn_call", generatedFnCall);
            result.put("fn_call_score", fnCallScore);
            result.put("execution_result", executionResult);
            result.put("execution_error", executionError);
            result.put("output_effectiveness_score", outputScore);
            result.put("weighted_score", weightedScore);
            result.put("answer", answer);
            result.put("errors", errors);
        } catch (Exception e) {
            Loggers.AGENT.error("Error evaluating example: {}", e.getMessage());
            result.put("instruction", instruction);
            result.put("expected_fn_call", expectedFnCall);
            result.put("generated_fn_call", null);
            result.put("fn_call_score", 0.0d);
            result.put("execution_result", null);
            result.put("execution_error", Map.of("error", e.getMessage()));
            result.put("output_effectiveness_score", 0.0d);
            result.put("weighted_score", 0.0d);
            result.put("answer", answer);
            result.put("errors", List.of(Map.of("function_name", tool.getOrDefault("name", "unknown"), "arguments",
                    new HashMap<>(), "error_msg", e.getMessage())));
        }

        return result;
    }

    /**
     * generateFunctionCall.
     * 
     * @param tool tool
     * @param description description
     * @param instruction instruction
     * @return the result
     * @since 0.1.7
     */
    protected Map<String, Object> generateFunctionCall(Map<String, Object> tool, String description,
            String instruction) {
        Map<String, Object> normalizedTool = normalizeTool(tool);
        String fallbackName = String.valueOf(normalizedTool.getOrDefault("name", tool.getOrDefault("name", "")));

        try {
            ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                    .modelName(String.valueOf(config.getOrDefault("eval_model_id", ""))).build();
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(String.valueOf(config.getOrDefault("client_provider", "OpenAI")))
                    .apiBase(String.valueOf(config.getOrDefault("api_base", "https://api.openai.com/v1")))
                    .apiKey(String.valueOf(config.getOrDefault("llm_api_key", "")))
                    .verifySsl(Boolean.parseBoolean(String.valueOf(config.getOrDefault("verify_ssl", true))))
                    .timeout(getDouble(config, "timeout", 60.0d)).maxRetries(getInt(config, "max_retries", 1)).build();

            Model model = new Model(clientConfig, modelConfig);
            AssistantMessage response = model.invoke(List.of(Map.of("role", "user", "content", instruction)),
                    List.of(Map.of("type", "function", "function", normalizedTool)), null, null,
                    String.valueOf(config.getOrDefault("eval_model_id", "")), null, null, null, null, Map.of());

            List<ToolCall> toolCalls = response != null ? response.getToolCalls() : null;
            if (toolCalls != null && !toolCalls.isEmpty()) {
                ToolCall toolCall = toolCalls.get(0);
                Map<String, Object> functionCall = new LinkedHashMap<>();
                functionCall.put("name", toolCall.getName() != null ? toolCall.getName() : fallbackName);
                functionCall.put("arguments", parsePossibleJson(toolCall.getArguments()));
                return functionCall;
            }
        } catch (Exception e) {
            Loggers.AGENT.error("Error generating function call: {}", e.getMessage());
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("name", fallbackName);
        fallback.put("arguments", new LinkedHashMap<>());
        return fallback;
    }

    /**
     * evaluateFunctionCallAccuracy.
     * 
     * @param generated generated
     * @param expected expected
     * @return the result
     * @since 0.1.7
     */
    protected double evaluateFunctionCallAccuracy(Map<String, Object> generated, Object expected) {
        try {
            Map<String, Object> expectedMap = asMap(expected);
            if (generated == null || expectedMap == null) {
                return 0.0d;
            }

            double score = 0.0d;
            double maxScore = 0.0d;

            maxScore += 0.3d;
            if (Objects.equals(generated.get("name"), expectedMap.get("name"))) {
                score += 0.3d;
            }

            Object generatedArgs = parsePossibleJson(generated.get("arguments"));
            Object expectedArgs = parsePossibleJson(expectedMap.get("arguments"));

            if (isEmptyArguments(expectedArgs) && isEmptyArguments(generatedArgs)) {
                score += 0.7d;
                maxScore += 0.7d;
            } else if (expectedArgs instanceof Map<?, ?> expectedParams) {
                maxScore += 0.7d;
                int expectedSize = expectedParams.isEmpty() ? 1 : expectedParams.size();
                double perKeyScore = 0.7d / expectedSize;
                for (Map.Entry<?, ?> entry : expectedParams.entrySet()) {
                    Object key = entry.getKey();
                    if (generatedArgs instanceof Map<?, ?> generatedParams && generatedParams.containsKey(key)
                            && compareParameterValues(generatedParams.get(key), entry.getValue())) {
                        score += perKeyScore;
                    }
                }
            } else {
                maxScore += 0.7d;
            }

            return maxScore > 0.0d ? score / maxScore : 0.0d;
        } catch (Exception e) {
            Loggers.AGENT.error("Error evaluating function call accuracy: {}", e.getMessage());
            return 0.0d;
        }
    }

    /**
     * compareParameterValues.
     * 
     * @param actual actual
     * @param expected expected
     * @return the result
     * @since 0.1.7
     */
    protected boolean compareParameterValues(Object actual, Object expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        try {
            if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
                return Math.abs(actualNumber.doubleValue() - expectedNumber.doubleValue()) < 1e-6d;
            }
            return String.valueOf(actual).trim().equalsIgnoreCase(String.valueOf(expected).trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * evaluateOutputEffectiveness.
     * 
     * @param instruction instruction
     * @param executionResult executionResult
     * @param executionError executionError
     * @param expectedAnswer expectedAnswer
     * @return the result
     * @since 0.1.7
     */
    protected double evaluateOutputEffectiveness(String instruction, Object executionResult, Object executionError,
            String expectedAnswer) {
        if (executionError != null) {
            return 0.0d;
        }

        String prompt = """
                Evaluate whether the function execution result effectively solves the user's problem.

                User Instruction: %s

                Function Execution Result: %s

                Expected Answer/Goal: %s

                Please evaluate on a scale of 0-100 how well the function execution result addresses the user's \
                instruction and matches the expected answer. Consider:
                1. Does the result provide the information requested in the instruction?
                2. Is the result accurate and complete?
                3. Does it align with the expected answer?

                Respond with only a number between 0 and 100. Do not include explainations.
                """.formatted(instruction, toJson(executionResult), expectedAnswer);

        try {
            String response = RitsUtils.getRitsResponse(String.valueOf(config.getOrDefault("eval_model_id", "")),
                    prompt, String.valueOf(config.getOrDefault("llm_api_key", "")));
            double score = Double.parseDouble(response.trim());
            return Math.max(0.0d, Math.min(100.0d, score)) / 100.0d;
        } catch (NumberFormatException e) {
            Loggers.AGENT.error("Error evaluating output effectiveness: {}", e.getMessage());
            return simpleOutputComparison(executionResult, expectedAnswer);
        }
    }

    /**
     * simpleOutputComparison.
     * 
     * @param executionResult executionResult
     * @param expectedAnswer expectedAnswer
     * @return the result
     * @since 0.1.7
     */
    protected double simpleOutputComparison(Object executionResult, String expectedAnswer) {
        try {
            if (executionResult == null) {
                return 0.0d;
            }
            String resultString = executionResult instanceof String text ? text : toJson(executionResult);
            String normalizedResult = resultString.toLowerCase(Locale.ROOT).trim();
            String normalizedExpected = expectedAnswer == null ? "" : expectedAnswer.toLowerCase(Locale.ROOT).trim();
            if (!normalizedExpected.isEmpty() && normalizedResult.contains(normalizedExpected)) {
                return 1.0d;
            }
            if (!normalizedResult.isEmpty() && normalizedExpected.contains(normalizedResult)) {
                return 0.8d;
            }
            return 0.3d;
        } catch (Exception e) {
            Loggers.AGENT.error("Error in simple output comparison: {}", e.getMessage());
            return 0.0d;
        }
    }

    /**
     * invokeApiWrapper.
     * 
     * @param tool tool
     * @param generatedFnCall generatedFnCall
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private Object[] invokeApiWrapper(Map<String, Object> tool, Map<String, Object> generatedFnCall) throws Exception {
        if (apiWrapper instanceof SimpleApiWrapper wrapper) {
            return wrapper.call(tool, generatedFnCall);
        }
        for (String methodName : List.of("call", "apply")) {
            for (java.lang.reflect.Method method : apiWrapper.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 2) {
                    Object raw = method.invoke(apiWrapper, tool, generatedFnCall);
                    if (raw instanceof Object[] array) {
                        return array;
                    }
                    if (raw instanceof List<?> list) {
                        return list.toArray();
                    }
                }
            }
        }
        throw new NoSuchMethodException("Unsupported apiWrapper type: " + apiWrapper.getClass().getName());
    }

    /**
     * normalizeTool.
     * 
     * @param tool tool
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> normalizeTool(Map<String, Object> tool) {
        Map<String, Object> normalized = tool != null ? new LinkedHashMap<>(tool) : new LinkedHashMap<>();
        normalized.putIfAbsent("type", "function");
        Object description = normalized.get("description");
        if (description instanceof String text) {
            Object parsed = parsePossibleJson(text);
            if (parsed instanceof Map<?, ?> descriptionMap && descriptionMap.containsKey("function")) {
                Map<String, Object> function = asMap(descriptionMap.get("function"));
                if (function != null) {
                    Map<String, Object> flattened = new LinkedHashMap<>();
                    flattened.put("name", function.getOrDefault("name", normalized.get("name")));
                    flattened.put("type", normalized.getOrDefault("type", "function"));
                    flattened.put("description", function.getOrDefault("description", ""));
                    flattened.put("parameters", function.getOrDefault("parameters", Map.of()));
                    return flattened;
                }
            }
        }
        return normalized;
    }

    /**
     * asMap.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }
        return null;
    }

    /**
     * isEmptyArguments.
     * 
     * @param arguments arguments
     * @return the result
     * @since 0.1.7
     */
    private boolean isEmptyArguments(Object arguments) {
        if (arguments == null) {
            return true;
        }
        if (arguments instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (arguments instanceof String text) {
            return text.isBlank();
        }
        return false;
    }

    /**
     * parsePossibleJson.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private Object parsePossibleJson(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return value;
        }
        try {
            return OBJECT_MAPPER.readValue(text, Object.class);
        } catch (JsonProcessingException ignored) {
            return value;
        }
    }

    /**
     * toStatusCode.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private int toStatusCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 12;
        }
    }

    /**
     * buildErrorEntry.
     * 
     * @param functionName functionName
     * @param arguments arguments
     * @param errorMessage errorMessage
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildErrorEntry(String functionName, Object arguments, String errorMessage) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("function_name", functionName);
        entry.put("arguments", arguments);
        entry.put("error_msg", errorMessage);
        return entry;
    }

    /**
     * toJson.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    /**
     * getInt.
     * 
     * @param map map
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map != null ? map.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * getDouble.
     * 
     * @param map map
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map != null ? map.get(key) : null;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * getDoubleValue.
     * 
     * @param map map
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    /**
     * calculateAverage.
     * 
     * @param values values
     * @return the result
     * @since 0.1.7
     */
    private double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    /**
     * calculateStdDev.
     * 
     * @param values values
     * @return the result
     * @since 0.1.7
     */
    private double calculateStdDev(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0d;
        }
        double mean = calculateAverage(values);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0d);
        return Math.sqrt(variance);
    }
}
