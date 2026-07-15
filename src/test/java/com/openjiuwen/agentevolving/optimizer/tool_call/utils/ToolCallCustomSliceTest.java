
package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class ToolCallCustomSliceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void simpleApiWrapperCallSuccessNotFoundAndException() throws Exception {
        Function<Map<String, Object>, Object> okFn = params -> Map.of("echo", params);

        SimpleApiWrapper wrapper = new SimpleApiWrapper("ok_fn", Map.of("ok_fn", okFn));
        Object[] payload = wrapper.call(Map.of("name", "ok_fn"), Map.of("a", 1));
        assertEquals(0, ((Number) payload[1]).intValue());
        assertEquals(Map.of("response", Map.of("echo", Map.of("a", 1))),
                OBJECT_MAPPER.readValue(String.valueOf(payload[0]), Map.class));

        SimpleApiWrapper missing = new SimpleApiWrapper("missing", Map.of("ok_fn", okFn));
        Object[] payload2 = missing.call(Map.of("name", "missing"), Map.of("a", 1));
        assertEquals(12, ((Number) payload2[1]).intValue());
        assertTrue(String.valueOf(OBJECT_MAPPER.readValue(String.valueOf(payload2[0]), Map.class).get("error"))
                .contains("no function"));

        Function<Map<String, Object>, Object> badFn = params -> {
            throw new IllegalArgumentException("boom");
        };
        SimpleApiWrapper bad = new SimpleApiWrapper("bad_fn", Map.of("bad_fn", badFn));
        Object[] payload3 = bad.call(Map.of("name", "bad_fn"), Map.of("a", 1));
        assertEquals(12, ((Number) payload3[1]).intValue());
        assertTrue(String.valueOf(OBJECT_MAPPER.readValue(String.valueOf(payload3[0]), Map.class).get("error"))
                .contains("boom"));
    }

    @Test
    void simpleApiWrapperAddFunctionAndLoadCustomData(@TempDir Path tempDir) throws Exception {
        SimpleApiWrapper wrapper = new SimpleApiWrapper("ping",
                Map.of("ping", (Function<Map<String, Object>, Object>) params -> Map.of("pong", params.get("x"))));
        Object[] payload = wrapper.call(Map.of("name", "ping"), Map.of("x", 9));
        assertEquals(0, ((Number) payload[1]).intValue());
        assertEquals(Map.of("response", Map.of("pong", 9)),
                OBJECT_MAPPER.readValue(String.valueOf(payload[0]), Map.class));

        wrapper.addFunction("sum2",
                (Function<Map<String, Object>, Object>) params -> ((Number) params.get("a")).intValue()
                        + ((Number) params.get("b")).intValue());
        wrapper.fnCallName = "sum2";
        Object[] payload2 = wrapper.call(Map.of("name", "sum2"), Map.of("a", 1, "b", 2));
        assertEquals(0, ((Number) payload2[1]).intValue());
        assertEquals(Map.of("response", 3), OBJECT_MAPPER.readValue(String.valueOf(payload2[0]), Map.class));

        Path jsonl = tempDir.resolve("x.jsonl");
        Files.writeString(jsonl,
                OBJECT_MAPPER.writeValueAsString(Map.of("function", Map.of("name", "f1"))) + System.lineSeparator()
                        + OBJECT_MAPPER.writeValueAsString(
                                Map.of("function", List.of(Map.of("name", "f2"), Map.of("name", "f3")))));
        assertEquals(List.of("f1", "f2", "f3"), SimpleApiWrapper.loadCustomData(jsonl.toString(), null).stream()
                .map(tool -> String.valueOf(((Map<?, ?>) tool.get("function")).get("name"))).toList());

        Path asList = tempDir.resolve("list.json");
        Files.writeString(asList, OBJECT_MAPPER
                .writeValueAsString(List.of(Map.of("function", Map.of("name", "a")), Map.of("name", "b"))));
        assertEquals(List.of("a", "b"), SimpleApiWrapper.loadCustomData(asList.toString(), null).stream()
                .map(tool -> String.valueOf(((Map<?, ?>) tool.get("function")).get("name"))).toList());

        Path asObject = tempDir.resolve("obj.json");
        Files.writeString(asObject,
                OBJECT_MAPPER.writeValueAsString(Map.of("functions", List.of(Map.of("name", "c")))));
        assertEquals(List.of("c"), SimpleApiWrapper.loadCustomData(asObject.toString(), null).stream()
                .map(tool -> String.valueOf(((Map<?, ?>) tool.get("function")).get("name"))).toList());
    }

    @Test
    void simpleEvalValidatesWeightsAndAggregatesRuns() {
        assertThrows(IllegalArgumentException.class,
                () -> new StubSimpleEval(null, Map.of("eval_model_id", "gpt-test"), 0.7d, 0.4d));

        StubSimpleEval evaluator = new StubSimpleEval(null, Map.of("eval_model_id", "gpt-test"), 0.4d, 0.6d);
        Map<String, Object> result = evaluator.evaluate(Map.of("name", "f"), "d",
                java.util.Collections.singletonList(new Object[]{"i", Map.of(), "", "a"}), 2);

        assertEquals(65.0d, ((Number) result.get("score_avg")).doubleValue(), 1e-9d);
        assertEquals(50.0d, ((Number) result.get("fn_call_accuracy")).doubleValue(), 1e-9d);
        assertEquals(75.0d, ((Number) result.get("output_effectiveness")).doubleValue(), 1e-9d);
    }

    @Test
    void simpleEvalHandlesJsonArgumentsAndWrapperFailures() {
        DeterministicSimpleEval evaluator = new DeterministicSimpleEval(
                new SimpleApiWrapper("ok_fn", Map.of("ok_fn", (Function<Map<String, Object>, Object>) params -> {
                    throw new IllegalStateException("boom");
                })));

        Map<String, Object> result = evaluator.evaluateSingleExample(Map.of("name", "ok_fn"), "desc",
                new Object[]{"ask", Map.of("name", "ok_fn", "arguments", "{\"a\":1}"), "", "answer"});

        assertEquals(1.0d, ((Number) result.get("fn_call_score")).doubleValue());
        assertNotNull(result.get("execution_error"));
        assertEquals(0.0d, ((Number) result.get("output_effectiveness_score")).doubleValue());
        assertFalse(((List<?>) result.get("errors")).isEmpty());
        assertEquals("ok_fn", ((Map<?, ?>) ((List<?>) result.get("errors")).get(0)).get("function_name"));
    }

    @Test
    void customizedPipelineRejectsUnsupportedInputs() {
        assertThrows(UnsupportedOperationException.class,
                () -> CustomizedPipeline.customizedPipeline("example", Map.of("name", "tool"),
                        Map.of("fn_call_path", "x"), (Function<Map<String, Object>, Object>) params -> params));

        assertThrows(IllegalArgumentException.class,
                () -> CustomizedPipeline.customizedPipeline("example", Map.of("name", "tool"), Map.of(), null));

        assertThrows(IllegalArgumentException.class, () -> CustomizedPipeline.customizedPipeline("bad",
                Map.of("name", "tool"), Map.of(), (Function<Map<String, Object>, Object>) params -> params));
    }

    @Test
    void customizedPipelineMergesExistingSavedResultsIntoReturnValue() throws Exception {
        Path saveDir = Files.createTempDirectory("customized-pipeline-test");
        Path saveFile = saveDir.resolve("tool.json");
        Files.writeString(saveFile, "[[{\"description\":\"old\"}]]");

        @SuppressWarnings("unchecked")
        List<Object> result = CustomizedPipeline.customizedPipeline("description", Map.of("name", "tool"),
                new LinkedHashMap<>(Map.of("save_dir", saveDir.toString(), "beam_width", 1, "expand_num", 1,
                        "max_depth", 1, "num_workers", 1, "top_k", 1, "verbose", 0)),
                (Function<Map<String, Object>, Object>) params -> params);

        assertEquals(2, result.size());
        assertEquals("old", ((Map<?, ?>) ((List<?>) result.get(0)).get(0)).get("description"));
    }

    private static final class StubSimpleEval extends SimpleEval {
        private StubSimpleEval(Object apiWrapper, Map<String, Object> config, double fnCallWeight,
                double outputWeight) {
            super(apiWrapper, config, fnCallWeight, outputWeight);
        }

        @Override
        protected Map<String, Object> evaluateSingleExample(Map<String, Object> tool, String description,
                Object[] example) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fn_call_score", 0.5d);
            result.put("output_effectiveness_score", 0.75d);
            result.put("weighted_score", 0.65d);
            result.put("answer", "ok");
            result.put("errors", List.of());
            return result;
        }
    }

    private static final class DeterministicSimpleEval extends SimpleEval {
        private DeterministicSimpleEval(Object apiWrapper) {
            super(apiWrapper, Map.of("eval_model_id", "gpt-test"), 0.4d, 0.6d);
        }

        @Override
        protected Map<String, Object> generateFunctionCall(Map<String, Object> tool, String description,
                String instruction) {
            Map<String, Object> generated = new LinkedHashMap<>();
            generated.put("name", "ok_fn");
            generated.put("arguments", "{\"a\":1}");
            return generated;
        }

        @Override
        protected double evaluateOutputEffectiveness(String instruction, Object executionResult, Object executionError,
                String expectedAnswer) {
            return executionError == null ? 1.0d : 0.0d;
        }
    }
}
