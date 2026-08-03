/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base method class for tool optimization.
 *
 * <p>Mirrors Python's {@code BaseMethod} and module helpers in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/base_method.py}.</p>
 */
public class BaseMethod {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected final Map<String, Object> config;
    protected final boolean verbose;

    public BaseMethod(Map<String, Object> config) {
        this.config = config == null ? Map.of() : config;
        this.verbose = truthy(this.config.get("verbose"));
    }

    public static Object parseJson(String output) {
        return parseJson(output, null);
    }

    public static Object parseJson(String output, String header) {
        return FormatUtils.parseJson(output, header);
    }

    public static String formatPromptLlama(String systemPrompt, String userPrompt) {
        return FormatUtils.formatPromptLlama(systemPrompt, userPrompt);
    }

    public static void printBold(String text) {
        // Python implementation only constructs ANSI variables and returns None.
    }

    /**
     * Produce a short natural-language answer from API call output.
     *
     * @param instruction user instruction
     * @param docStr tool documentation
     * @param apiResponse raw API response
     * @return answer text
     */
    public String produceAnswerFromApiCall(String instruction, String docStr, String apiResponse) {
        String userPrompt = """
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
You must strictly follow the output format. You can begin your task now.""".formatted(docStr, instruction, apiResponse);

        Function<String, Object> verifyOutput = output -> {
            Object parsed = parseJson(output);
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
            return answer == null ? "" : String.valueOf(answer).strip();
        };

        String prompt = formatPromptLlama("", userPrompt);
        Object output = invokeRitsResponse(
                stringValue(config.get("gen_model_id")),
                prompt,
                stringValue(config.get("llm_api_key")),
                verifyOutput,
                Map.of(
                        "max_attempts", 15,
                        "include_stop_sequence", false,
                        "stop_sequences", List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"),
                        "verbose", verbose
                )
        );
        return output == null ? "" : String.valueOf(output);
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object invokeRitsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn,
            Map<String, Object> kwargs
    ) {
        try {
            Class<?> ritsClass = Class.forName(
                    "com.openjiuwen.agentevolving.optimizer.tool_call.utils.RitsUtils"
            );
            Method method = ritsClass.getMethod(
                    "getRitsResponse",
                    String.class,
                    String.class,
                    String.class,
                    Function.class,
                    boolean.class,
                    Map.class
            );
            return method.invoke(null, modelId, prompt, llmApiKey, verifyFn, verbose, kwargs);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("RitsUtils dependency is unavailable", exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("RitsUtils dependency could not be invoked", exception);
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String text) {
            String stripped = text.strip();
            return "true".equalsIgnoreCase(stripped) || "1".equals(stripped);
        }
        return false;
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
}
