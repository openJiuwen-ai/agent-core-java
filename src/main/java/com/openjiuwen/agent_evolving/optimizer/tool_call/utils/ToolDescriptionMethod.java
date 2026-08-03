/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Beam-search method for optimizing tool descriptions.
 *
 * <p>Mirrors Python's {@code ToolDescriptionMethod} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/description_example_method.py}.</p>
 */
public class ToolDescriptionMethod extends BaseMethod {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> LIST_OF_OBJECTS = new TypeReference<>() {
    };
    private static final double PERFORMANCE_THRESHOLD = 60.0d;

    private final Object evalFn;

    public ToolDescriptionMethod(Map<String, Object> config, Object evalFn) {
        super(config);
        this.evalFn = evalFn;
    }

    public BeamSearch.StepResult step(Map<String, Object> tool, List<Object> examples, int it) {
        return step(tool, examples, null, it);
    }

    public BeamSearch.StepResult step(
            Map<String, Object> tool,
            List<Object> examples,
            List<Object> prevOutputs,
            int it
    ) {
        Map<String, Object> output;
        if (it == 0) {
            String description = getOriginalDescription(tool);
            output = new LinkedHashMap<>();
            output.put("description", description);
            output.put("iteration", 0);
            Loggers.AGENT.info("Current description - original description: {}", output);
        } else {
            String functionName = stringValue(tool.get("name"));
            Map<String, Object> examplesObtained = new LinkedHashMap<>();
            examplesObtained.put("neg_examples", getNegativeExamples(functionName));
            examplesObtained.put("examples", examples);
            output = generate(tool, examplesObtained, prevOutputs, it);
            Loggers.AGENT.info("Current description - generated description: {}", output);
        }

        Map<String, Object> results = evalLoop(
                tool,
                stringValue(output.get("description")),
                examples,
                1
        );
        output.putAll(results);
        return new BeamSearch.StepResult(
                output.get("description"),
                doubleValue(output.get("score_avg"), 0.0d),
                output
        );
    }

    public Map<String, Object> generate(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs,
            int it
    ) {
        Loggers.AGENT.info("Generating desc");
        Map<String, Object> output = generateDescriptionFromDocumentation(tool, examples, prevOutputs);
        Loggers.AGENT.info("Generating desc finished");
        output.put("iteration", it);
        return output;
    }

