package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallSchemaExamplesSliceTest {

    @Test
    void schemaExtractorMatchesPythonCases() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("name", "tool");
        src.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of("q", Map.of("type", "string"), "k", List.of(1, 2)),
                "required", List.of("q")
        ));
        src.put("enabled", true);

        Map<String, Object> out = SchemaExtractor.extractSchema(src);
        assertEquals("", out.get("name"));
        assertEquals("", out.get("enabled"));
        assertEquals(List.of("q"), ((Map<?, ?>) out.get("parameters")).get("required"));
        assertEquals("", ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) out.get("parameters")).get("properties")).get("q")).get("type"));
        assertEquals(List.of(1, 2), ((Map<?, ?>) ((Map<?, ?>) out.get("parameters")).get("properties")).get("k"));
        assertEquals(Map.of("a", "", "b", Map.of("c", "")), SchemaExtractor.extractSchema("{\"a\":1,\"b\":{\"c\":2}}"));
        assertEquals(Map.of(), SchemaExtractor.extractSchema("not-json"));
    }

    @Test
    void toolDescriptionMethodMatchesPythonSlice(@TempDir Path tempDir) throws Exception {
        EvalStub eval = new EvalStub(Map.of("score_avg", 77.0d, "score_std", 1.0d, "results", List.of()));
        ToolDescriptionMethod method = new ToolDescriptionMethod(config(tempDir), eval);
        Map<String, Object> tool = tool();

        ToolDescriptionMethod.StepResult step0 = method.step(tool, List.of(new Object[]{"i", Map.of(), "", "a"}), null, 0);
        assertEquals("origin-desc", step0.data);
        assertEquals(77.0d, step0.score, 1e-9d);

        ToolDescriptionMethod generatedMethod = new ToolDescriptionMethod(config(tempDir), eval) {
            @Override
            public Map<String, Object> generate(Map<String, Object> tool, Map<String, Object> examples, List<Object> prevOutputs, int it) {
                return new LinkedHashMap<>(Map.of("description", "new-desc", "iteration", it));
            }
        };
        ToolDescriptionMethod.StepResult step1 = generatedMethod.step(
                tool,
                List.of(new Object[]{"i", Map.of(), "", "a"}),
                List.of(Map.of("iteration", 0)),
                1
        );
        assertEquals("new-desc", step1.data);
        assertEquals(77.0d, step1.score, 1e-9d);

        String validJson = """
                {"description":{"type":"function","name":"search","description":"ok","parameters":{"type":"object","properties":{},"required":[]}}}
                """;
        QueueToolDescriptionMethod queued = new QueueToolDescriptionMethod(config(tempDir), new EvalStub(Map.of()), List.of(
                "desc-analysis",
                "neg-analysis",
                "all-analysis",
                validJson
        ));
        List<Object> examples = List.of(new Object[]{"inst", Map.of("name", "search", "arguments", Map.of()), "fn_out", "ans"});
        List<Object> prevOutputs = List.of(
                Map.of("iteration", 0, "description", "d0", "results", List.of(Map.of("answer", "a", "errors", List.of())), "score_avg", 70.0d, "score_std", 2.0d),
                Map.of("iteration", 1, "description", "d1", "results", List.of(Map.of(
                        "answer", "a",
                        "errors", List.of(Map.of("function_name", "f", "arguments", Map.of(), "error_msg", "e"))
                )), "score_avg", 40.0d, "score_std", 10.0d)
        );

        assertEquals("desc-analysis", queued.critiqueDescriptions(tool, examples, prevOutputs).get("analysis"));
        assertEquals("neg-analysis", queued.critiqueNegativeExamples(tool, examples).get("analysis"));
        assertEquals("all-analysis", queued.critiqueAllDescriptions(tool, Map.of("examples", examples, "neg_examples", examples), prevOutputs).get("analysis"));

        QueueToolDescriptionMethod generator = new QueueToolDescriptionMethod(config(tempDir), new EvalStub(Map.of()), List.of(validJson, validJson, validJson));
        Map<String, Object> generated = generator.generateDescriptionFromDocumentation(tool, Map.of("examples", examples, "neg_examples", examples), prevOutputs);
        assertTrue(generated.containsKey("description"));

        List<List<Map<String, Object>>> goodData = List.of(
                List.of(Map.of("instructions", List.of("inst1"), "fn_call", Map.of("name", "search", "arguments", Map.of("q", "x")), "tool_results", "result1", "answers", List.of("ans1"), "scores", List.of(3))),
                List.of(Map.of("instructions", List.of("inst2"), "fn_call", Map.of("name", "search", "arguments", Map.of("q", "y")), "tool_results", "result2", "answers", List.of("ans2"), "scores", List.of(2)))
        );
        Files.writeString(tempDir.resolve("search.json"), Jsons.toJson(goodData));
        Files.writeString(tempDir.resolve("neg.json"), Jsons.toJson(goodData));

        assertEquals(1, method.loadExamples(tempDir.toString(), "search", 5).size());
        assertEquals(1, method.getNegativeExamples("search").size());
        assertEquals(1, method.getExamples(tool).size());
    }

    @Test
    void apiCallToExampleMethodMatchesPythonSlice() {
        QueueApiCallMethod method = new QueueApiCallMethod(baseConfig(), new EvalStub(Map.of()), List.of(
                "{\"name\":\"search\",\"arguments\":{\"q\":\"x\"}}",
                "{\"analysis\":\"ok\",\"err_code\":0}",
                "{\"instruction\":\"I need weather in Beijing\"}",
                "{\"analysis\":\"good\",\"score\":3}",
                "reflection"
        ));
        Map<String, Object> tool = tool();

        assertEquals("desc", method.getOriginalDescription(Map.of("name", "search", "description", "The description of this function is: \"desc\"")));
        assertEquals(
                Map.of("name", "search", "arguments", Map.of("q", "x")),
                method.generateApiCallFromDescription(tool, 1, List.of(Map.of("fn_call", Map.of("name", "search"), "tool_results", Map.of("ok", 1), "status_code", 0)))
        );
        assertThrows(IllegalArgumentException.class, () -> new QueueApiCallMethod(baseConfig(), new EvalStub(Map.of()), List.of("{\"name\":\"other\",\"arguments\":{}}"))
                .generateApiCallFromDescription(tool, 1, List.of()));

        Map<String, Object> fnCall = Map.of("name", "search", "arguments", Map.of("q", "x"));
        assertEquals(0, method.critiqueApiCall(tool, fnCall, "r".repeat(3000)).get("err_code"));
        assertTrue(method.generateInstructionFromApiCall(tool, fnCall, "resp", Map.of(
                "instructions", List.of("a"),
                "scores", List.of(1),
                "batch_reflection", "b"
        )).contains("Beijing"));
        assertEquals(3, method.critiqueInstruction(tool, "inst", fnCall, "resp", "ans").get("score"));
        assertEquals("reflection", method.batchReflectionWithScores(tool, fnCall, List.of("i1"), List.of(2.0d), List.of("a1")));

        FlowApiCallMethod flow = new FlowApiCallMethod(baseConfig(), new EvalStub(Map.of("score_avg", 50.0d)));
        APICallToExampleMethod.StepResult result = flow.step(tool, new ArrayList<>(), 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.results;
        assertEquals(List.of("inst-1", "inst-2"), result.data);
        assertEquals(0, payload.get("status_code"));
        assertEquals(3.25d, result.score, 1e-9d);
    }

    private static Map<String, Object> config(Path tempDir) {
        Map<String, Object> config = baseConfig();
        config.put("num_feedback_steps", 2);
        config.put("num_examples_for_desc", 3);
        config.put("examples_dir", tempDir.toString());
        config.put("neg_ex_input_path", tempDir.resolve("neg.json").toString());
        return config;
    }

    private static Map<String, Object> baseConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("gen_model_id", "gpt-gen");
        config.put("eval_model_id", "gpt-eval");
        config.put("llm_api_key", "k");
        config.put("verbose", false);
        config.put("num_init_loop", 2);
        config.put("num_refine_steps", 2);
        config.put("num_feedback_steps", 1);
        config.put("score_eval_weight", 0.5d);
        return config;
    }

    private static Map<String, Object> tool() {
        return Map.of("name", "search", "description", "The description of this function is: \"origin-desc\"");
    }

    private static final class EvalStub {
        private final Map<String, Object> result;

        private EvalStub(Map<String, Object> result) {
            this.result = result;
        }

        public Map<String, Object> evaluate(Map<String, Object> tool, String description, List<Object> examples, int runs) {
            return new LinkedHashMap<>(result);
        }
    }

    private static final class QueueToolDescriptionMethod extends ToolDescriptionMethod {
        private final ArrayDeque<String> outputs;

        private QueueToolDescriptionMethod(Map<String, Object> config, Object evalFn, List<String> outputs) {
            super(config, evalFn);
            this.outputs = new ArrayDeque<>(outputs);
        }

        @Override
        protected Object invokeRitsResponse(String modelId, String prompt, String llmApiKey, Function<String, Object> verifyFn, Map<String, Object> kwargs) {
            return verifyFn.apply(outputs.removeFirst());
        }
    }

    private static final class QueueApiCallMethod extends APICallToExampleMethod {
        private final ArrayDeque<String> outputs;

        private QueueApiCallMethod(Map<String, Object> config, Object evalFn, List<String> outputs) {
            super(config, (BiToolCall) (tool, fnCall) -> new Object[]{"{\"response\":\"ok\"}", 0}, evalFn, null, null);
            this.outputs = new ArrayDeque<>(outputs);
        }

        @Override
        protected Object invokeRitsResponse(String modelId, String prompt, String llmApiKey, Function<String, Object> verifyFn, Map<String, Object> kwargs) {
            return verifyFn.apply(outputs.removeFirst());
        }
    }

    private static final class FlowApiCallMethod extends APICallToExampleMethod {
        private int critiqueApiIdx;
        private int instructionIdx;
        private int answerIdx;
        private int critiqueInstructionIdx;

        private FlowApiCallMethod(Map<String, Object> config, Object evalFn) {
            super(config, (BiToolCall) (tool, fnCall) -> new Object[]{"{\"response\":\"ok\"}", 0}, evalFn, null, null);
        }

        @Override
        public Map<String, Object> generateApiCallFromDescription(Map<String, Object> tool, List<String> exampleCalls, int numGen, List<Object> prevOutputs) {
            return Map.of("name", "search", "arguments", Map.of("q", "x"));
        }

        @Override
        public Map<String, Object> critiqueApiCall(Map<String, Object> tool, Map<String, Object> fnCall, String fnResponse) {
            critiqueApiIdx++;
            return critiqueApiIdx == 1
                    ? Map.of("analysis", "bad", "err_code", -1)
                    : Map.of("analysis", "", "err_code", 0);
        }

        @Override
        public String generateInstructionFromApiCall(Map<String, Object> tool, Map<String, Object> fnCall, String fnResponse, Map<String, Object> prevOutput) {
            instructionIdx++;
            return instructionIdx == 1 ? "inst-1" : "inst-2";
        }

        @Override
        public String produceAnswerFromApiCall(String instruction, String docStr, String apiResponse) {
            answerIdx++;
            return answerIdx == 1 ? "ans-1" : "ans-2";
        }

        @Override
        public Map<String, Object> critiqueInstruction(Map<String, Object> tool, String instruction, Map<String, Object> fnCall, String fnResponse, String answer) {
            critiqueInstructionIdx++;
            return critiqueInstructionIdx == 1
                    ? Map.of("analysis", "a", "score", 2)
                    : Map.of("analysis", "b", "score", 3);
        }

        @Override
        public String batchReflectionWithScores(Map<String, Object> tool, Map<String, Object> fnCall, List<String> instructions, List<Double> scores, List<String> analyses) {
            return "refl";
        }
    }

    @FunctionalInterface
    private interface BiToolCall {
        Object[] call(Map<String, Object> tool, Map<String, Object> fnCall);
    }

    private static final class Jsons {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

        private static String toJson(Object value) throws Exception {
            return MAPPER.writeValueAsString(value);
        }
    }
}
