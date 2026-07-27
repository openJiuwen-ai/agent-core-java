/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Tests for API-call example generation method.
 *
 * <p>Mirrors Python's {@code APICallToExampleMethod} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/toolcall_example_method.py}.</p>
 */
class APICallToExampleMethodTest {

    @Test
    void stepGeneratesInstructionAndStopsOnPerfectScore() {
        QueueMethod method = new QueueMethod(
                baseConfig(),
                (BiFunction<Map<String, Object>, Map<String, Object>, Object>) (tool, call) -> new Object[] {"tool-output", 0},
                null,
                null,
                null,
                "{\"name\":\"weather\",\"arguments\":{\"city\":\"Paris\"}}",
                "{\"analysis\":\"api ok\",\"err_code\":0}",
                "{\"instruction\":\"ask weather\"}",
                "{\"answer\":\"done\"}",
                "{\"analysis\":\"good\",\"score\":3}",
                " reflect "
        );

        APICallToExampleMethod.StepResult result = method.step(tool(), null, 0);

        assertEquals(3.0d, result.score, 1e-9);
        assertEquals(List.of("ask weather"), result.data);
        Map<?, ?> output = (Map<?, ?>) result.results;
        assertEquals(0, output.get("status_code"));
        assertEquals(List.of("done"), output.get("answers"));
        assertEquals(List.of("ask weather"), output.get("instructions"));
        assertEquals(List.of(3.0d), output.get("scores"));
        assertEquals(List.of("reflect"), output.get("batch_reflections"));
        assertEquals(6, method.prompts.size());
    }

    @Test
    void generateApiCallValidatesFunctionName() {
        QueueMethod method = new QueueMethod(
                baseConfig(),
                null,
                null,
                List.of("KEY"),
                List.of(),
                "{\"name\":\"other\",\"arguments\":{}}"
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> method.generateApiCallFromDescription(tool(), List.of("weather(city='Paris')"), 1,
                        List.of(linkedMap("fn_call", Map.of("name", "weather"), "status_code", -1,
                                "api_reflection", "bad args")))
        );

        assertTrue(error.getMessage().contains("Output function must match"));
        assertTrue(method.prompts.get(0).contains("Available API keys"));
        assertTrue(method.prompts.get(0).contains("Previous fn_call"));
    }

    @Test
    void critiqueApiCallParsesAnalysisAndErrorCode() {
        QueueMethod method = new QueueMethod(
                baseConfig(),
                null,
                null,
                null,
                null,
                "{\"analysis\":\"bad request\",\"err_code\":-1}"
        );

        Map<String, Object> result = method.critiqueApiCall(
                tool(),
                linkedMap("name", "weather", "arguments", Map.of()),
                "failed"
        );

        assertEquals("bad request", result.get("analysis"));
        assertEquals(-1, result.get("err_code"));
        assertTrue(method.prompts.get(0).contains("Function call"));
        assertTrue(method.prompts.get(0).contains("Execution result"));
    }

    @Test
    void batchReflectionTrimsModelOutput() {
        QueueMethod method = new QueueMethod(baseConfig(), null, null, null, null, "  improve params  ");

        String reflection = method.batchReflectionWithScores(
                tool(),
                linkedMap("name", "weather", "arguments", Map.of()),
                List.of("ask"),
                List.of(2.0d),
                List.of("too vague")
        );

        assertEquals("improve params", reflection);
        assertTrue(method.prompts.get(0).contains("score=2.0"));
    }

    @Test
    void getOriginalDescriptionHandlesIndicatorAndPlainText() {
        QueueMethod method = new QueueMethod(baseConfig(), null, null, null, null);

        assertEquals("Find weather", method.getOriginalDescription(linkedMap(
                "description", "The description of this function is: \"Find weather\""
        )));
        assertEquals("plain", method.getOriginalDescription(linkedMap("description", "plain")));
    }

    private static Map<String, Object> baseConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("gen_model_id", "gen");
        config.put("eval_model_id", "eval");
        config.put("llm_api_key", "key");
        config.put("verbose", false);
        config.put("num_init_loop", 1);
        config.put("num_refine_steps", 1);
        config.put("num_feedback_steps", 2);
        config.put("score_eval_weight", 0.0d);
        return config;
    }

    private static Map<String, Object> tool() {
        return linkedMap("name", "weather", "description", "The description of this function is: \"Find weather\"");
    }

    private static Map<String, Object> linkedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            map.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return map;
    }

    private static final class QueueMethod extends APICallToExampleMethod {

        private final Deque<String> responses = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();

        private QueueMethod(
                Map<String, Object> config,
                Object apiCallFn,
                Object evalFn,
                List<String> apiKeys,
                List<String> nonOptParams,
                String... responses
        ) {
            super(config, apiCallFn, evalFn, apiKeys, nonOptParams);
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected Object invokeRitsResponse(
                String modelId,
                String prompt,
                String llmApiKey,
                Function<String, Object> verifyFn,
                Map<String, Object> kwargs
        ) {
            prompts.add(prompt);
            String response = responses.isEmpty() ? "" : responses.removeFirst();
            return verifyFn.apply(response);
        }
    }
}
