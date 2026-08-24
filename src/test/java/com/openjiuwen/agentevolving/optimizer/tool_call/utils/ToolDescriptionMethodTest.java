/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tests for tool description beam-search method.
 *
 * <p>Mirrors Python's {@code ToolDescriptionMethod} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/description_example_method.py}.</p>
 *
 * <p>Mirrors Python's {@code test_description_example_method} module in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_description_example_method.py}.</p>
 */
class ToolDescriptionMethodTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void stepAtInitialIterationUsesOriginalDescriptionAndEvalLoop() {
        ToolDescriptionMethod method = new ToolDescriptionMethod(baseConfig(), new RecordingEval(88.0d));
        Map<String, Object> tool = linkedMap(
                "name", "weather",
                "description", "The description of this function is: \"Find weather\""
        );

        BeamSearch.StepResult result = method.step(tool, List.of("example"), 0);

        assertEquals("Find weather", result.data);
        assertEquals(88.0d, result.score, 1e-9);
        Map<?, ?> output = (Map<?, ?>) result.results;
        assertEquals("Find weather", output.get("description"));
        assertEquals(0, output.get("iteration"));
        assertEquals(88.0d, output.get("score_avg"));
    }

    @Test
    void stepAtLaterIterationLoadsNegativeExamplesAndGeneratesDescription() {
        GenerateRecordingMethod method = new GenerateRecordingMethod(baseConfig(), new RecordingEval(66.0d));
        List<Object> examples = List.of(tuple("pos", linkedMap("name", "weather"), "out", "ans"));

        BeamSearch.StepResult result = method.step(tool(), examples, List.of(linkedMap("description", "old")), 2);

        assertEquals("generated", result.data);
        assertEquals(66.0d, result.score, 1e-9);
        assertSame(examples, method.generateExamples.get("examples"));
        assertEquals(List.of("negative-weather"), method.generateExamples.get("neg_examples"));
        assertEquals(2, ((Map<?, ?>) result.results).get("iteration"));
    }

    @Test
    void critiqueDescriptionsSeparatesPositiveAndNegativeOutputs() {
        RitsRecordingMethod method = new RitsRecordingMethod(baseConfig(), new RecordingEval(1.0d), null);
        List<Object> examples = List.of(tuple("inst", linkedMap("name", "weather"), "out", "ans"));
        List<Object> previous = List.of(
                linkedMap("iteration", 0, "description", "good desc", "score_avg", 80.0d,
                        "score_std", 1.0d, "results", List.of(linkedMap("answer", "ok", "errors", List.of()))),
                linkedMap("iteration", 1, "description", "bad desc", "score_avg", 20.0d,
                        "score_std", 4.0d, "results", List.of(linkedMap("answer", "bad", "errors",
                                List.of(linkedMap("function_name", "weather", "arguments", Map.of(), "error_msg", "wrong")))))
        );

        Map<String, Object> result = method.critiqueDescriptions(tool(), examples, previous);

        assertEquals("analysis text", result.get("analysis"));
        assertEquals("eval", method.modelId);
        assertTrue(method.prompt.contains("POSITIVE EXAMPLES"));
        assertTrue(method.prompt.contains("NEGATIVE EXAMPLES"));
        assertTrue(method.prompt.contains("good desc"));
        assertTrue(method.prompt.contains("bad desc"));
    }

    @Test
    void generateDescriptionVerifiesDescriptionJson() {
        RitsRecordingMethod method = new RitsRecordingMethod(baseConfig(), new RecordingEval(1.0d), null);
        method.generatedResponse = "prefix {\"description\":\" improved \"} suffix";
        Map<String, Object> examples = linkedMap(
                "examples", List.of(tuple("inst", Map.of(), "out", "ans")),
                "neg_examples", List.of()
        );
        List<Object> previous = List.of(linkedMap(
                "iteration", 0,
                "description", "old",
                "score_avg", 10.0d,
                "score_std", 1.0d,
                "results", List.of()
        ));

        Map<String, Object> output = method.generateDescriptionFromDocumentation(tool(), examples, previous);

        assertEquals("improved", output.get("description"));
        assertEquals("gen", method.modelId);
        assertTrue(method.prompt.contains("Required Output Format"));
        assertTrue(method.prompt.contains("analysis text"));
        assertTrue(method.prompt.contains("contrast text"));
    }

    @Test
    void loadExamplesSelectsFirstHighScoringReverseHistoryEntry() throws Exception {
        Path examplesPath = tempDir.resolve("weather.json");
        OBJECT_MAPPER.writeValue(examplesPath.toFile(), List.of(List.of(
                linkedMap("instructions", List.of("low"), "answers", List.of("bad"),
                        "scores", List.of(2.0d), "fn_call", Map.of("name", "weather"), "tool_results", "low-out"),
                linkedMap("instructions", List.of(" use weather "), "answers", List.of(" done "),
                        "scores", List.of(3.0d), "fn_call", Map.of("name", "weather"), "tool_results", "ok")
        )));
        ToolDescriptionMethod method = new ToolDescriptionMethod(baseConfig(), new RecordingEval(1.0d));

        List<Object> examples = method.loadExamples(tempDir.toString(), "weather", 3);

        assertEquals(1, examples.size());
        Object[] first = (Object[]) examples.get(0);
        assertEquals("use weather", first[0]);
        assertEquals("done", first[3]);
        assertEquals(1, method.getExamples(tool()).size());
    }

    @Test
    void getNegativeExamplesUsesScoreWindowAndMissingScoreFallback() throws Exception {
        Path examplesPath = tempDir.resolve("negative.json");
        OBJECT_MAPPER.writeValue(examplesPath.toFile(), List.of(List.of(
                linkedMap("instructions", List.of("skip high"), "answers", List.of("a"),
                        "scores", List.of(3.0d), "fn_call", Map.of(), "tool_results", "out"),
                linkedMap("instructions", List.of("keep scored"), "answers", List.of("b"),
                        "scores", List.of(2.0d), "fn_call", Map.of(), "tool_results", "out"),
                linkedMap("instructions", List.of("keep missing"), "answers", List.of("c"),
                        "fn_call", Map.of(), "tool_results", "out")
        )));
        Map<String, Object> config = baseConfig();
        config.put("neg_ex_input_path", examplesPath.toString());
        config.put("num_examples_for_desc", 2);
        ToolDescriptionMethod method = new ToolDescriptionMethod(config, new RecordingEval(1.0d));

        List<Object> examples = method.getNegativeExamples("weather");

        assertEquals(2, examples.size());
        assertEquals("keep missing", ((Object[]) examples.get(0))[0]);
        assertEquals("keep scored", ((Object[]) examples.get(1))[0]);
    }

    @Test
    void getOriginalDescriptionHandlesIndicatorAndPlainDescription() {
        ToolDescriptionMethod method = new ToolDescriptionMethod(baseConfig(), new RecordingEval(1.0d));

        assertEquals("Find weather", method.getOriginalDescription(linkedMap(
                "description", "The description of this function is: \"Find weather\""
        )));
        assertEquals("plain", method.getOriginalDescription(linkedMap("description", "plain")));
    }

    private Map<String, Object> baseConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("eval_model_id", "eval");
        config.put("gen_model_id", "gen");
        config.put("llm_api_key", "key");
        config.put("verbose", false);
        config.put("num_feedback_steps", 2);
        config.put("num_examples_for_desc", 3);
        config.put("examples_dir", tempDir.toString());
        config.put("neg_ex_input_path", tempDir.resolve("missing.json").toString());
        return config;
    }

    private static Map<String, Object> tool() {
        return linkedMap("name", "weather", "description", "Find weather");
    }

    private static Object[] tuple(Object... values) {
        return values;
    }

    private static Map<String, Object> linkedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    public static final class RecordingEval {

        private final double score;

        private RecordingEval(double score) {
            this.score = score;
        }

        public Map<String, Object> call(
                Map<String, Object> tool,
                String description,
                List<Object> examples,
                int runs
        ) {
            return linkedMap("score_avg", score, "score_std", 0.0d, "results", List.of());
        }
    }

    private static final class GenerateRecordingMethod extends ToolDescriptionMethod {

        private Map<String, Object> generateExamples;

        private GenerateRecordingMethod(Map<String, Object> config, Object evalFn) {
            super(config, evalFn);
        }

        @Override
        public List<Object> getNegativeExamples(String functionName) {
            return List.of("negative-" + functionName);
        }

        @Override
        public Map<String, Object> generate(
                Map<String, Object> tool,
                Map<String, Object> examples,
                List<Object> prevOutputs,
                int it
        ) {
            this.generateExamples = examples;
            return linkedMap("description", "generated", "iteration", it);
        }
    }

    private static final class RitsRecordingMethod extends ToolDescriptionMethod {

        private String modelId;
        private String prompt;
        private Object generatedResponse;

        private RitsRecordingMethod(Map<String, Object> config, Object evalFn, Object generatedResponse) {
            super(config, evalFn);
            this.generatedResponse = generatedResponse;
        }

        @Override
        public Map<String, Object> critiqueDescriptions(
                Map<String, Object> tool,
                List<Object> examples,
                List<Object> prevOutputs
        ) {
            if (generatedResponse != null) {
                return linkedMap("analysis", "analysis text");
            }
            return super.critiqueDescriptions(tool, examples, prevOutputs);
        }

        @Override
        public Map<String, Object> critiqueAllDescriptions(
                Map<String, Object> tool,
                Map<String, Object> examples,
                List<Object> prevOutputs
        ) {
            if (generatedResponse != null) {
                return linkedMap("analysis", "contrast text");
            }
            return super.critiqueAllDescriptions(tool, examples, prevOutputs);
        }

        @Override
        protected Object invokeRitsResponse(
                String modelId,
                String prompt,
                String llmApiKey,
                Function<String, Object> verifyFn,
                Map<String, Object> kwargs
        ) {
            this.modelId = modelId;
            this.prompt = prompt;
            Object response = generatedResponse != null ? generatedResponse : "analysis text";
            return verifyFn.apply(String.valueOf(response));
        }
    }
}
