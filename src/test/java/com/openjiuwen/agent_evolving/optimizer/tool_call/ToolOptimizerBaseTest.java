package com.openjiuwen.agent_evolving.optimizer.tool_call;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.ToolDescriptionReviewer;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the tool-call optimizer base.
 *
 * <p>Mirrors Python's {@code test_tool_base.py} in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call}.
 */
class ToolOptimizerBaseTest {

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
        assertEquals(2, optimizer.getMaxTurns());
        assertEquals("k", optimizer.getLlmApiKey());
        assertTrue(String.valueOf(optimizer.getConfigEg().get("save_dir")).endsWith("examples"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("save_dir")).endsWith("descriptions"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("examples_dir")).endsWith("examples"));
        assertTrue(String.valueOf(optimizer.getConfigDesc().get("neg_ex_input_path")).endsWith("search.json"));
    }

    @Test
    void testToolOptimizerOptimizeToolWithMocks(@TempDir Path tempDir) {
        RecordingReviewer reviewer = new RecordingReviewer("eval-m", "api-key");
        RecordingToolOptimizer optimizer = new RecordingToolOptimizer(
                Map.of(
                        "max_turns", 2,
                        "llm_api_key", "api-key",
                        "config_eg", Map.of("eval_model_id", "x"),
                        "config_desc", Map.of("eval_model_id", "eval-m"),
                        "path_save_dir", tempDir.toString(),
                        "tool_name", "search"
                ),
                reviewer
        );
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "search");
        tool.put("description", "{\"name\":\"search\"}");

        Map<String, Object> out = optimizer.optimizeTool(tool, "callable");

        assertEquals(4, optimizer.pipelineCalls.size());
        assertEquals(List.of("clean", "cross_check", "translate"), reviewer.steps);
        assertEquals("desc-1", reviewer.oriTool);
        assertEquals("", ((Map<?, ?>) out.get("schema")).get("name"));
        assertTrue(String.valueOf(out.get("processed")).contains("desc-2"));
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
        Map<String, Object> result = ToolOptimizerBase.filterOperators(Map.of(
                "op1", new FakeOperator(Map.of("tool_description", "A search tool")),
                "op2", new FakeOperator(Map.of("system_prompt", "prompt"))
        ), List.of("tool_description"));

        assertTrue(result.containsKey("op1"));
        assertTrue(!result.containsKey("op2"));
    }

    @Test
    void testFilterEmptyTargets() {
        Map<String, Object> result = ToolOptimizerBase.filterOperators(
                Map.of("op1", new FakeOperator(Map.of("tool_description", "desc"))),
                List.of()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterSkipsNoTunables() {
        Map<String, Object> result = ToolOptimizerBase.filterOperators(
                Map.of("op1", new FakeOperator(Map.of())),
                List.of("tool_description")
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterMultipleToolOperators() {
        Map<String, Object> result = ToolOptimizerBase.filterOperators(Map.of(
                "op1", new FakeOperator(Map.of("tool_description", "Tool 1")),
                "op2", new FakeOperator(Map.of("tool_description", "Tool 2"))
        ), List.of("tool_description"));

        assertTrue(result.containsKey("op1"));
        assertTrue(result.containsKey("op2"));
    }

    @Test
    void testBindWithToolOperators() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        int count = optimizer.bind(Map.of(
                "op1", new FakeOperator(Map.of("tool_description", "A search tool")),
                "op2", new FakeOperator(Map.of("system_prompt", "prompt"))
        ), null, Map.of());

        assertEquals(1, count);
        assertTrue(optimizer.getOperators().containsKey("op1"));
    }

    @Test
    void testBindWithNoMatchingOperators() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        int count = optimizer.bind(
                Map.of("op1", new FakeOperator(Map.of("other", "value"))),
                null,
                Map.of()
        );

        assertEquals(0, count);
    }

    @Test
    void testBindWithMultipleMatching() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        int count = optimizer.bind(Map.of(
                "op1", new FakeOperator(Map.of("tool_description", "Tool 1")),
                "op2", new FakeOperator(Map.of("tool_description", "Tool 2"))
        ), null, Map.of());

        assertEquals(2, count);
    }

    @Test
    void testDefaultTargetsReturnsList() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertTrue(List.class.isInstance(optimizer.defaultTargets()));
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

    @Test
    void inheritedDomainAndDefaultTargetsMatchPython() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertEquals("tool", optimizer.getDomain());
        assertEquals(List.of("tool_description"), optimizer.defaultTargets());
    }

    @Test
    void bindUsesDefaultToolDescriptionTarget() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        int count = optimizer.bind(
                Map.of(
                        "tool", new FakeOperator(Map.of("tool_description", "desc")),
                        "other", new FakeOperator(Map.of("system_prompt", "prompt"))
                ),
                null,
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.getOperators().containsKey("tool"));
    }

    private static class TestToolOptimizer extends ToolOptimizerBase {

        private TestToolOptimizer() {
            super();
        }

        private TestToolOptimizer(Map<String, Object> kwargs) {
            super(kwargs);
        }

        @Override
        protected Updates doStep() {
            return new Updates();
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        }
    }

    public static final class FakeOperator {
        private final Map<String, Object> tunables;

        private FakeOperator(Map<String, Object> tunables) {
            this.tunables = new LinkedHashMap<>(tunables);
        }

        public Map<String, Object> getTunables() {
            return tunables;
        }
    }

    private static final class RecordingToolOptimizer extends TestToolOptimizer {
        private final RecordingReviewer reviewer;
        private final List<String> pipelineCalls = new ArrayList<>();
        private final Deque<List<Object>> descriptions = new ArrayDeque<>();

        private RecordingToolOptimizer(Map<String, Object> kwargs, RecordingReviewer reviewer) {
            super(kwargs);
            this.reviewer = reviewer;
            descriptions.add(List.of(List.of(Map.of("description", "desc-1"))));
            descriptions.add(List.of(List.of(Map.of("description", "desc-2"))));
        }

        @Override
        protected List<Object> runCustomizedPipeline(
                String stage,
                Map<String, Object> tool,
                Object toolCallable,
                Map<String, Object> config
        ) {
            pipelineCalls.add(stage + ":" + tool.get("description"));
            if ("example".equals(stage)) {
                return List.of(Map.of("example", true));
            }
            return descriptions.removeFirst();
        }

        @Override
        protected ToolDescriptionReviewer createToolDescriptionReviewer(String evalModelId, String apiKey) {
            assertEquals("eval-m", evalModelId);
            assertEquals("api-key", apiKey);
            return reviewer;
        }
    }

    private static final class RecordingReviewer extends ToolDescriptionReviewer {
        private String oriTool;
        private List<String> steps;

        private RecordingReviewer(String evalModelId, String llmApiKey) {
            super(evalModelId, llmApiKey);
        }

        @Override
        public Map<String, Object> process(Map<String, Object> data, String oriTool, List<String> steps) {
            this.oriTool = oriTool;
            this.steps = steps;
            return Map.of("processed", data, "ori", oriTool);
        }

        @Override
        public Map<String, Object> format(Map<String, Object> jsonSchema, String description, String example) {
            return Map.of("schema", jsonSchema, "processed", description);
        }
    }
}
