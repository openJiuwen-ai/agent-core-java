/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base method class for tool optimization.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.base_method.BaseMethod}.
 */
public abstract class BaseMethod {

    protected final Map<String, Object> config;
    protected final boolean verbose;

    /**
     * Create base method with configuration.
     *
     * @param config Configuration map
     */
    public BaseMethod(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
        this.verbose = isTruthy(this.config.get("verbose"));
    }

    /**
     * Produce answer from API call.
     *
     * @param instruction  Instruction text
     * @param docStr       Documentation string
     * @param apiResponse  API response
     * @return Answer string
     */
    public String produceAnswerFromApiCall(String instruction, String docStr, String apiResponse) {
        String userPrompt = String.format("""
Please respond in natural language text. Do not include code in your responses. You are given an API tool with the following documentation, which includes the functionality description, required parameters, code snippets for API calls, etc.

Documentation:
%s

You are given the following instruction: "%s"
To produce a response to the instruction, you made an API call to the given tool, which returned the following results:
%s

Given the instruction and the results of API call, produce an effective and short answer (less than 300 letters) to the user in natural language. Your answer must be based on the results of the API call, do not hallucinate or answer anything not in the API results. You must not include code, comments, JSON data structures, notes, or other irrelevant information in your answer. If there is an error or failure using the tool, you must report the error in your answer and do not make things up, especially when you receive an input about invalid parameters. Also, absolutely do NOT tell a user about a simulated response. Treat every successful API output as real. Every successful API call contains real data. This is very important.

Finally, organize your output in the following JSON format:
{
    "answer": answer
}
You must strictly follow the output format. You can begin your task now.
""", docStr, instruction, apiResponse);

        Function<String, Object> verifyOutput = output -> {
            Object parsed = FormatUtils.parseJson(output);
            if (!(parsed instanceof Map<?, ?> outputJson)) {
                throw new IllegalArgumentException("Output must be a dict.");
            }
            if (!outputJson.containsKey("answer")) {
                throw new IllegalArgumentException("\"answer\" field is required.");
            }
            if (outputJson.containsKey("error")) {
                throw new IllegalArgumentException(String.valueOf(outputJson.get("error")));
            }
            Object answer = outputJson.get("answer");
            return answer == null ? "" : String.valueOf(answer).trim();
        };

        String prompt = FormatUtils.formatPromptLlama("", userPrompt);
        Object output = invokeRitsResponse(
                (String) config.get("gen_model_id"),
                prompt,
                (String) config.get("llm_api_key"),
                verifyOutput,
                Map.of(
                        "max_attempts", 15,
                        "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>")
                )
        );

        if (verbose) {
            Loggers.AGENT.info("Final LLM output: {}", output);
        }
        if (output instanceof Map<?, ?> outputMap && outputMap.containsKey("error")) {
            throw new IllegalArgumentException(String.valueOf(outputMap.get("error")));
        }
        return output == null ? "" : String.valueOf(output);
    }

    /**
     * Get configuration.
     *
     * @return Configuration map
     */
    public Map<String, Object> getConfig() {
        return config;
    }

    /**
     * Check if verbose mode is enabled.
     *
     * @return True if verbose
     */
    public boolean isVerbose() {
        return verbose;
    }

    protected Object invokeRitsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn,
            Map<String, Object> kwargs
    ) {
        return RitsUtils.getRitsResponse(modelId, prompt, llmApiKey, verifyFn, verbose, kwargs);
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text.trim());
        }
        return false;
    }
}
