/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.MockNodes;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_branch_comp.py} in
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
class TestBranchComp {

    @Test
    @DisplayName("sub workflow branch router chooses the matching target")
    void testSubWorkflowWithBranch() {
        Workflow workflow = new Workflow();
        workflow.setStartComp("s", new com.openjiuwen.core.workflow.component.Start(),
                Map.of("input", "${data}"), null);
        workflow.addWorkflowComp("sub_workflow", new MockSubWorkflowComponent());
        workflow.setEndComp("e", new End(), Map.of("end_out", "${print_inputs}"), null);
        workflow.addConnection("s", "sub_workflow");
        workflow.addConnection("sub_workflow", "e");

        WorkflowOutput result = workflow.invoke(
                Map.of("data", "aaa"),
                WorkflowSessions.createWorkflowSession(),
                null);

        assertNotNull(result);
        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
    }

    private static final class MockSubWorkflowComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(subWorkflow().invoke(
                        Map.of("a", "1", "b", 2),
                        session,
                        context,
                        true).getResult());
            }
            return Map.of("results", results);
        }

        @Override
        public boolean graphInvoker() {
            return true;
        }

        @Override
        public String componentType() {
            return "sub_workflow";
        }

        private Workflow subWorkflow() {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockNodes.MockStartNode("start"),
                    Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)), null);

            BranchRouter router = new BranchRouter();
            router.addBranch("len(${start.d}) > 2", "a", null);
            router.addBranch("len(${start.d}) < 2", "b", null);

            flow.addConditionalConnection("start", router);
            flow.addWorkflowComp("a", new MockNodes.Node1("a"), Map.of("a", "${start.a}"), null);
            flow.addWorkflowComp("b", new MockNodes.Node1("b"), Map.of("b", "${start.b}"), null);
            flow.setEndComp("end", new End(), Map.of("result1", "${a.a}", "result2", "${b.b}"), null);
            flow.addConnection("a", "end");
            flow.addConnection("b", "end");
            return flow;
        }
    }

    @Test
    @DisplayName("addBranch rejects invalid condition and target values")
    void testAddBranchError() {
        BranchComponent branch = new BranchComponent();

        assertThrows(BaseError.class, () -> branch.addBranch(null, "a", ""));
        assertThrows(BaseError.class, () -> branch.addBranch("sss", "", ""));
        assertThrows(BaseError.class, () -> branch.addBranch("sss", null, ""));
        assertThrows(BaseError.class, () -> branch.addBranch("sss", new ArrayList<>(List.of("", "xxx")), ""));

        List<String> targetWithNull = new ArrayList<>();
        targetWithNull.add("xxx");
        targetWithNull.add(null);
        assertThrows(BaseError.class, () -> branch.addBranch("sss", targetWithNull, ""));
    }

    @Test
    @DisplayName("is_empty expression matches Python edge cases")
    void testExpressionIsEmpty() throws Exception {
        assertTrue(evaluate("is_empty(${start.input})", null));
        assertTrue(evaluate("is_empty(${start.input})", List.of()));
        assertTrue(evaluate("is_empty(${start.input})", ""));
        assertTrue(evaluate("is_empty(${start.input})", Map.of()));

        BaseError numberError = assertThrows(BaseError.class,
                () -> evaluate("is_empty(${start.input})", 0));
        assertEquals(StatusCode.EXPRESSION_EVAL_ERROR.getCode(), numberError.getCode());

        BaseError decimalError = assertThrows(BaseError.class,
                () -> evaluate("is_not_empty(${start.input})", 1.2));
        assertEquals(StatusCode.EXPRESSION_EVAL_ERROR.getCode(), decimalError.getCode());

        List<Object> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        listWithNull.add("y");
        assertTrue(evaluate("is_empty(${start.input}[0])", listWithNull));

        Map<String, Object> mapWithNull = new LinkedHashMap<>();
        mapWithNull.put("x", null);
        assertTrue(evaluate("is_empty(${start.input}['x'])", mapWithNull));

        List<Object> nestedListWithNull = new ArrayList<>();
        nestedListWithNull.add(null);
        Map<String, Object> nestedMapWithNull = new LinkedHashMap<>();
        nestedMapWithNull.put("x", nestedListWithNull);
        assertTrue(evaluate("is_empty(${start.input}['x'][0])", nestedMapWithNull));
    }

    @Test
    @DisplayName("is_not_empty expression matches Python edge cases")
    void testExpressionIsNotEmpty() throws Exception {
        assertTrue(evaluate("is_not_empty(${start.input})", "x"));
        assertTrue(evaluate("is_not_empty(${start.input})", Map.of("a", "a")));
        assertTrue(evaluate("is_not_empty(${start.input})", List.of("a")));
        assertFalse(evaluate("is_not_empty(${start.input})", null));

        BaseError error = assertThrows(BaseError.class,
                () -> evaluate("is_not_empty(${start.input})", 1.2));
        assertEquals(StatusCode.EXPRESSION_EVAL_ERROR.getCode(), error.getCode());

        assertTrue(evaluate("is_not_empty(${start.input}[0])", List.of("x", "y")));
        assertTrue(evaluate("is_not_empty(${start.input}['x'])", Map.of("x", "x")));
        assertTrue(evaluate("is_not_empty(${start.input}['x'][0])", Map.of("x", List.of("x"))));
    }

    @Test
    @DisplayName("length expression handles containers and rejects scalar numbers")
    void testExpressionLength() throws Exception {
        BaseError error = assertThrows(BaseError.class,
                () -> evaluate("length(${start.input}) == 0", 0));
        assertEquals(StatusCode.EXPRESSION_EVAL_ERROR.getCode(), error.getCode());

        assertTrue(evaluate("length(${start.input}) == 0", Map.of()));
        assertTrue(evaluate("length(${start.input}) == 0", List.of()));
        assertTrue(evaluate("length(${start.input}) == 0", ""));
    }

    private static boolean evaluate(String expression, Object value) throws Exception {
        ExpressionCondition condition = new ExpressionCondition(expression);
        Method method = ExpressionCondition.class.getDeclaredMethod(
                "evaluateExpression", String.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("${start.input}", value);
        try {
            return (Boolean) method.invoke(condition, expression, inputs);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
