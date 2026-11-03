/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import java.util.Map;
import java.util.function.BooleanSupplier;

public final class T01162ConditionBaseTest {

    private T01162ConditionBaseTest() {
    }

    public static void main(String[] args) {
        testConditionEvaluateBooleanResult();
        testConditionEvaluateTupleResult();
        testFuncConditionInvokeAndTraceInfo();
        testAlwaysTrueInvokeAndTraceInfo();
        System.out.println("T01162ConditionBaseTest passed");
    }

    private static void testConditionEvaluateBooleanResult() {
        RecordingCondition condition = new RecordingCondition(true);

        assertTrue(condition.evaluate(newSession()), "boolean condition should evaluate true");
        assertEquals(Map.of(), condition.lastInputs, "no input schema should pass empty inputs");
    }

    private static void testConditionEvaluateTupleResult() {
        RecordingCondition condition = new RecordingCondition(new Object[]{true, Map.of("answer", 42)});

        assertTrue(condition.evaluate(newSession()), "tuple result should evaluate first item");
    }

    private static void testFuncConditionInvokeAndTraceInfo() {
        BooleanSupplier supplier = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return true;
            }

            @Override
            public String toString() {
                return "named_predicate";
            }
        };
        FuncCondition condition = new FuncCondition(supplier);

        assertEquals(true, condition.doInvoke(Map.of(), newSession()), "FuncCondition should call supplier");
        assertEquals("named_predicate", condition.traceInfo(newSession()), "trace info should expose callable name");
    }

    private static void testAlwaysTrueInvokeAndTraceInfo() {
        AlwaysTrue condition = new AlwaysTrue();

        assertEquals(true, condition.doInvoke(Map.of("ignored", "value"), newSession()),
                "AlwaysTrue should return true");
        assertEquals("True", condition.traceInfo(newSession()), "AlwaysTrue trace info");
    }

    private static WorkflowRuntimeSession newSession() {
        return new WorkflowRuntimeSession("workflow", null, "session", InMemoryState.create(), null);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingCondition extends Condition {
        private final Object result;
        private Object lastInputs;

        private RecordingCondition(Object result) {
            this.result = result;
        }

        @Override
        public Object doInvoke(Object inputs, BaseSession session) {
            this.lastInputs = inputs;
            return result;
        }
    }
}
