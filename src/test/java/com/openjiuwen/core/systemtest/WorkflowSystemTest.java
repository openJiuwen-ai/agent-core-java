/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.session.NodeSessionApi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Workflow module.
 * Tests workflow construction, execution, branching, and End template rendering.
 * Corresponds to Python's build_workflow_agent example.
 */
@Tag("system-test")
class WorkflowSystemTest {

    // ---- Helper components ----

    static class PassthroughComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    static class UpperCaseComponent extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (inputs instanceof Map) {
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
                    Object val = entry.getValue();
                    result.put(entry.getKey(),
                            val instanceof String ? ((String) val).toUpperCase() : val);
                }
                return result;
            }
            return inputs;
        }
    }

    static class AddNumbersComponent extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (inputs instanceof Map) {
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                Number a = (Number) inputMap.getOrDefault("a", 0);
                Number b = (Number) inputMap.getOrDefault("b", 0);
                return Map.of("sum", a.doubleValue() + b.doubleValue());
            }
            return inputs;
        }
    }

    private static WorkflowSessionApi newSession() {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of());
    }

    // ---- Tests ----

    @Test
    @DisplayName("Simple linear workflow: Start -> Process -> End")
    void testSimpleLinearWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("message", "${message}"), null);
        flow.addWorkflowComp("process", new PassthroughComponent(),
                Map.of("message", "${start.message}"), null);
        flow.setEndComp("end", new PassthroughComponent(),
                Map.of("result", "${process.message}"), null);
        flow.addConnection("start", "process");
        flow.addConnection("process", "end");

        WorkflowOutput output = flow.invoke(Map.of("message", "hello"), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertNotNull(output.getResult());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output.getResult();
        assertEquals("hello", result.get("result"));
        System.out.println("[Workflow Linear] Result: " + result);
    }

    @Test
    @DisplayName("Workflow with End component response template")
    void testWorkflowEndTemplate() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("name", "${name}", "age", "${age}"), null);
        flow.setEndComp("end", new End(Map.of(
                        "responseTemplate", "你好, {{name}}! 你{{age}}岁了。")),
                Map.of("name", "${start.name}", "age", "${start.age}"), null);
        flow.addConnection("start", "end");

        WorkflowOutput output = flow.invoke(
                Map.of("name", "小明", "age", 25), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertNotNull(output.getResult());
        String response = output.getResult().toString();
        assertTrue(response.contains("小明"), "Template should resolve name");
        assertTrue(response.contains("25"), "Template should resolve age");
        System.out.println("[Workflow EndTemplate] Result: " + output.getResult());
    }

    @Test
    @DisplayName("Workflow with parallel branches")
    void testWorkflowParallelBranches() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("x", "${x}", "y", "${y}"), null);
        flow.addWorkflowComp("branch_a", new PassthroughComponent(),
                Map.of("value", "${start.x}"), null);
        flow.addWorkflowComp("branch_b", new PassthroughComponent(),
                Map.of("value", "${start.y}"), null);
        flow.setEndComp("end", new PassthroughComponent(),
                Map.of("a", "${branch_a.value}", "b", "${branch_b.value}"), null);
        flow.addConnection("start", "branch_a");
        flow.addConnection("start", "branch_b");
        flow.addConnection("branch_a", "end");
        flow.addConnection("branch_b", "end");

        WorkflowOutput output = flow.invoke(Map.of("x", 10, "y", 20), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output.getResult();
        assertEquals(10, result.get("a"));
        assertEquals(20, result.get("b"));
        System.out.println("[Workflow Parallel] Result: " + result);
    }

    @Test
    @DisplayName("Workflow with BranchRouter conditional connections")
    void testWorkflowBranchRouter() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("score", "${score}"), null);

        BranchRouter router = new BranchRouter();
        router.addBranch("${start.score} >= 60", "pass", null);
        router.addBranch("${start.score} < 60", "fail", null);
        flow.addConditionalConnection("start", router);

        flow.addWorkflowComp("pass", new PassthroughComponent(),
                Map.of("result", "及格"), null);
        flow.addWorkflowComp("fail", new PassthroughComponent(),
                Map.of("result", "不及格"), null);
        flow.setEndComp("end", new PassthroughComponent(),
                Map.of("grade", "${pass.result}", "fail_grade", "${fail.result}"), null);
        flow.addConnection("pass", "end");
        flow.addConnection("fail", "end");

        WorkflowOutput highScore = flow.invoke(Map.of("score", 85), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, highScore.getState());
        System.out.println("[Workflow Branch High] Result: " + highScore.getResult());

        WorkflowOutput lowScore = flow.invoke(Map.of("score", 45), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, lowScore.getState());
        System.out.println("[Workflow Branch Low] Result: " + lowScore.getResult());
    }

    @Test
    @DisplayName("Workflow with BranchComponent")
    void testWorkflowBranchComponent() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), null, null);

        BranchComponent branch = new BranchComponent();
        branch.addBranch("${value} > 100", List.of("big"), "high");
        branch.addBranch("${value} <= 100", List.of("small"), "low");

        flow.addWorkflowComp("switch", branch);
        flow.addWorkflowComp("big", new PassthroughComponent(),
                Map.of("label", "大数"), null);
        flow.addWorkflowComp("small", new PassthroughComponent(),
                Map.of("label", "小数"), null);
        flow.setEndComp("end", new PassthroughComponent(),
                Map.of("big_label", "${big.label}", "small_label", "${small.label}"), null);

        flow.addConnection("start", "switch");
        flow.addConnection("big", "end");
        flow.addConnection("small", "end");

        WorkflowOutput result = flow.invoke(Map.of("value", 200), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        System.out.println("[Workflow BranchComp] Result: " + result.getResult());
    }

    @Test
    @DisplayName("Workflow with WorkflowCard metadata")
    void testWorkflowWithCard() {
        WorkflowCard card = WorkflowCard.builder()
                .id("test-workflow-001")
                .name("Test Workflow")
                .description("A test workflow for integration testing")
                .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("input", "${input}"), null);
        flow.setEndComp("end", new PassthroughComponent(),
                Map.of("output", "${start.input}"), null);
        flow.addConnection("start", "end");

        WorkflowOutput output = flow.invoke(Map.of("input", "test"), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertEquals("test-workflow-001", flow.getCard().getId());
        System.out.println("[Workflow Card] Id=" + flow.getCard().getId()
                + ", Result=" + output.getResult());
    }

    @Test
    @DisplayName("Workflow with data transformation pipeline")
    void testWorkflowDataPipeline() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("a", "${a}", "b", "${b}"), null);
        flow.addWorkflowComp("add", new AddNumbersComponent(),
                Map.of("a", "${start.a}", "b", "${start.b}"), null);
        flow.setEndComp("end", new PassthroughComponent(),
                Map.of("total", "${add.sum}"), null);
        flow.addConnection("start", "add");
        flow.addConnection("add", "end");

        WorkflowOutput output = flow.invoke(Map.of("a", 15, "b", 27), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output.getResult();
        assertEquals(42.0, result.get("total"));
        System.out.println("[Workflow Pipeline] Result: " + result);
    }
}
