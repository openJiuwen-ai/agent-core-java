/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class T01161ArrayConditionTest {

    private T01161ArrayConditionTest() {
    }

    public static void main(String[] args) {
        testArrayConditionReadsListInput();
        testArrayConditionReadsStringInputLikePythonSequence();
        testArrayConditionStopsAtMissingInput();
        testArrayConditionInSessionAcceptsArrayAsTuple();
        testArrayConditionInSessionRejectsInvalidArrays();
        System.out.println("T01161ArrayConditionTest passed");
    }

    private static void testArrayConditionReadsListInput() {
        ArrayCondition condition = new ArrayCondition(Map.of("item", "${input.item}"));
        Object result = condition.doInvoke(Map.of("item", List.of("a", "b")), newSession(1));

        Object[] tuple = assertTuple(result);
        assertEquals(true, tuple[0], "list condition result");
        assertEquals(Map.of("item", "b"), tuple[1], "list io updates");
    }

    private static void testArrayConditionReadsStringInputLikePythonSequence() {
        ArrayCondition condition = new ArrayCondition(Map.of("item", "${input.item}"));
        Object result = condition.doInvoke(Map.of("item", "abc"), newSession(1));

        Object[] tuple = assertTuple(result);
        assertEquals(true, tuple[0], "string condition result");
        assertEquals(Map.of("item", "b"), tuple[1], "string index should return one-character string");
    }

    private static void testArrayConditionStopsAtMissingInput() {
        ArrayCondition condition = new ArrayCondition(Map.of("item", "${input.item}"));

        assertEquals(false, condition.doInvoke(Map.of(), newSession(0)), "missing input should stop loop");
    }

    private static void testArrayConditionInSessionAcceptsArrayAsTuple() {
        Map<String, Object> arrays = new LinkedHashMap<>();
        arrays.put("item", new Object[]{"x", "y"});
        ArrayConditionInSession condition = new ArrayConditionInSession(arrays);
        Object result = condition.doInvoke(Map.of(), newSession(1));

        Object[] tuple = assertTuple(result);
        assertEquals(true, tuple[0], "array-as-tuple condition result");
        assertEquals(Map.of("item", "y"), tuple[1], "array-as-tuple io updates");
    }

    private static void testArrayConditionInSessionRejectsInvalidArrays() {
        expectIllegalArgument(() -> new ArrayConditionInSession(mapWithValue("item", null)),
                "cannot be None");
        expectIllegalArgument(() -> new ArrayConditionInSession(Map.of("item", "abc")),
                "Expected list/tuple");
    }

    private static WorkflowRuntimeSession newSession(int index) {
        WorkflowRuntimeSession session = new WorkflowRuntimeSession("workflow", null, "session",
                InMemoryState.create(), null);
        session.state().update(Map.of(Constant.INDEX, index));
        session.state().commit();
        return session;
    }

    private static Map<String, Object> mapWithValue(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static Object[] assertTuple(Object result) {
        if (!(result instanceof Object[] tuple)) {
            throw new AssertionError("expected Object[] tuple, got " + result);
        }
        return tuple;
    }

    private static void expectIllegalArgument(Runnable runnable, String messagePart) {
        try {
            runnable.run();
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains(messagePart)) {
                return;
            }
            throw new AssertionError("unexpected error message: " + exception.getMessage());
        }
        throw new AssertionError("expected IllegalArgumentException containing " + messagePart);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
