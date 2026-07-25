/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tool optimizer-base tests in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_tool_base.py}.
 */
class ToolOptimizerBasePythonParityTest {

    @Test
    void testToolOptimizerBaseInitAndDefaultTargets(@TempDir Path tempDir) {
        TestToolOptimizer optimizer = new TestToolOptimizer(Map.of(
                "max_turns", 2,
                "llm_api_key", "k",
                "config_eg", Map.of("x", 1),
                "config_desc", Map.of("y", 2),
                "path_save_dir", tempDir.toString(),
                "tool_name", "search"
        ));

        assertEquals(List.of("tool_description"), optimizer.defaultTargets());
        assertTrue(String.valueOf(optimizer.getConfigEg().get("save_dir")).endsWith("examples"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("save_dir")).endsWith("descriptions"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("examples_dir")).endsWith("examples"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("neg_ex_input_path")).endsWith("search.json"));
    }

    @Test
    void testToolOptimizerOptimizeToolWithMocks(@TempDir Path tempDir) {
        RecordingToolOptimizer optimizer = new RecordingToolOptimizer(Map.of(
                "max_turns", 2,
                "llm_api_key", "api-key",
                "config_eg", Map.of("eval_model_id", "x"),
                "config_desc", Map.of("eval_model_id", "eval-m"),
                "path_save_dir", tempDir.toString(),
                "tool_name", "search"
        ));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "search");
        tool.put("description", "{\"name\":\"search\"}");

        Map<String, Object> out = optimizer.optimizeTool(tool, "callable");

        assertEquals(Map.of("name", ""), out.get("schema"));
        Map<?, ?> processed = assertInstanceOf(Map.class, out.get("processed"));
        assertEquals("desc-2", processed.get("processed"));
    }

    @Test
    void testDomainIsTool() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertEquals("tool", optimizer.getDomain());
    }

    @Test
    void testDefaultTargetsToolDescription() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertEquals(List.of("tool_description"), optimizer.defaultTargets());
    }

    @Test
    void testFilterMatchesToolTargets() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of(
                "op1", makeMockToolOperator(Map.of("tool_description", "A search tool"), "tool_op"),
                "op2", makeMockToolOperator(Map.of("system_prompt", "prompt"), "tool_op")
        );

        Map<String, Operator> result = optimizer.filter(operators, List.of("tool_description"));

        assertTrue(result.containsKey("op1"));
        assertFalse(result.containsKey("op2"));
    }

    @Test
    void testFilterEmptyTargets() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of("op1", makeMockToolOperator(
                Map.of("tool_description", "A search tool"),
                "tool_op"
        ));

        Map<String, Operator> result = optimizer.filter(operators, List.of());

        assertEquals(Map.of(), result);
    }

    @Test
    void testFilterSkipsNoTunables() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of("op1", makeMockToolOperator(Map.of(), "tool_op"));

        Map<String, Operator> result = optimizer.filter(operators, List.of("tool_description"));

        assertEquals(Map.of(), result);
    }

    @Test
    void testFilterMultipleToolOperators() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of(
                "op1", makeMockToolOperator(Map.of("tool_description", "Tool 1"), "tool1"),
                "op2", makeMockToolOperator(Map.of("tool_description", "Tool 2"), "tool2")
        );

        Map<String, Operator> result = optimizer.filter(operators, List.of("tool_description"));

        assertTrue(result.containsKey("op1"));
        assertTrue(result.containsKey("op2"));
    }

    @Test
    void testBindWithToolOperators() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of(
                "op1", makeMockToolOperator(Map.of("tool_description", "A search tool"), "tool_op"),
                "op2", makeMockToolOperator(Map.of("system_prompt", "prompt"), "tool_op")
        );

        int count = optimizer.bind(operators, null, Map.of());

        assertEquals(1, count);
    }

    @Test
    void testBindWithNoMatchingOperators() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of("op1", makeMockToolOperator(Map.of("other", "value"), "tool_op"));

        int count = optimizer.bind(operators, null, Map.of());

        assertEquals(0, count);
    }

    @Test
    void testBindWithMultipleMatching() {
        TestToolOptimizer optimizer = new TestToolOptimizer();
        Map<String, Operator> operators = Map.of(
                "op1", makeMockToolOperator(Map.of("tool_description", "Tool 1"), "tool1"),
                "op2", makeMockToolOperator(Map.of("tool_description", "Tool 2"), "tool2")
        );

        int count = optimizer.bind(operators, null, Map.of());

        assertEquals(2, count);
    }

    @Test
    void testDefaultTargetsReturnsList() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertInstanceOf(List.class, optimizer.defaultTargets());
    }

    @Test
    void testDefaultTargetsContainsToolDescription() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertTrue(optimizer.defaultTargets().contains("tool_description"));
    }

    @Test
    void testDefaultTargetsCount() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertEquals(1, optimizer.defaultTargets().size());
    }

    private static FakeOperator makeMockToolOperator(Map<String, Object> tunables, String operatorId) {
        return new FakeOperator(operatorId, tunables);
    }

    private static class TestToolOptimizer extends ToolOptimizerBase {

        private TestToolOptimizer() {
            super();
        }

        private TestToolOptimizer(Map<String, Object> kwargs) {
            super(kwargs);
        }

        private String getDomain() {
            return domain;
        }

        private Map<String, Operator> filter(Map<String, Operator> operators, List<String> targets) {
            return BaseOptimizer.filterOperators(operators, targets);
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
        private final Queue<List<Object>> descResults = new ArrayDeque<>();

        private RecordingToolOptimizer(Map<String, Object> kwargs) {
            super(kwargs);
            descResults.add(List.of(List.of(Map.of("description", "desc-1"))));
            descResults.add(List.of(List.of(Map.of("description", "desc-2"))));
        }

        @Override
        protected List<Object> runCustomizedPipeline(
                String stage,
                Map<String, Object> tool,
                Object toolCallable,
                Map<String, Object> config
        ) {
            if ("example".equals(stage)) {
                return List.of(Map.of("example", true));
            }
            return descResults.remove();
        }

        @Override
        protected Object createToolDescriptionReviewer(String evalModelId, String apiKey) {
            assertEquals("eval-m", evalModelId);
            assertEquals("api-key", apiKey);
            return new Object();
        }

        @Override
        protected Object processDescription(Object reviewer, Object data, String originalTool, List<String> steps) {
            return Map.of("processed", data, "ori", originalTool, "steps", new ArrayList<>(steps));
        }

        @Override
        protected Map<String, Object> formatDescription(
                Object reviewer,
                Map<String, Object> schema,
                Object description,
                String example
        ) {
            return Map.of("schema", schema, "processed", description);
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