    public Map<String, Object> evalLoop(
            Map<String, Object> tool,
            String description,
            List<Object> examples,
            int runs
    ) {
        if (evalFn == null) {
            throw new IllegalStateException("eval_fn is required");
        }
        try {
            return ensureMap(invokeCallable(
                    evalFn,
                    List.of("call", "evaluate", "apply", "invoke"),
                    tool,
                    description,
                    examples,
                    runs
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to invoke eval_fn", exception);
        }
    }

    public Map<String, Object> critiqueDescriptions(
            Map<String, Object> tool,
            List<Object> examples,
            List<Object> prevOutputs
    ) {
        String functionName = stringValue(tool.get("name"));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("""
        You are given a function %s with the following documentation, which includes the functionality description, required parameters, code snippets for API calls, etc.

        Documentation:
        %s

        """.formatted(functionName, toJson(tool)));

        List<Object> safeExamples = asList(examples);
        List<Map<String, Object>> previous = asMapList(prevOutputs);
        if (!safeExamples.isEmpty() && !previous.isEmpty()) {
            List<Map<String, Object>> positiveExamples = new ArrayList<>();
            List<Map<String, Object>> negativeExamples = new ArrayList<>();
            for (Map<String, Object> output : recent(previous, getInt("num_feedback_steps", 2))) {
                if (doubleValue(output.get("score_avg"), 0.0d) >= PERFORMANCE_THRESHOLD) {
                    positiveExamples.add(output);
                } else {
                    negativeExamples.add(output);
                }
            }
            appendDescriptionAnalysis(userPrompt, "POSITIVE EXAMPLES (Good Performance)",
                    "The following tool descriptions achieved good performance:", positiveExamples, safeExamples);
            appendDescriptionAnalysis(userPrompt, "NEGATIVE EXAMPLES (Poor Performance)",
                    "The following tool descriptions had poor performance:", negativeExamples, safeExamples);
            userPrompt.append("""

            Now your task is to critique the descriptions by comparing positive and negative examples. A good description maximizes the score, minimizes the stdev, and helps the assistant correctly use the function without errors. In your analysis:

            (1) POSITIVE PATTERN ANALYSIS: Identify what makes the high-performing descriptions (>60.0%) successful. What specific phrases, structures, or information do they contain that help the assistant use the function correctly?

            (2) NEGATIVE PATTERN ANALYSIS: Identify what causes low-performing descriptions to fail. What specific errors does the assistant make, and what aspects of these descriptions lead to confusion or incorrect function calls?

            (3) CONTRAST AND RECOMMENDATIONS: Compare positive vs negative patterns. What are the key differences? What specific improvements would transform a negative example into a positive one?

            Your analysis should be less than 500 characters long, do not violate.
            """);
        }
        return invokeAnalysis(userPrompt.toString());
    }

    public Map<String, Object> critiqueNegativeExamples(Map<String, Object> tool, List<Object> examples) {
        String functionName = stringValue(tool.get("name"));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("""
        You are given a function %s with the following documentation, which includes the functionality description, required parameters, code snippets for API calls, etc.

        Documentation:
        %s
        """.formatted(functionName, toJson(tool)));
        List<Object> safeExamples = asList(examples);
        if (!safeExamples.isEmpty()) {
            userPrompt.append("""

Previously, the given tool was used in solving instructions by a tool assistant with the following function descriptions:
Here are the instructions the assistant tried to solve with this tool description, with their corresponding answers and errors produced by the assistant:\s
""");
            for (int i = 0; i < safeExamples.size(); i++) {
                Object[] tuple = tuple(safeExamples.get(i));
                String fnOutput = stringValue(tupleValue(tuple, 2));
                if (fnOutput.length() > 256) {
                    fnOutput = fnOutput.substring(0, 256);
                    userPrompt.append("Example response of the function: ").append(fnOutput).append(", etc");
                } else {
                    userPrompt.append("Response of the function: ").append(fnOutput);
                }
                userPrompt.append(i + 1).append(". instruction=\"").append(stringValue(tupleValue(tuple, 0))).append("\"");
                userPrompt.append(". The system generated function call as below ");
                userPrompt.append("  base on the original documentation: ").append(toJson(tupleValue(tuple, 1))).append(".\n");
                userPrompt.append("The runction output obtained is ").append(fnOutput).append(": fn_output. ");
                userPrompt.append("And thus result to answer=\"").append(stringValue(tupleValue(tuple, 3))).append("\"");
            }
            userPrompt.append("""

            Now your task is to critique the descriptions based on these results. In your analysis:
            (1) Identify how the descriptions affect the function call errors of the assistant. Be specific on which errors the assistant tends to make, and find patterns in the description that causes the assistant to make such errors.
            (2) Identify any constrains or limitations the tool have. Analyze how the description can be improved so that it reflect the ability constrains.

            Your analysis should be less than 500 characters long, do not violate.
            """);
        }
        return invokeAnalysis(userPrompt.toString());
    }

    public Map<String, Object> critiqueAllDescriptions(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs
    ) {
        String functionName = stringValue(tool.get("name"));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("""
        You are given a function %s with the following documentation, which includes the functionality description, required parameters, code snippets for API calls, etc.

        Documentation:
        %s
        """.formatted(functionName, toJson(tool)));

        List<Object> positiveExamples = asList(examples == null ? null : examples.get("examples"));
        List<Object> negativeExamples = asList(examples == null ? null : examples.get("neg_examples"));
        if (!positiveExamples.isEmpty() || !negativeExamples.isEmpty()) {
            if (!positiveExamples.isEmpty()) {
                userPrompt.append("\n=== POSITIVE EXAMPLES (Good Performance) ===\n");
                userPrompt.append("The following examples achieved good performance:\n\n");
                appendExampleList(userPrompt, positiveExamples, true);
            }
            if (!negativeExamples.isEmpty()) {
                userPrompt.append("\n=== NEGATIVE EXAMPLES (Poor Performance) ===\n");
                userPrompt.append("The following tool descriptions had poor performance:\n\n");
                appendExampleList(userPrompt, negativeExamples, false);
            }
            userPrompt.append("""

            Now your task is to critique the descriptions by comparing positive and negative examples. In your analysis:

            (1) POSITIVE PATTERN ANALYSIS: Identify patterns in successful cases. What specific phrases, structures, or information do they contain that help the assistant use the function correctly?

            (2) NEGATIVE PATTERN ANALYSIS: Identify what causes un-successful cases. What specific errors does the assistant make, and what aspects of these descriptions lead to confusion or incorrect function calls?

            (3) CONTRAST AND RECOMMENDATIONS: Compare positive vs negative patterns. What are the key differences? Analyze carefully to uncover any unspecified constrains or limitations?

            Your analysis should be less than 500 characters long, do not violate.
            """);
        }
        return invokeAnalysis(userPrompt.toString());
    }

    public Map<String, Object> generateDescriptionFromDocumentation(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs
    ) {
        List<Object> pos = asList(examples == null ? null : examples.get("examples"));
        List<Object> neg = asList(examples == null ? null : examples.get("neg_examples"));
        Map<String, Object> tmp = critiqueDescriptions(tool, pos, prevOutputs);
        Map<String, Object> tmpContrast = critiqueAllDescriptions(
                tool,
                Map.of("examples", pos, "neg_examples", neg),
                prevOutputs
        );

        String analysis = stringValue(tmp.get("analysis"));
        String analysisContrast = stringValue(tmpContrast.get("analysis"));
        String functionName = stringValue(tool.get("name"));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("""
        You are given an API tool with the following documentation, which includes the functionality description, required parameters, code snippets for API calls, etc.

        Documentation:
        %s

        """.formatted(toJson(tool)));

        if (!pos.isEmpty() && prevOutputs != null && !prevOutputs.isEmpty()) {
            userPrompt.append("\nPreviously, the given tool was used in solving instructions by a tool assistant with the following function descriptions:\n");
            for (Map<String, Object> output : recent(asMapList(prevOutputs), getInt("num_feedback_steps", 2))) {
                if (Objects.equals(output.get("iteration"), 0)) {
                    userPrompt.append("Original description: ");
                } else {
                    userPrompt.append("Iteration #").append(output.get("iteration")).append(", description=");
                }
                userPrompt.append(stringValue(output.get("description"))).append("\n");
                userPrompt.append("Performance of this description is: ");
                userPrompt.append(" score=").append(output.get("score_avg")).append("%, stdev=")
                        .append(output.get("score_std")).append(".\n");
            }
            userPrompt.append("\nFurthermore, an analysis was performed on the descriptions for the previous iterations: \"")
                    .append(analysis)
                    .append("\". An analysis was performed on the negative cases for the constrains and ability limits of the function: \"")
                    .append(analysisContrast)
                    .append("\"");
            userPrompt.append("""

Your task is to further enhance the description for the function %s to MODIFY THE TOOL DESCRIPTION and PARAMETER DESCRIPTION part, with the objective of maximizing the score, minimizing the stdev, and help the assistant correctly use the function without errors.\s\s

Incorporate the analysis and generate the enhanced descriptions. The enhanced description should focus on what this tool can or cannot do, and add the capability boundaries of the tool, e.g., "returns summaries, not full text", "covers domestic locations only", "supports English language only", etc.

The enhanced description should not be longer than 1000 characters, do not violate this.
""".formatted(functionName));
        }

        String desiredDescSchema = toJson(Map.of(
                "type", "",
                "name", "",
                "description", "",
                "parameters", Map.of(
                        "type", "",
                        "properties", Map.of(
                                "<PARAMETER_NAME_0>", Map.of("type", "", "description", ""),
                                "<PARAMETER_NAME_1>", Map.of("type", "", "description", "")
                        ),
                        "required", List.of("<PARAMETER_NAME>")
                )
        ));
        userPrompt.append("""

**IMPORTANT**: You must preserve the exact JSON schema structure provided below. Only modify the text content - do not change schema structure.

**IMPORTANT**: Since no extra fields can be added, include capability boundaries within the main tool description text. Be explicit about what the function CANNOT do to prevent misuse.

**Required Output Format:**
Return JSON following this exact schema structure (modify only description texts):
{
    "description": %s
}

**Critical**: Maintain all field names, types, and schema structure. Only enhance the textual detail contents.
""".formatted(desiredDescSchema));

        Function<String, Object> verifyOutput = output -> {
            Object parsed = FormatUtils.parseJson(output, "description");
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            Map<String, Object> outputJson = toStringMap(raw);
            if (!outputJson.containsKey("description")) {
                throw new AssertionError("No \"description\" found in output");
            }
            outputJson.put("description", stringValue(outputJson.get("description")).strip());
            return outputJson;
        };
        return ensureMap(invokeRitsResponse(
                stringValue(config.get("gen_model_id")),
                FormatUtils.formatPromptLlama("", userPrompt.toString()),
                stringValue(config.get("llm_api_key")),
                verifyOutput,
                Map.of(
                        "max_attempts", 15,
                        "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"),
                        "verbose", config.get("verbose")
                )
        ));
    }

    public List<Object> loadExamples(String examplesDir, String functionName, int maxNumExamples) {
        Path examplesPath = Path.of(examplesDir, functionName + ".json");
        Loggers.AGENT.info("Trying to load examples from {}", examplesPath);
        try {
            List<Object> allOutputs = OBJECT_MAPPER.readValue(Files.readString(examplesPath), LIST_OF_OBJECTS);
            if (allOutputs == null) {
                throw new IllegalStateException("examples file parsed to null");
            }
            List<Object> selectedExamples = new ArrayList<>();
            for (Object nodeHistory : allOutputs) {
                for (Object stepOutputValue : reverse(asList(nodeHistory))) {
                    Map<String, Object> stepOutput = asMap(stepOutputValue);
                    if (stepOutput == null) {
                        continue;
                    }
                    Double score = lastNumber(stepOutput.get("scores"));
                    String inst = lastString(stepOutput.get("instructions"));
                    String ans = lastString(stepOutput.get("answers"));
                    if (score != null && score >= 3.0d && inst != null && ans != null) {
                        selectedExamples.add(new Object[] {
                                inst.strip(),
                                stepOutput.get("fn_call"),
                                stepOutput.get("tool_results"),
                                ans.strip()
                        });
                        break;
                    }
                }
            }
            return selectedExamples.subList(0, Math.min(selectedExamples.size(), maxNumExamples));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load examples from " + examplesPath, exception);
        }
    }

    public List<Object> getNegativeExamples(String functionName) {
        String examplesPathText = stringValue(config.get("neg_ex_input_path"));
        int maxNumExamples = getInt("num_examples_for_desc", 4);
        Path examplesPath = Path.of(examplesPathText);
        if (!Files.exists(examplesPath)) {
            Loggers.AGENT.warn("NO NEGATIVE FILE FOUND at {}, FALLBACK TO LOAD GENERATED EXAMPLES", examplesPath);
            examplesPath = Path.of(stringValue(config.get("examples_dir")), functionName + ".json");
        }
        try {
            List<Object> allOutputs = OBJECT_MAPPER.readValue(Files.readString(examplesPath), LIST_OF_OBJECTS);
            if (allOutputs == null) {
                throw new IllegalStateException("negative examples file parsed to null");
            }
            List<Object> selectedExamples = new ArrayList<>();
            for (Object nodeHistory : allOutputs) {
                for (Object stepOutputValue : reverse(asList(nodeHistory))) {
                    Map<String, Object> stepOutput = asMap(stepOutputValue);
                    if (stepOutput == null
                            || !stepOutput.keySet().containsAll(List.of("instructions", "fn_call", "tool_results", "answers"))) {
                        continue;
                    }
                    String inst = lastString(stepOutput.get("instructions"));
                    String ans = lastString(stepOutput.get("answers"));
                    if (inst == null || ans == null) {
                        continue;
                    }
                    Double score = lastNumber(stepOutput.get("scores"));
                    if (score == null || (score >= 1.0d && score < 3.0d)) {
                        selectedExamples.add(new Object[] {
                                inst.strip(),
                                stepOutput.get("fn_call"),
                                stepOutput.get("tool_results"),
                                ans.strip()
                        });
                    }
                }
            }
            return selectedExamples.subList(0, Math.min(selectedExamples.size(), maxNumExamples));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load negative examples from " + examplesPath, exception);
        }
    }

    public String getOriginalDescription(Map<String, Object> tool) {
        String description = stringValue(tool.get("description"));
        String indicator = "The description of this function is: \"";
        int found = description.indexOf(indicator);
        return found != -1 ? description.substring(found + indicator.length(), description.length() - 1) : description;
    }

    public List<Object> getExamples(Map<String, Object> tool) {
        String functionName = stringValue(tool.get("name"));
        List<Object> examples = null;
        if (config.get("examples_dir") != null) {
            examples = loadExamples(
                    stringValue(config.get("examples_dir")),
                    functionName,
                    getInt("num_examples_for_desc", 4)
            );
        }
        Loggers.AGENT.info("{} Examples loaded for tool: {}: {}",
                examples == null ? 0 : examples.size(), functionName, examples);
        return examples;
    }

    private Map<String, Object> invokeAnalysis(String userPrompt) {
        Function<String, Object> verifyOutput = output -> Map.of("analysis", output == null ? "" : output.strip());
        return ensureMap(invokeRitsResponse(
                stringValue(config.get("eval_model_id")),
                FormatUtils.formatPromptLlama("", userPrompt),
                stringValue(config.get("llm_api_key")),
                verifyOutput,
                Map.of(
                        "max_attempts", 15,
                        "include_stop_sequence", false,
                        "verbose", config.get("verbose")
                )
        ));
    }

    private void appendDescriptionAnalysis(
            StringBuilder prompt,
            String title,
            String intro,
            List<Map<String, Object>> outputs,
            List<Object> examples
    ) {
        if (outputs.isEmpty()) {
            return;
        }
        prompt.append("\n=== ").append(title).append(" ===\n");
        prompt.append(intro).append("\n\n");
        for (Map<String, Object> output : outputs) {
            if (Objects.equals(output.get("iteration"), 0)) {
                prompt.append("Original description: ");
            } else {
                prompt.append("Iteration #").append(output.get("iteration")).append(", description=");
            }
            prompt.append(stringValue(output.get("description"))).append("\n");
            prompt.append(title.startsWith("POSITIVE") ? "Instructions solved successfully: " : "Instructions with problems: ");
            appendOutputExamples(prompt, examples, asList(output.get("results")));
            prompt.append("Performance: score=").append(output.get("score_avg"))
                    .append("%, stdev=").append(output.get("score_std")).append(".\n\n");
        }
    }

    private void appendOutputExamples(StringBuilder prompt, List<Object> examples, List<Object> results) {
        int limit = Math.min(examples.size(), results.size());
        for (int index = 0; index < limit; index++) {
            Object[] example = tuple(examples.get(index));
            Map<String, Object> result = asMap(results.get(index));
            prompt.append(index + 1).append(". instruction=\"").append(stringValue(tupleValue(example, 0))).append("\"");
            prompt.append(", answer=\"").append(result == null ? "" : stringValue(result.get("answer"))).append("\", errors: ");
            List<Object> errors = result == null ? List.of() : asList(result.get("errors"));
            if (errors.isEmpty()) {
                prompt.append("None");
            } else {
                for (int errorIndex = 0; errorIndex < errors.size(); errorIndex++) {
                    Map<String, Object> error = asMap(errors.get(errorIndex));
                    prompt.append("(").append(errorIndex).append(") function_call=")
                            .append(error == null ? "" : stringValue(error.get("function_name")))
                            .append(", arguments=")
                            .append(toJson(error == null ? Map.of() : error.get("arguments")))
                            .append(", error=")
                            .append(truncate(error == null ? "" : stringValue(error.get("error_msg")), 512))
                            .append(" ");
                }
            }
            prompt.append(". Ground truth: ").append(toJson(tupleValue(example, 1))).append(".\n");
        }
    }

    private void appendExampleList(StringBuilder prompt, List<Object> examples, boolean positive) {
        for (int index = 0; index < examples.size(); index++) {
            Object[] example = tuple(examples.get(index));
            String fnOutput = stringValue(tupleValue(example, 2));
            prompt.append(index + 1).append(". instruction=\"").append(stringValue(tupleValue(example, 0))).append("\"");
            if (positive) {
                prompt.append(", Ground truth: ").append(toJson(tupleValue(example, 1))).append(".\n");
            } else {
                prompt.append(", The function call system generated: ").append(toJson(tupleValue(example, 1))).append(".");
            }
            if (fnOutput.length() > 256) {
                prompt.append("Example response of the function: ").append(fnOutput, 0, 256);
                if (positive) {
                    prompt.append(", etc");
                }
            } else {
                prompt.append("Response of the function: ").append(fnOutput);
            }
        }
    }

    private Object invokeCallable(Object target, List<String> methodNames, Object... args) throws Exception {
        for (String methodName : methodNames) {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException("Unsupported eval_fn type: " + target.getClass().getName());
    }

    private List<Map<String, Object>> recent(List<Map<String, Object>> values, int limit) {
        List<Map<String, Object>> reversed = new ArrayList<>(values);
        Collections.reverse(reversed);
        List<Map<String, Object>> sliced = reversed.subList(0, Math.min(reversed.size(), Math.max(limit, 0)));
        List<Map<String, Object>> result = new ArrayList<>(sliced);
        Collections.reverse(result);
        return result;
    }

    private List<Object> reverse(List<Object> values) {
        List<Object> result = new ArrayList<>(values);
        Collections.reverse(result);
        return result;
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
        return value instanceof Map<?, ?> raw ? toStringMap(raw) : new LinkedHashMap<>();
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> raw ? toStringMap(raw) : null;
    }

    private static Map<String, Object> toStringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Object[] tuple(Object value) {
        if (value instanceof Object[] values) {
            return values;
        }
        if (value instanceof List<?> values) {
            return values.toArray();
        }
        return new Object[] {value};
    }

    private Object tupleValue(Object[] tuple, int index) {
        return tuple != null && tuple.length > index ? tuple[index] : null;
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
        try {
            return last == null ? null : Double.parseDouble(String.valueOf(last));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Object lastValue(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.get(list.size() - 1);
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return stringValue(value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
