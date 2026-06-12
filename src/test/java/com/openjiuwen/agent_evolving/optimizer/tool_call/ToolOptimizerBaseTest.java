/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tests for the tool-call optimizer base.
 *
 * <p>Mirrors Python's {@code ToolOptimizerBase} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/base.py}.</p>
 */
class ToolOptimizerBaseTest {

    @Test
    void initializesConfigAndDefaultTargets(@TempDir Path tempDir) {
        TestToolOptimizer optimizer = new TestToolOptimizer(Map.of(
                "max_turns", 2,
                "llm_api_key", "api-key",
                "config_eg", Map.of("beam_width", 1),
                "config_desc", Map.of("eval_model_id", "eval-m"),
                "path_save_dir", tempDir.toString(),
                "tool_name", "search"
        ));

        assertEquals("tool", optimizer.getDomain());
        assertEquals(List.of("tool_description"), optimizer.defaultTargets());
        assertEquals(2, optimizer.getMaxTurns());
        assertEquals("api-key", optimizer.getLlmApiKey());
        assertTrue(String.valueOf(optimizer.getConfigEg().get("save_dir")).endsWith("examples"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("save_dir")).endsWith("descriptions"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("examples_dir")).endsWith("examples"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("neg_ex_input_path")).endsWith("search.json"));
    }

    @Test
    void bindUsesDefaultToolDescriptionTarget() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        int count = optimizer.bind(Map.of(
                "tool", new FakeOperator("tool", Map.of("tool_description", "desc")),
                "prompt", new FakeOperator("prompt", Map.of("system_prompt", "prompt"))
        ), null, Map.of());

        assertEquals(1, count);
        assertTrue(optimizer.getOperators().containsKey("tool"));
    }

    @Test
    void optimizeToolUsesCurrentPythonDescriptionIndexes(@TempDir Path tempDir) {
        RecordingToolOptimizer optimizer = new RecordingToolOptimizer(Map.of(
                "max_turns", 2,
                "llm_api_key", "api-key",
                "config_eg", Map.of("beam_width", 1),
                "config_desc", Map.of("eval_model_id", "eval-m"),
                "path_save_dir", tempDir.toString(),
                "tool_name", "search"
        ));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "search");
        tool.put("description", "{\"name\":\"search\",\"description\":\"original\"}");

        Map<String, Object> result = optimizer.optimizeTool(tool, "callable");

        assertEquals(List.of(
                "example:{\"name\":\"search\",\"description\":\"original\"}",
                "description:{\"name\":\"search\",\"description\":\"original\"}",
                "example:desc-1-first",
                "description:desc-1-first"
        ), optimizer.pipelineCalls);
        assertEquals("desc-2-last", optimizer.processData);
        assertEquals("desc-1-first", optimizer.processOriginalTool);
        assertEquals(List.of("clean", "cross_check", "translate"), optimizer.processSteps);
        assertEquals("", ((Map<?, ?>) result.get("schema")).get("name"));
        assertEquals(Map.of("processed", "desc-2-last"), result.get("description"));
    }

    private static class TestToolOptimizer extends ToolOptimizerBase {

        private TestToolOptimizer() {
            super();
        }

        private TestToolOptimizer(Map<String, Object> kwargs) {
            super(kwargs);
        }

        String getDomain() {
            return domain;
        }

        @Override
        protected CompletionStage<Void> doBackward(List<EvolutionSignal> signals) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected Updates doStep() {
            return new Updates();
        }
    }

    private static final class RecordingToolOptimizer extends TestToolOptimizer {
        private final List<String> pipelineCalls = new ArrayList<>();
        private final Queue<List<Object>> descResults = new ArrayDeque<>();
        private Object processData;
        private String processOriginalTool;
        private List<String> processSteps;

        private RecordingToolOptimizer(Map<String, Object> kwargs) {
            super(kwargs);
            descResults.add(List.of(List.of(
                    Map.of("description", "desc-1-first"),
                    Map.of("description", "desc-1-last")
            )));
            descResults.add(List.of(List.of(
                    Map.of("description", "desc-2-first"),
                    Map.of("description", "desc-2-last")
            )));
        }

        @Override
        protected List<Object> runCustomizedPipeline(
                String stage,
                Map<String, Object> tool,
                Object toolCallable,
                Map<String, Object> config
        ) {
            pipelineCalls.add(stage + ":" + tool.get("description"));
            return "description".equals(stage) ? descResults.remove() : List.of();
        }

        @Override
        protected Object createToolDescriptionReviewer(String evalModelId, String apiKey) {
            assertEquals("eval-m", evalModelId);
            assertEquals("api-key", apiKey);
            return new Object();
        }

        @Override
        protected Object processDescription(Object reviewer, Object data, String originalTool, List<String> steps) {
            processData = data;
            processOriginalTool = originalTool;
            processSteps = List.copyOf(steps);
            return Map.of("processed", data);
        }

        @Override
        protected Map<String, Object> formatDescription(
                Object reviewer,
                Map<String, Object> schema,
                Object description,
                String example
        ) {
            return Map.of("schema", schema, "description", description);
        }
    }

    private static final class FakeOperator extends Operator {
        private final String operatorId;
        private final Map<String, TunableSpec> tunables;
        private Object value;

        private FakeOperator(String operatorId, Map<String, Object> tunableValues) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>();
            for (String key : tunableValues.keySet()) {
                this.tunables.put(key, new TunableSpec(key, "text", key));
            }
            this.value = tunableValues;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return tunables;
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of("value", value);
        }

        @Override
        public void setParameter(String target, Object value) {
            this.value = value;
        }

        @Override
        public ApplyResult applyUpdate(String target, UpdateValue update) {
            return super.applyUpdate(target, update);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.value = state;
        }
    }
}
