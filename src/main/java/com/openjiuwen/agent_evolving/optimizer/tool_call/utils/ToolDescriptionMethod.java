/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class ToolDescriptionMethod extends BaseMethod {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() { };
    private static final double PERFORMANCE_THRESHOLD = 60.0d;

    private final Object evalFn;

    public ToolDescriptionMethod(Map<String, Object> config, Object evalFn) {
        super(config);
        this.evalFn = evalFn;
    }

    public StepResult step(Map<String, Object> tool, List<Object> examples, List<Object> prevOutputs, int it) {
        Map<String, Object> output;
        if (it == 0) {
            output = new LinkedHashMap<>();
            output.put("description", getOriginalDescription(tool));
            output.put("iteration", 0);
        } else {
            Map<String, Object> exampleBundle = new LinkedHashMap<>();
            exampleBundle.put("examples", examples != null ? examples : List.of());
            exampleBundle.put("neg_examples", getNegativeExamples(String.valueOf(tool.get("name"))));
            output = generate(tool, exampleBundle, prevOutputs, it);
        }
        output.putAll(evalLoop(tool, String.valueOf(output.getOrDefault("description", "")), examples, 1));
        return new StepResult(output.get("description"), getDouble(output, "score_avg", 0.0d), output);
    }

    public Map<String, Object> generate(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs,
            int it
    ) {
        Map<String, Object> output = generateDescriptionFromDocumentation(tool, examples, prevOutputs);
        output.put("iteration", it);
        return output;
    }

    public Map<String, Object> generateDescriptionFromDocumentation(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs,
            int it
    ) {
        return generate(tool, examples, prevOutputs, it);
    }

    public Map<String, Object> generateDescriptionFromDocumentation(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs
    ) {
        List<Object> pos = asList(examples != null ? examples.get("examples") : null);
        List<Object> neg = asList(examples != null ? examples.get("neg_examples") : null);
        Map<String, Object> descAnalysis = critiqueDescriptions(tool, pos, prevOutputs);
        Map<String, Object> contrastAnalysis = critiqueAllDescriptions(tool, Map.of("examples", pos, "neg_examples", neg), prevOutputs);

        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        List<Map<String, Object>> previous = asMapList(prevOutputs);
        if (!pos.isEmpty() && !previous.isEmpty()) {
            prompt.append("Previous descriptions:\n");
            for (Map<String, Object> output : tail(previous, getInt("num_feedback_steps", 2))) {
                Object iteration = output.get("iteration");
                prompt.append(Objects.equals(iteration, 0) ? "Original description: " : "Iteration #" + iteration + ", description=");
                prompt.append(String.valueOf(output.getOrDefault("description", ""))).append("\n");
                prompt.append("score=").append(output.getOrDefault("score_avg", 0)).append("%, stdev=")
                        .append(output.getOrDefault("score_std", 0)).append("%.\n");
            }
            prompt.append("Analysis: ").append(String.valueOf(descAnalysis.getOrDefault("analysis", ""))).append("\n");
            prompt.append("Contrast: ").append(String.valueOf(contrastAnalysis.getOrDefault("analysis", ""))).append("\n");
        }
        prompt.append("Return JSON only in the form {\"description\": ...}. Keep the schema structure, improve only the description text.");

        Function<String, Object> verify = output -> {
            Object parsed = FormatUtils.parseJson(output, "description");
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            Map<String, Object> result = toMap(raw);
            if (!result.containsKey("description")) {
                throw new IllegalArgumentException("No \"description\" found in output");
            }
            result.put("description", stringify(result.get("description")));
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

    public Map<String, Object> evalLoop(Map<String, Object> tool, String description, List<Object> examples, int runs) {
        if (evalFn == null) {
            return defaultEval();
        }
        try {
            return ensureMap(invokeCallable(evalFn, List.of("evaluate", "call", "apply", "invoke"), tool, description, examples, runs));
        } catch (Exception e) {
            Loggers.AGENT.warn("Eval function invocation failed: {}", e.getMessage());
            return defaultEval();
        }
    }

    public Map<String, Object> critiqueDescriptions(Map<String, Object> tool, List<Object> examples, List<Object> prevOutputs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        List<Map<String, Object>> positives = new ArrayList<>();
        List<Map<String, Object>> negatives = new ArrayList<>();
        for (Map<String, Object> output : tail(asMapList(prevOutputs), getInt("num_feedback_steps", 2))) {
            if (getDouble(output, "score_avg", 0.0d) >= PERFORMANCE_THRESHOLD) {
                positives.add(output);
            } else {
                negatives.add(output);
            }
        }
        appendDescriptionSection(prompt, "positive", positives, examples);
        appendDescriptionSection(prompt, "negative", negatives, examples);
        prompt.append("Summarize the patterns in under 500 characters.");
        return invokeAnalysis(prompt.toString());
    }

    public Map<String, Object> critiqueNegativeExamples(Map<String, Object> tool, List<Object> examples) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        appendExamplePairs(prompt, asList(examples), false);
        prompt.append("Explain the failure patterns and capability limits in under 500 characters.");
        return invokeAnalysis(prompt.toString());
    }

    public Map<String, Object> critiqueAllDescriptions(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Documentation:\n").append(toJson(tool)).append("\n");
        prompt.append("Positive examples:\n");
        appendExamplePairs(prompt, asList(examples != null ? examples.get("examples") : null), true);
        prompt.append("Negative examples:\n");
        appendExamplePairs(prompt, asList(examples != null ? examples.get("neg_examples") : null), false);
        prompt.append("Compare successful and unsuccessful patterns in under 500 characters.");
        return invokeAnalysis(prompt.toString());
    }

    public List<Object> loadExamples(String examplesDir, String functionName, int maxNumExamples) {
        try {
            Path path = Path.of(examplesDir, functionName + ".json");
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            List<Object> allOutputs = OBJECT_MAPPER.readValue(Files.readString(path), LIST_TYPE);
            List<Object> selected = new ArrayList<>();
            for (Object nodeHistory : allOutputs) {
                for (Object step : reverse(asList(nodeHistory))) {
                    Map<String, Object> map = asMap(step);
                    if (map == null) {
                        continue;
                    }
                    Double score = lastNumber(map.get("scores"));
                    String inst = lastString(map.get("instructions"));
                    String ans = lastString(map.get("answers"));
                    if (score != null && score >= 3.0d && inst != null && ans != null) {
                        selected.add(new Object[]{inst.trim(), map.get("fn_call"), map.get("tool_results"), ans.trim()});
                        break;
                    }
                }
                if (selected.size() >= maxNumExamples) {
                    break;
                }
            }
            return selected;
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to load examples: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Object> getNegativeExamples(String functionName) {
        try {
            String negPath = getString("neg_ex_input_path");
            Path path;
            if (negPath != null && Files.exists(Path.of(negPath))) {
                path = Path.of(negPath);
            } else {
                String examplesDir = getString("examples_dir");
                if (examplesDir == null) {
                    return new ArrayList<>();
                }
                path = Path.of(examplesDir, functionName + ".json");
            }
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            List<Object> allOutputs = OBJECT_MAPPER.readValue(Files.readString(path), LIST_TYPE);
            List<Object> selected = new ArrayList<>();
            for (Object nodeHistory : allOutputs) {
                for (Object step : reverse(asList(nodeHistory))) {
                    Map<String, Object> map = asMap(step);
                    if (map == null || !map.keySet().containsAll(List.of("instructions", "fn_call", "tool_results", "answers"))) {
                        continue;
                    }
                    String inst = lastString(map.get("instructions"));
                    String ans = lastString(map.get("answers"));
                    if (inst == null || ans == null) {
                        continue;
                    }
                    Double score = lastNumber(map.get("scores"));
                    if (score == null || (score >= 1.0d && score < 3.0d)) {
                        selected.add(new Object[]{inst.trim(), map.get("fn_call"), map.get("tool_results"), ans.trim()});
                        if (selected.size() >= getInt("num_examples_for_desc", 4)) {
                            return selected;
                        }
                    }
                }
            }
            return selected;
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to load negative examples: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public String getOriginalDescription(Map<String, Object> tool) {
        String description = String.valueOf(tool.getOrDefault("description", ""));
        String indicator = "The description of this function is: \"";
        int found = description.indexOf(indicator);
        return found >= 0 && description.length() > indicator.length()
                ? description.substring(found + indicator.length(), description.length() - 1)
                : description;
    }

    public List<Object> getExamples(Map<String, Object> tool) {
        String examplesDir = getString("examples_dir");
        if (examplesDir == null) {
            return null;
        }
        List<Object> examples = loadExamples(examplesDir, String.valueOf(tool.get("name")), getInt("num_examples_for_desc", 4));
        Loggers.AGENT.info("{} Examples loaded for tool: {}: {}", examples.size(), tool.get("name"), examples);
        return examples;
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

    private Map<String, Object> invokeAnalysis(String prompt) {
        return ensureMap(invokeRitsResponse(
                String.valueOf(config.getOrDefault("eval_model_id", "")),
                FormatUtils.formatPromptLlama("", prompt),
                String.valueOf(config.getOrDefault("llm_api_key", "")),
                out -> Map.of("analysis", out == null ? "" : out.trim()),
                Map.of("max_attempts", 15, "include_stop_sequence", false)
        ));
    }

    private void appendDescriptionSection(StringBuilder prompt, String label, List<Map<String, Object>> outputs, List<Object> examples) {
        if (outputs.isEmpty()) {
            return;
        }
        prompt.append(label).append(" descriptions:\n");
        for (Map<String, Object> output : outputs) {
            prompt.append("description=").append(output.getOrDefault("description", "")).append("\n");
            prompt.append("score=").append(output.getOrDefault("score_avg", 0)).append(", stdev=").append(output.getOrDefault("score_std", 0)).append("\n");
            List<Object> results = asList(output.get("results"));
            int limit = Math.min(results.size(), examples != null ? examples.size() : 0);
            for (int i = 0; i < limit; i++) {
                Object[] tuple = tuple(examples.get(i));
                Map<String, Object> result = asMap(results.get(i));
                prompt.append(i + 1).append(". instruction=\"").append(tuple.length > 0 ? tuple[0] : "").append("\"")
                        .append(", answer=\"").append(result != null ? result.getOrDefault("answer", "") : "").append("\"")
                        .append(", errors=").append(formatErrors(result != null ? asList(result.get("errors")) : List.of()))
                        .append(", truth=").append(toJson(tuple.length > 1 ? tuple[1] : Map.of())).append("\n");
            }
        }
    }

    private void appendExamplePairs(StringBuilder prompt, List<Object> examples, boolean positive) {
        for (int i = 0; i < examples.size(); i++) {
            Object[] tuple = tuple(examples.get(i));
            prompt.append(i + 1).append(". instruction=\"").append(tuple.length > 0 ? tuple[0] : "").append("\"");
            prompt.append(", fn_call=").append(toJson(tuple.length > 1 ? tuple[1] : Map.of()));
            prompt.append(", output=").append(String.valueOf(tuple.length > 2 ? tuple[2] : ""));
            if (positive) {
                prompt.append(", answer=").append(String.valueOf(tuple.length > 3 ? tuple[3] : ""));
            }
            prompt.append("\n");
        }
    }

    private Object invokeCallable(Object target, List<String> names, Object... args) throws Exception {
        for (String name : names) {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException("Unsupported callable type: " + target.getClass().getName());
    }

    private Map<String, Object> defaultEval() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score_avg", 0.0d);
        result.put("score_std", 0.0d);
        result.put("results", new ArrayList<>());
        return result;
    }

    private String formatErrors(List<Object> errors) {
        if (errors.isEmpty()) {
            return "None";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            Map<String, Object> error = asMap(errors.get(i));
            if (i > 0) {
                text.append(" ");
            }
            text.append("(").append(i).append(") ");
            text.append(error != null ? error.getOrDefault("function_name", "") : "");
            text.append(" ").append(toJson(error != null ? error.getOrDefault("arguments", Map.of()) : Map.of()));
            text.append(" ").append(error != null ? error.getOrDefault("error_msg", "") : "");
        }
        return text.toString();
    }

    private List<Object> reverse(List<Object> list) {
        List<Object> copy = new ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private List<Map<String, Object>> tail(List<Map<String, Object>> list, int limit) {
        int from = Math.max(0, list.size() - Math.max(limit, 0));
        return new ArrayList<>(list.subList(from, list.size()));
    }

    private List<Object> asList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    private List<Map<String, Object>> asMapList(List<Object> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (Object value : values) {
            Map<String, Object> map = asMap(value);
            if (map != null) {
                result.add(map);
            }
        }
        return result;
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

    private Object[] tuple(Object value) {
        if (value instanceof Object[] array) {
            return array;
        }
        return value instanceof List<?> list ? list.toArray() : new Object[]{value};
    }

    private String lastString(Object value) {
        Object last = lastValue(value);
        return last instanceof String text ? text : null;
    }

    private Double lastNumber(Object value) {
        Object last = lastValue(value);
        if (last instanceof Number number) {
            return number.doubleValue();
        }
        if (last instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object lastValue(Object value) {
        return value instanceof List<?> list && !list.isEmpty() ? list.get(list.size() - 1) : value;
    }

    private String stringify(Object value) {
        if (value instanceof String text) {
            return text.trim();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value).trim();
        }
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String getString(String key) {
        Object value = config.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
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
