// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;

/**
 * API call to example method for generating test cases.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.toolcall_example_method.APICallToExampleMethod}.
 */
public class APICallToExampleMethod extends BaseMethod {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object runToolWithApiCall;
    private final Object evalFn;
    private final List<String> apiKeys;
    private final List<String> nonOptParams;

    /**
     * Create API call to example method.
     *
     * @param config       Configuration map
     * @param apiCallFn    API call function
     * @param evalFn       Evaluation function
     * @param apiKeys      API keys
     * @param nonOptParams Non-optimized parameters
     */
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
        this.nonOptParams = nonOptParams != null ? nonOptParams : new ArrayList<>();
    }

    /**
     * Execute a step in the optimization process.
     *
     * @param tool        Tool definition
     * @param prevOutputs Previous outputs
     * @param it          Iteration number
     * @return Step result
     */
    public StepResult step(
            Map<String, Object> tool,
            List<Object> prevOutputs,
            int it
    ) {
        Loggers.AGENT.info("Inside method, trying to step");

        List<Object> history = prevOutputs != null ? new ArrayList<>(prevOutputs) : new ArrayList<>();
        String description = getOriginalDescription(tool);
        Loggers.AGENT.info("Original desc obtained: {}", description);

        Map<String, Object> toolForOpt = new HashMap<>(tool);

        // 1. Rejection sampling
        Map<String, Object> outputs = null;
        for (int retry = 0; retry < getIntValue(config, "num_init_loop", 1); retry++) {
            Map<String, Object> fnCall = generateApiCallFromDescription(toolForOpt, 1, history);
            Loggers.AGENT.info("API call generation completed: {}", fnCall);

            Object[] toolResult = executeToolCall(toolForOpt, fnCall);
            String toolRes = (String) toolResult[0];
            int statusCode = (Integer) toolResult[1];

            outputs = new HashMap<>();
            outputs.put("fn_call", fnCall);
            outputs.put("tool_results", toolRes);
            outputs.put("status_code", statusCode);
            outputs.put("score", (double) statusCode);

            Loggers.AGENT.info("Run tool with api call completed, status code {}", statusCode);

            if (statusCode >= 0) {
                break;
            }
        }

        if (outputs == null) {
            return new StepResult(new ArrayList<>(), 0.0, new HashMap<>());
        }

        // 2. Q/A generation and refinement
        List<String> insts = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<String> analyses = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        List<String> refls = new ArrayList<>();

        Map<String, Object> instOutput = null;
        int numRefineSteps = getIntValue(config, "num_refine_steps", 1);

        for (int nRefine = 0; nRefine < numRefineSteps; nRefine++) {
            Map<String, Object> fnCall = (Map<String, Object>) outputs.get("fn_call");
            String toolRes = (String) outputs.get("tool_results");

            String inst = generateInstructionFromApiCall(toolForOpt, fnCall, toolRes, instOutput);
            String ans = produceAnswerFromApiCall(inst, toJson(toolForOpt), toolRes);
            Map<String, Object> instEval = critiqueInstruction(toolForOpt, inst, fnCall, toolRes, ans);

            insts.add(inst);
            answers.add(ans);
            scores.add(getDoubleValue(instEval, "score", 0.0));
            analyses.add((String) instEval.get("analysis"));

            String batchRefl = batchReflectionWithScores(toolForOpt, fnCall, insts, scores, analyses);
            refls.add(batchRefl);

            instOutput = new HashMap<>();
            int numFeedbackSteps = getIntValue(config, "num_feedback_steps", 2);
            int start = Math.max(0, insts.size() - numFeedbackSteps);
            instOutput.put("instructions", insts.subList(start, insts.size()));
            instOutput.put("scores", scores.subList(start, scores.size()));
            instOutput.put("batch_reflection", batchRefl);

            if (getDoubleValue(instEval, "score", 0.0) == 3.0) {
                break;
            }
        }

        // 3. Calculate final score
        double evalScore = 1.0;
        double scoreEvalWeight = getDoubleValue(config, "score_eval_weight", 0.0);

        if (scoreEvalWeight > 0 && !insts.isEmpty() && !answers.isEmpty()) {
            // Evaluation with downstream LLM
            evalScore = 1.0;
        }

        double finalScore = scores.isEmpty() ? 0.0 : scores.get(scores.size() - 1);
        finalScore += scoreEvalWeight * (1.0 - evalScore);

        outputs.put("answers", answers);
        outputs.put("instructions", insts);
        outputs.put("scores", scores);
        outputs.put("analyses", analyses);
        outputs.put("batch_reflections", refls);
        outputs.put("score", finalScore);

        return new StepResult(insts, finalScore, outputs);
    }

    /**
     * Generate API call from description.
     *
     * @param tool        Tool definition
     * @param numGen      Number of calls to generate
     * @param prevOutputs Previous outputs
     * @return Generated function call
     */
    public Map<String, Object> generateApiCallFromDescription(
            Map<String, Object> tool,
            int numGen,
            List<Object> prevOutputs
    ) {
        String functionName = (String) tool.get("name");
        String docStr = toJson(tool);

        // Build prompt and call LLM
        // Simplified implementation - returns basic structure
        Map<String, Object> fnCall = new HashMap<>();
        fnCall.put("name", functionName);
        fnCall.put("arguments", new HashMap<>());

        return fnCall;
    }

    /**
     * Generate instruction from API call.
     *
     * @param tool        Tool definition
     * @param fnCall      Function call
     * @param fnResponse  Function response
     * @param prevOutput  Previous output
     * @return Generated instruction
     */
    public String generateInstructionFromApiCall(
            Map<String, Object> tool,
            Map<String, Object> fnCall,
            String fnResponse,
            Map<String, Object> prevOutput
    ) {
        // Simplified implementation
        return "Generated instruction placeholder";
    }

    /**
     * Critique instruction.
     *
     * @param tool        Tool definition
     * @param instruction Instruction text
     * @param fnCall      Function call
     * @param fnResponse  Function response
     * @param answer      Answer text
     * @return Critique result
     */
    public Map<String, Object> critiqueInstruction(
            Map<String, Object> tool,
            String instruction,
            Map<String, Object> fnCall,
            String fnResponse,
            String answer
    ) {
        // Simplified implementation
        Map<String, Object> result = new HashMap<>();
        result.put("analysis", "");
        result.put("score", 2);
        return result;
    }

    /**
     * Batch reflection with scores.
     *
     * @param tool         Tool definition
     * @param fnCall       Function call
     * @param instructions Instructions list
     * @param scores       Scores list
     * @param analyses     Analyses list
     * @return Batch reflection
     */
    public String batchReflectionWithScores(
            Map<String, Object> tool,
            Map<String, Object> fnCall,
            List<String> instructions,
            List<Double> scores,
            List<String> analyses
    ) {
        // Simplified implementation
        return "Batch reflection placeholder";
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

    private Object[] executeToolCall(Map<String, Object> tool, Map<String, Object> fnCall) {
        // Simplified execution
        return new Object[]{"{}", 0};
    }

    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
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