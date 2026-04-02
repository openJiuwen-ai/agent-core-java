// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Tool description optimization method.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.description_example_method.ToolDescriptionMethod}.
 */
public class ToolDescriptionMethod extends BaseMethod {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object evalFn;

    /**
     * Create tool description method.
     *
     * @param config Configuration map
     * @param evalFn Evaluation function
     */
    public ToolDescriptionMethod(Map<String, Object> config, Object evalFn) {
        super(config);
        this.evalFn = evalFn;
    }

    /**
     * Execute a step in the optimization process.
     *
     * @param tool        Tool definition
     * @param examples    Example cases
     * @param prevOutputs Previous outputs
     * @param it          Iteration number
     * @return Step result
     */
    public StepResult step(
            Map<String, Object> tool,
            List<Object> examples,
            List<Object> prevOutputs,
            int it
    ) {
        Map<String, Object> output;

        if (it == 0) {
            String description = getOriginalDescription(tool);
            output = new HashMap<>();
            output.put("description", description);
            output.put("iteration", 0);
            Loggers.AGENT.info("Current description - original: {}", output);
        } else {
            String functionName = (String) tool.get("name");
            List<Object> negExamples = getNegativeExamples(functionName);
            Map<String, Object> examplesObtained = new HashMap<>();
            examplesObtained.put("neg_examples", negExamples);
            examplesObtained.put("examples", examples);

            output = generateDescriptionFromDocumentation(tool, examplesObtained, prevOutputs, it);
            Loggers.AGENT.info("Current description - generated: {}", output);
        }

        // Evaluate with examples
        Map<String, Object> results = evalLoop(tool, (String) output.get("description"), examples, 1);
        output.putAll(results);

        return new StepResult(output.get("description"), getDoubleValue(output, "score_avg", 0.0), output);
    }

    /**
     * Generate description from documentation.
     *
     * @param tool        Tool definition
     * @param examples    Examples
     * @param prevOutputs Previous outputs
     * @param it          Iteration number
     * @return Generated description
     */
    public Map<String, Object> generateDescriptionFromDocumentation(
            Map<String, Object> tool,
            Map<String, Object> examples,
            List<Object> prevOutputs,
            int it
    ) {
        // Simplified implementation
        String description = getOriginalDescription(tool);
        Map<String, Object> result = new HashMap<>();
        result.put("description", description);
        result.put("iteration", it);
        return result;
    }

    /**
     * Evaluate description with examples.
     *
     * @param tool        Tool definition
     * @param description Description text
     * @param examples    Example cases
     * @param runs        Number of runs
     * @return Evaluation results
     */
    public Map<String, Object> evalLoop(
            Map<String, Object> tool,
            String description,
            List<Object> examples,
            int runs
    ) {
        // Simplified evaluation
        Map<String, Object> result = new HashMap<>();
        result.put("score_avg", 0.0);
        result.put("score_std", 0.0);
        result.put("results", new ArrayList<>());
        return result;
    }

    /**
     * Load examples from directory.
     *
     * @param examplesDir   Examples directory
     * @param functionName  Function name
     * @param maxNumExamples Maximum number of examples
     * @return List of examples
     */
    public List<Object> loadExamples(String examplesDir, String functionName, int maxNumExamples) {
        try {
            Path examplesPath = Paths.get(examplesDir, functionName + ".json");
            if (!Files.exists(examplesPath)) {
                return new ArrayList<>();
            }

            String content = Files.readString(examplesPath);
            List<?> allOutputs = OBJECT_MAPPER.readValue(content, List.class);

            List<Object> selectedExamples = new ArrayList<>();
            for (Object nodeHistory : allOutputs) {
                if (selectedExamples.size() >= maxNumExamples) {
                    break;
                }
                // Extract examples from node history
                selectedExamples.add(nodeHistory);
            }
            return selectedExamples;
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to load examples: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get negative examples for function.
     *
     * @param functionName Function name
     * @return List of negative examples
     */
    public List<Object> getNegativeExamples(String functionName) {
        String negExInputPath = (String) config.get("neg_ex_input_path");
        int numExamplesForDesc = getIntValue(config, "num_examples_for_desc", 4);

        try {
            Path examplesPath;
            if (negExInputPath != null && Files.exists(Paths.get(negExInputPath))) {
                examplesPath = Paths.get(negExInputPath);
            } else {
                String examplesDir = (String) config.get("examples_dir");
                if (examplesDir == null) {
                    return new ArrayList<>();
                }
                examplesPath = Paths.get(examplesDir, functionName + ".json");
            }

            if (!Files.exists(examplesPath)) {
                return new ArrayList<>();
            }

            String content = Files.readString(examplesPath);
            List<?> allOutputs = OBJECT_MAPPER.readValue(content, List.class);

            List<Object> selectedExamples = new ArrayList<>();
            for (Object nodeHistory : allOutputs) {
                if (selectedExamples.size() >= numExamplesForDesc) {
                    break;
                }
                selectedExamples.add(nodeHistory);
            }
            return selectedExamples;
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to load negative examples: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get original description from tool.
     *
     * @param tool Tool definition
     * @return Original description
     */
    public String getOriginalDescription(Map<String, Object> tool) {
        String description = (String) tool.get("description");
        String indicator = "The description of this function is: \"";
        int found = description != null ? description.indexOf(indicator) : -1;
        if (found != -1) {
            return description.substring(found + indicator.length(), description.length() - 1);
        }
        return description != null ? description : "";
    }

    /**
     * Get examples for tool.
     *
     * @param tool Tool definition
     * @return List of examples
     */
    public List<Object> getExamples(Map<String, Object> tool) {
        String functionName = (String) tool.get("name");
        String examplesDir = (String) config.get("examples_dir");

        if (examplesDir != null) {
            int numExamplesForDesc = getIntValue(config, "num_examples_for_desc", 4);
            return loadExamples(examplesDir, functionName, numExamplesForDesc);
        }
        return null;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Step result container.
     */
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
}
