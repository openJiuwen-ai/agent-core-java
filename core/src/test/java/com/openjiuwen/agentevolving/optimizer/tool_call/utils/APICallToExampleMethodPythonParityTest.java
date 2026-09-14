/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code APICallToExampleMethod} unit tests in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_toolcall_example_method.py}.
 */
class APICallToExampleMethodPythonParityTest {

    @Test
    void testGetOriginalDescription() {
        ScriptedMethod method = new ScriptedMethod(config(), null, null);

        assertThat(method.getOriginalDescription(tool())).isEqualTo("desc");
        assertThat(method.getOriginalDescription(map("name", "x", "description", "plain"))).isEqualTo("plain");
    }

    @Test
    void testGenerateApiCallFromDescription() {
        ScriptedMethod method = new ScriptedMethod(
                config(),
                null,
                null,
                "{\"name\":\"search\",\"arguments\":{\"q\":\"x\"}}"
        );

        Map<String, Object> out = method.generateApiCallFromDescription(
                tool(),
                null,
                1,
                List.of(map("fn_call", Map.of("name", "search"), "tool_results", Map.of("ok", 1),
                        "status_code", 0))
        );

        assertThat(method.models).containsExactly("gpt-gen");
        assertThat(method.prompts.get(0)).contains("search");
        assertThat(out).containsEntry("name", "search");
        assertThat(objectMap(out.get("arguments"))).containsEntry("q", "x");
    }

    @Test
    void testGenerateApiCallFromDescriptionValidationError() {
        ScriptedMethod method = new ScriptedMethod(
                config(),
                null,
                null,
                "{\"name\":\"other\",\"arguments\":{}}"
        );

        assertThatThrownBy(() -> method.generateApiCallFromDescription(tool(), null, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Output function must match");
    }

    @Test
    void testCritiqueAndInstructionAndBatchMethods() {
        ScriptedMethod method = new ScriptedMethod(
                config(),
                null,
                null,
                "{\"analysis\":\"ok\",\"err_code\":0}",
                "{\"instruction\":\"I need weather in Beijing\"}",
                "{\"analysis\":\"good\",\"score\":3}",
                "reflection"
        );
        Map<String, Object> fnCall = map("name", "search", "arguments", Map.of("q", "x"));

        Map<String, Object> critique = method.critiqueApiCall(tool(), fnCall, "r".repeat(3000));
        String instruction = method.generateInstructionFromApiCall(
                tool(),
                fnCall,
                "resp",
                map("instructions", List.of("a"), "scores", List.of(1), "batch_reflection", "b")
        );
        Map<String, Object> scored = method.critiqueInstruction(tool(), "inst", fnCall, "resp", "ans");
        String reflection = method.batchReflectionWithScores(tool(), fnCall, List.of("i1"), List.of(2.0d),
                List.of("a1"));

        assertThat(critique).containsEntry("err_code", 0);
        assertThat(instruction).contains("Beijing");
        assertThat(scored).containsEntry("score", 3);
        assertThat(reflection).isEqualTo("reflection");
    }

    @Test
    void testStepFullFlow() {
        RecordingEval eval = new RecordingEval();
        FullFlowMethod method = new FullFlowMethod(
                config(),
                (BiFunction<Map<String, Object>, Map<String, Object>, Object>) (tool, call) ->
                        new Object[] {"{\"response\":\"ok\"}", 0},
                eval
        );

        APICallToExampleMethod.StepResult result = method.step(tool(), List.of(), 0);
        Map<String, Object> outputs = objectMap(result.results);

        assertThat(result.data).isEqualTo(List.of("inst-1", "inst-2"));
        assertThat(outputs).containsEntry("status_code", 0);
        assertThat((List<?>) outputs.get("scores")).last().isEqualTo(3.0d);
        assertThat(result.score).isCloseTo(3.25d, org.assertj.core.data.Offset.offset(1e-9));
        List<?> firstExample = (List<?>) eval.examples.get(0);
        assertThat(firstExample.get(0)).isEqualTo("inst-2");
    }

    private static Map<String, Object> config() {
        return map(
                "gen_model_id", "gpt-gen",
                "eval_model_id", "gpt-eval",
                "llm_api_key", "k",
                "verbose", false,
                "num_init_loop", 2,
                "num_refine_steps", 2,
                "num_feedback_steps", 1,
                "score_eval_weight", 0.5d
        );
    }

    private static Map<String, Object> tool() {
        return map("name", "search", "description", "The description of this function is: \"desc\"");
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            result.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static class ScriptedMethod extends APICallToExampleMethod {
        private final Deque<String> responses = new ArrayDeque<>();
        private final List<String> models = new ArrayList<>();
        private final List<String> prompts = new ArrayList<>();

        ScriptedMethod(Map<String, Object> config, Object apiCallFn, Object evalFn, String... responses) {
            super(config, apiCallFn, evalFn, null, null);
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected Object invokeRitsResponse(String modelId, String prompt, String llmApiKey,
                                            Function<String, Object> verifyFn, Map<String, Object> kwargs) {
            models.add(modelId);
            prompts.add(prompt);
            return verifyFn.apply(responses.removeFirst());
        }
    }

    private static final class FullFlowMethod extends APICallToExampleMethod {
        private final Deque<Map<String, Object>> critiqueResponses = new ArrayDeque<>(List.of(
                map("analysis", "bad", "err_code", -1),
                map("analysis", "", "err_code", 0)
        ));
        private final Deque<String> instructions = new ArrayDeque<>(List.of("inst-1", "inst-2"));
        private final Deque<String> answers = new ArrayDeque<>(List.of("ans-1", "ans-2"));
        private final Deque<Map<String, Object>> scoreResponses = new ArrayDeque<>(List.of(
                map("analysis", "a", "score", 2),
                map("analysis", "b", "score", 3)
        ));

        private FullFlowMethod(Map<String, Object> config, Object apiCallFn, Object evalFn) {
            super(config, apiCallFn, evalFn, null, null);
        }

        @Override
        public Map<String, Object> generateApiCallFromDescription(
                Map<String, Object> tool, List<String> exampleCalls, int numGen, List<Object> prevOutputs) {
            return map("name", "search", "arguments", Map.of("q", "x"));
        }

        @Override
        public Map<String, Object> critiqueApiCall(Map<String, Object> tool, Map<String, Object> fnCall,
                                                   String fnResponse) {
            return critiqueResponses.removeFirst();
        }

        @Override
        public String generateInstructionFromApiCall(Map<String, Object> tool, Map<String, Object> fnCall,
                                                     String fnResponse, Map<String, Object> prevOutput) {
            return instructions.removeFirst();
        }

        @Override
        public String produceAnswerFromApiCall(String instruction, String docStr, String apiResponse) {
            return answers.removeFirst();
        }

        @Override
        public Map<String, Object> critiqueInstruction(Map<String, Object> tool, String instruction,
                                                       Map<String, Object> fnCall, String fnResponse,
                                                       String answer) {
            return scoreResponses.removeFirst();
        }

        @Override
        public String batchReflectionWithScores(Map<String, Object> tool, Map<String, Object> fnCall,
                                                List<String> instructions, List<Double> scores,
                                                List<String> analyses) {
            return "refl";
        }
    }

    public static final class RecordingEval {
        private List<Object> examples = List.of();

        public Map<String, Object> call(Map<String, Object> tool, String description, List<Object> examples, int runs) {
            this.examples = List.copyOf(examples);
            return Map.of("score_avg", 50);
        }
    }
}
