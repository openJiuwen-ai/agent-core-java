/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import java.util.Map;

public final class T01164NumberConditionTest {

    private T01164NumberConditionTest() {
    }

    public static void main(String[] args) {
        testNumberCondition();
        testNumberConditionInSession();
        testInvalidValues();
        System.out.println("T01164NumberConditionTest passed");
    }

    private static void testNumberCondition() {
        NumberCondition condition = new NumberCondition("${input.limit}");

        assertEquals(true, condition.doInvoke(5, session(2)), "index below input limit");
        assertEquals(false, condition.doInvoke(5, session(5)), "index equal to input limit");
        assertEquals("${input.limit}", field(condition, "limit"), "limit field");
        assertEquals("${input.limit}", field(condition, "inputSchema"), "input schema");
    }

    private static void testNumberConditionInSession() {
        NumberConditionInSession condition = new NumberConditionInSession(3);

        assertEquals(true, condition.doInvoke(Map.of(), session(2)), "index below fixed limit");
        assertEquals(false, condition.doInvoke(Map.of(), session(3)), "index equal to fixed limit");
        assertEquals(3, field(condition, "limit"), "fixed limit field");
    }

    private static void testInvalidValues() {
        assertThrows(() -> new NumberCondition("${input.limit}").doInvoke("not-number", session(0)),
                "non numeric input limit");
        assertThrows(() -> new NumberCondition("${input.limit}").doInvoke(1, session("not-number")),
                "non numeric index");
        assertThrows(() -> new NumberConditionInSession(null).doInvoke(Map.of(), session(0)),
                "null fixed limit");
    }

    private static WorkflowRuntimeSession session(Object index) {
        WorkflowRuntimeSession session = new WorkflowRuntimeSession("workflow", null, "session",
                InMemoryState.create(), null);
        session.state().update(Map.of(Constant.INDEX, index));
        session.state().commit();
        return session;
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message + ": expected RuntimeException");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static Object field(Object target, String fieldName) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("field not found: " + fieldName);
    }
}
