// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.Map;

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
        this.verbose = this.config.containsKey("verbose") 
                && Boolean.TRUE.equals(this.config.get("verbose"));
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

        // Call LLM API
        String prompt = FormatUtils.formatPromptLlama("", userPrompt);
        String output = RitsUtils.getRitsResponse(
                (String) config.get("gen_model_id"),
                prompt,
                (String) config.get("llm_api_key")
        );

        if (verbose) {
            Loggers.AGENT.info("Final LLM output: {}", output);
        }
        return output;
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
}