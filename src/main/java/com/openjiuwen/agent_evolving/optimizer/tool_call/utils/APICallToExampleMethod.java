/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class APICallToExampleMethod extends BaseMethod {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object runToolWithApiCall;
    private final Object evalFn;
    private final List<String> apiKeys;
    private final List<String> nonOptParams;

    public APICallToExampleMethod(
            Map<String, Object> config,
            Object apiCallFn,
            Object evalFn,
            List<String> apiKeys,
            List<String> nonOptParams
    ) {
        super(config);
        this.runToolWithApiCall = apiCallFn;
        this.evalFn = evalFn;
        this.apiKeys = apiKeys;
        this.nonOptParams = nonOptParams != null ? new ArrayList<>(nonOptParams) : new ArrayList<>();
    }

    public StepResult step(Map<String, Object> tool, List<Object> prevOutputs, int it) {
        List<Object> history = prevOutputs != null ? new ArrayList<>(prevOutputs) : new ArrayList<>();
        String description = getOriginalDescription(tool);
        Map<String, Object> toolForOpt = new LinkedHashMap<>(tool);
        Map<String, Object> outputs = null;
        Map<String, Object> fnCall = null;
        String toolRes = null;

        for (int retry = 0; retry < getInt("num_init_loop", 1); retry++) {
            fnCall = generateApiCallFromDescription(toolForOpt, null, 1, history);
            Object[] execution = executeToolCall(toolForOpt, fnCall);
            toolRes = String.valueOf(execution[0]);
            int statusCode = toInt(execution.length > 1 ? execution[1] : 12, 12);
            outputs = new LinkedHashMap<>();
            outputs.put("fn_call", fnCall);
            outputs.put("tool_results", toolRes);
            outputs.put("status_code", statusCode);
            outputs.put("score", statusCode);

            Map<String, Object> apiAnalysis = critiqueApiCall(toolForOpt, fnCall, toolRes);
            if (toInt(apiAnalysis.get("err_code"), 0) == -1) {
                outputs.put("status_code", -1);
                outputs.put("score", -1);
                outputs.put("api_reflection", String.valueOf(apiAnalysis.getOrDefault("analysis", "")));
                history.add(outputs);
                continue;
            }
            break;
        }

        if (outputs == null || fnCall == null || toolRes == null) {
            return new StepResult(new ArrayList<>(), 0.0d, new LinkedHashMap<>());
        }

        List<String> instructions = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<String> analyses = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        List<String> reflections = new ArrayList<>();
        Map<String, Object> instOutput = null;

        for (int refine = 0; refine < getInt("num_refine_steps", 1); refine++) {
            String instruction = generateInstructionFromApiCall(toolForOpt, fnCall, toolRes, instOutput);
            String answer = produceAnswerFromApiCall(instruction, toJson(toolForOpt), toolRes);
            Map<String, Object> critique = critiqueInstruction(toolForOpt, instruction, fnCall, toolRes, answer);
            instructions.add(instruction);
            answers.add(answer);
            scores.add(toDouble(critique.get("score"), 0.0d));
            analyses.add(String.valueOf(critique.getOrDefault("analysis", "")));

            List<String> lastInstructions = tail(instructions, getInt("num_feedback_steps", 2));
            List<Double> lastScores = tail(scores, getInt("num_feedback_steps", 2));
            List<String> lastAnalyses = tail(analyses, getInt("num_feedback_steps", 2));
            String reflection = batchReflectionWithScores(toolForOpt, fnCall, lastInstructions, lastScores, lastAnalyses);
            reflections.add(reflection);

            instOutput = new LinkedHashMap<>();
            instOutput.put("instructions", lastInstructions);
            instOutput.put("scores", lastScores);
            instOutput.put("batch_reflection", reflection);
            if (toInt(critique.get("score"), 0) == 3) {
                break;
            }
        }

        double evalScore = 1.0d;
        double weight = getDouble("score_eval_weight", 0.0d);
        if (weight > 0.0d && !instructions.isEmpty() && !answers.isEmpty()) {
            List<Object> examples = List.of(new Object[]{
                    instructions.get(instructions.size() - 1).trim(),
                    fnCall,
                    toolRes,
                    answers.get(answers.size() - 1).trim()
            });
            Map<String, Object> eval = invokeEval(tool, description, examples, 1);
            evalScore = getDouble(eval, "score_avg", 100.0d) / 100.0d;
        }

        double finalScore = scores.isEmpty() ? 0.0d : scores.get(scores.size() - 1) + weight * (1.0d - evalScore);
        outputs.put("answers", answers);
        outputs.put("instructions", instructions);
        outputs.put("scores", scores);
        outputs.put("analyses", analyses);
        outputs.put("batch_reflections", reflections);
        outputs.put("score", finalScore);
        return new StepResult(instructions, finalScore, outputs);
    }

    public Map<String, Object> generateApiCallFromDescription(Map<String, Object> tool, int numGen, List<Object> prevOutputs) {
        return generateApiCallFromDescription(tool, null, numGen, prevOutputs);
    }

    public Map<String, Object> generateApiCallFromDescription(
            Map<String, Object> tool,
            List<String> exampleCalls,
            int numGen,
            List<Object> prevOutputs
    ) {
        String functionName = String.valueOf(tool.getOrDefault("name", ""));
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        if (exampleCalls != null && !exampleCalls.isEmpty()) {
            prompt.append("Example use cases:\n");
            for (String exampleCall : exampleCalls) {
                prompt.append(exampleCall).append("\n");
            }
        }
        if (apiKeys != null && !apiKeys.isEmpty()) {
            prompt.append("Available API keys: ").append(toJson(apiKeys)).append("\n");
        }
        prompt.append("Write ").append(numGen).append(" example API call(s) as JSON only with fields name and arguments. Use function ")
                .append(functionName).append(" only.\n");
        for (Object prevOutput : prevOutputs != null ? prevOutputs : List.of()) {
            Map<String, Object> output = asMap(prevOutput);
            if (output == null) {
                continue;
            }
            prompt.append("Previous fn_call=").append(toJson(output.get("fn_call")))
                    .append(" status=").append(output.get("status_code"))
                    .append(" reflection=").append(output.containsKey("api_reflection")
                            ? String.valueOf(output.get("api_reflection"))
                            : "good call, avoid duplicates if possible")
                    .append("\n");
        }

        Function<String, Object> verify = text -> {
            Object parsed = FormatUtils.parseJson(text);
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            Map<String, Object> result = toMap(raw);
            if (!result.containsKey("name")) {
                throw new IllegalArgumentException("\"name\" required");
            }
            if (!result.containsKey("arguments")) {
                throw new IllegalArgumentException("\"arguments\" required");
            }
            if (!functionName.equals(String.valueOf(result.get("name")))) {
                throw new IllegalArgumentException("Output function must match the given function");
            }
            return result;
        };
        return ensureMap(invokeRitsResponse(
                String.valueOf(config.getOrDefault("gen_model_id", "")),
                FormatUtils.formatPromptLlama("", prompt.toString()),
                String.valueOf(config.getOrDefault("llm_api_key", "")),
                verify,
                Map.of("max_attempts", 15, "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"))
        ));
    }

    public Map<String, Object> critiqueApiCall(Map<String, Object> tool, Map<String, Object> fnCall, String fnResponse) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        prompt.append("Function call: ").append(toJson(fnCall)).append("\n");
        prompt.append("Execution result: ").append(fnResponse != null && fnResponse.length() > 2048 ? fnResponse.substring(0, 2048) : fnResponse).append("\n");
        prompt.append("Return JSON only: {\"analysis\": ..., \"err_code\": -1|0}");

        Function<String, Object> verify = text -> {
            Object parsed = FormatUtils.parseJson(text);
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            Map<String, Object> result = toMap(raw);
            if (!result.containsKey("analysis") || !result.containsKey("err_code")) {
                throw new IllegalArgumentException("analysis and err_code required");
            }
            result.put("analysis", String.valueOf(result.getOrDefault("analysis", "")).trim());
            result.put("err_code", toInt(result.get("err_code"), 0));
            return result;
        };
        return ensureMap(invokeRitsResponse(
                String.valueOf(config.getOrDefault("eval_model_id", "")),
                FormatUtils.formatPromptLlama("", prompt.toString()),
                String.valueOf(config.getOrDefault("llm_api_key", "")),
                verify,
                Map.of("max_attempts", 15, "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"))
        ));
    }

    public String generateInstructionFromApiCall(
            Map<String, Object> tool,
            Map<String, Object> fnCall,
            String fnResponse,
            Map<String, Object> prevOutput
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        prompt.append("Function call: ").append(toJson(fnCall)).append("\n");
        prompt.append("Response: ").append(fnResponse).append("\n");
        prompt.append("Generate one first-person natural-language instruction in JSON only as {\"instruction\": ...}. Include every parameter value implicitly.");
        if (apiKeys != null && !apiKeys.isEmpty()) {
            prompt.append(" If an API key is required, include one of ").append(toJson(apiKeys)).append(".");
        }
        if (prevOutput != null) {
            prompt.append("\nPrevious instructions:\n");
            List<?> previousInstructions = prevOutput.get("instructions") instanceof List<?> list ? list : List.of();
            List<?> previousScores = prevOutput.get("scores") instanceof List<?> list ? list : List.of();
            int limit = Math.min(previousInstructions.size(), previousScores.size());
            for (int i = 0; i < limit; i++) {
                prompt.append(i + 1).append(". instruction=\"").append(previousInstructions.get(i)).append("\" score=").append(previousScores.get(i)).append("\n");
            }
            prompt.append("Reflection: ").append(String.valueOf(prevOutput.getOrDefault("batch_reflection", ""))).append("\n");
        }

        Function<String, Object> verify = text -> {
            Object parsed = FormatUtils.parseJson(text, "instruction");
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            Map<String, Object> result = toMap(raw);
            if (!result.containsKey("instruction")) {
                throw new IllegalArgumentException("instruction required");
            }
            return String.valueOf(result.get("instruction")).trim();
        };
        Object output = invokeRitsResponse(
                String.valueOf(config.getOrDefault("eval_model_id", "")),
                FormatUtils.formatPromptLlama("", prompt.toString()),
                String.valueOf(config.getOrDefault("llm_api_key", "")),
                verify,
                Map.of("max_attempts", 15, "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"))
        );
        return String.valueOf(output);
    }

    public Map<String, Object> critiqueInstruction(
            Map<String, Object> tool,
            String instruction,
            Map<String, Object> fnCall,
            String fnResponse,
            String answer
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Instruction: ").append(instruction).append("\n");
        prompt.append("Function call: ").append(toJson(fnCall)).append("\n");
        prompt.append("Answer: ").append(answer).append("\n");
        prompt.append("Score this instruction under the Python rules. Return JSON only: {\"analysis\": ..., \"score\": 1|2|3}");

        Function<String, Object> verify = text -> {
            Object parsed = FormatUtils.parseJson(text, "analysis");
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            Map<String, Object> result = toMap(raw);
            if (!result.containsKey("analysis") || !result.containsKey("score")) {
                throw new IllegalArgumentException("analysis and score required");
            }
            result.put("analysis", String.valueOf(result.getOrDefault("analysis", "")).trim());
            result.put("score", toInt(result.get("score"), 0));
            return result;
        };
        return ensureMap(invokeRitsResponse(
                String.valueOf(config.getOrDefault("eval_model_id", "")),
                FormatUtils.formatPromptLlama("", prompt.toString()),
                String.valueOf(config.getOrDefault("llm_api_key", "")),
                verify,
                Map.of("max_attempts", 15, "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"))
        ));
    }

    public String batchReflectionWithScores(
            Map<String, Object> tool,
            Map<String, Object> fnCall,
            List<String> instructions,
            List<Double> scores,
            List<String> analyses
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        prompt.append("Function call: ").append(toJson(fnCall)).append("\n");
        for (int i = 0; i < Math.min(instructions.size(), Math.min(scores.size(), analyses.size())); i++) {
            prompt.append(i + 1).append(". instruction=\"").append(instructions.get(i)).append("\" score=").append(scores.get(i))
                    .append(" analysis=\"").append(analyses.get(i)).append("\"\n");
        }
        prompt.append("Summarize the pattern and how to improve future instructions in under 500 characters.");
        Object output = invokeRitsResponse(
                String.valueOf(config.getOrDefault("eval_model_id", "")),
                FormatUtils.formatPromptLlama("", prompt.toString()),
                String.valueOf(config.getOrDefault("llm_api_key", "")),
                text -> text == null ? "" : text.trim(),
                Map.of("max_attempts", 15, "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"))
        );
        return String.valueOf(output).trim();
    }

    public String getOriginalDescription(Map<String, Object> tool) {
        String description = String.valueOf(tool.getOrDefault("description", ""));
        String indicator = "The description of this function is: \"";
        int found = description.indexOf(indicator);
        return found >= 0 && description.length() > indicator.length()
                ? description.substring(found + indicator.length(), description.length() - 1)
                : description;
    }

    public static class StepResult {
        public final Object data;
        public final double score;
        public final Object results;

        public StepResult(Object data, double score, Object results) {
            this.data = data;
            this.score = score;
            this.results = results;
        }
    }

    private Object[] executeToolCall(Map<String, Object> tool, Map<String, Object> fnCall) {
        try {
            if (runToolWithApiCall instanceof SimpleApiWrapper wrapper) {
                return wrapper.call(tool, fnCall);
            }
            if (runToolWithApiCall instanceof BiFunction<?, ?, ?> biFunction) {
                @SuppressWarnings("unchecked")
                Object raw = ((BiFunction<Map<String, Object>, Map<String, Object>, Object>) biFunction).apply(tool, fnCall);
                return normalizeExecution(raw);
            }
            Object raw = invokeCallable(runToolWithApiCall, List.of("call", "apply", "invoke"), tool, fnCall);
            return normalizeExecution(raw);
        } catch (Exception e) {
            return new Object[]{"{\"error\":\"" + e.getMessage() + "\"}", 12};
        }
    }

    private Object[] normalizeExecution(Object raw) {
        if (raw instanceof Object[] array) {
            return array;
        }
        if (raw instanceof List<?> list) {
            return list.toArray();
        }
        if (raw instanceof Map<?, ?> map && map.containsKey("response") && map.containsKey("status_code")) {
            return new Object[]{map.get("response"), map.get("status_code")};
        }
        return new Object[]{String.valueOf(raw), 0};
    }

    private Map<String, Object> invokeEval(Map<String, Object> tool, String description, List<Object> examples, int runs) {
        if (evalFn == null) {
            return new LinkedHashMap<>();
        }
        try {
            return ensureMap(invokeCallable(evalFn, List.of("evaluate", "call", "apply", "invoke"), tool, description, examples, runs));
        } catch (Exception e) {
            Loggers.AGENT.warn("Eval function invocation failed: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Object invokeCallable(Object target, List<String> names, Object... args) throws Exception {
        if (target == null) {
            throw new IllegalStateException("Missing callable");
        }
        for (String name : names) {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException("Unsupported callable type: " + target.getClass().getName());
    }

    private <T> List<T> tail(List<T> list, int limit) {
        int from = Math.max(0, list.size() - Math.max(limit, 0));
        return new ArrayList<>(list.subList(from, list.size()));
    }

    private Map<String, Object> ensureMap(Object value) {
        return value instanceof Map<?, ?> raw ? toMap(raw) : new LinkedHashMap<>();
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> raw ? toMap(raw) : null;
    }

    private Map<String, Object> toMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private int getInt(String key, int defaultValue) {
        return toInt(config.get(key), defaultValue);
    }

    private double getDouble(String key, double defaultValue) {
        return toDouble(config.get(key), defaultValue);
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        return toDouble(map.get(key), defaultValue);
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
