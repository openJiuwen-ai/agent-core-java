/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class T01163ExpressionConditionTest {

    private T01163ExpressionConditionTest() {
    }

    public static void main(String[] args) {
        testBasicExpressions();
        testTraceInfoAndRawReturn();
        testFunctionsAndCollections();
        testOperatorsAndComparisons();
        testStringLiteralPreservation();
        testAttributeAndSubscriptAccess();
        testErrorAndSecurityPaths();
        System.out.println("T01163ExpressionConditionTest passed");
    }

    private static void testBasicExpressions() {
        assertEquals(true, invoke("${a} > 5 && ${b} < 10", Map.of("a", 6, "b", 8)), "basic true");
        assertEquals(false, invoke("${a} > 5 && ${b} < 10", Map.of("a", 4, "b", 8)), "basic false");
        assertEquals(true, invoke("", Map.of()), "empty expression");
        assertEquals(true, invoke("${a} > 5 && ${b} < 10 || ${c} == 3",
                Map.of("a", 6, "b", 8, "c", 3)), "operator preprocessing");
        assertEquals(true, invoke("${a} > 0 && true", Map.of("a", 4)), "keyword preprocessing");
    }

    private static void testTraceInfoAndRawReturn() {
        ExpressionCondition condition = new ExpressionCondition("${a} > 5 && ${b} < 10");

        @SuppressWarnings("unchecked")
        Map<String, Object> traceInfo = (Map<String, Object>) condition.traceInfo(session(Map.of("a", 6, "b", 8)));
        assertEquals("${a} > 5 && ${b} < 10", traceInfo.get("bool_expression"), "trace expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) traceInfo.get("inputs");
        assertTrue(inputs.containsKey("${a}") && inputs.containsKey("${b}"), "trace inputs");

        assertEquals(0, invoke("${value}", Map.of("value", 0)), "raw non-comparison result");
        assertEquals(true, new ExpressionCondition("${value}").evaluate(session(Map.of("value", 1))),
                "evaluate uses Python truthiness");
    }

    private static void testFunctionsAndCollections() {
        assertEquals(true, invoke("is_empty(${empty_list}) && is_not_empty(${non_empty_list})",
                Map.of("empty_list", List.of(), "non_empty_list", List.of(1, 2, 3))), "safe emptiness");
        assertEquals(true, invoke("len(${list}) == 3", Map.of("list", List.of(1, 2, 3))), "len function");
        assertEquals(true, invoke("length(${text}) == 4", Map.of("text", "abcd")), "length alias");
        assertEquals(true, invoke("sum([1, 2, 3]) == 6 && bool(${non_empty_list})",
                Map.of("non_empty_list", List.of("x"))), "sum and bool runtime helpers");
        assertEquals(true, invoke("len([1, 2, 3, 4, 5]) == 5", Map.of()), "list literal");
    }

    private static void testOperatorsAndComparisons() {
        assertEquals(true, invoke("${a} + ${b} > 10 && ${c} * ${d} < 20",
                Map.of("a", 6, "b", 5, "c", 4, "d", 4)), "arithmetic");
        assertEquals(true, invoke("${a} == ${b} && ${c} != ${d}",
                Map.of("a", 5, "b", 5, "c", 10, "d", 20)), "equals and not equals");
        assertEquals(true, invoke("${a} in ${list} && ${b} not_in ${list}",
                Map.of("a", 1, "b", 4, "list", List.of(1, 2, 3))), "in and not in");
        assertEquals(true, invoke("${a} is None && ${b} is not None", mapWithNull("a", null, "b", "value")),
                "identity comparison");
        assertEquals(true, invoke("-(${a}) > 0 && not(${b} > 10)", Map.of("a", -5, "b", 5)),
                "unary operators");
        assertEquals(true, invoke("2 ** 3 == 8 && 5 // 2 == 2", Map.of()), "power and floor division");
        assertEquals(true, invoke("3 < 4 < 5", Map.of()), "comparison chain");
        assertEquals(true, invoke("(((${a} > 5 and ${b} < 10) or (${c} == 3 and ${d} != 4)) "
                        + "and (len(${list}) > 0))",
                Map.of("a", 4, "b", 8, "c", 3, "d", 5, "list", List.of(1, 2, 3))),
                "complex nested expression");
    }

    private static void testStringLiteralPreservation() {
        assertEquals(true, invoke("${a} ==\"true\"", Map.of("a", "true")), "double true string");
        assertEquals(false, invoke("${a} ==\"true\"", Map.of("a", "false")), "double true mismatch");
        assertEquals(true, invoke("${a} =='false'", Map.of("a", "false")), "single false string");
        assertEquals(true, invoke("${a} =='true' && true", Map.of("a", "true")),
                "single string with boolean literal");
    }

    private static void testAttributeAndSubscriptAccess() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("x", 7);
        nested.put("child", Map.of("value", 3));

        assertEquals(true, invoke("${obj}.x == 7", Map.of("obj", nested)), "dict attribute");
        assertEquals(true, invoke("${obj}['x'] == 7", Map.of("obj", nested)), "dict subscript");
        assertEquals(true, invoke("${obj}.child.value == 3", Map.of("obj", nested)), "nested dict attribute");
        assertError("${obj}.missing", Map.of("obj", nested), "missing dict attribute");
        assertError("${obj}['missing']", Map.of("obj", nested), "missing dict key");
    }

    private static void testErrorAndSecurityPaths() {
        assertError("${a} > 5 &&", Map.of(), "syntax error");
        assertError("${a} + 'string'", Map.of("a", 5), "eval error");
        assertError("disallowed_var > 5", Map.of(), "undefined variable");
        assertError("str(${a})", Map.of("a", 5), "unknown function");
        assertError("__import__('os').system('ls')", Map.of(), "import prohibited");
        assertError("${obj}.__class__", Map.of("obj", new Object()), "dunder attribute prohibited");
        assertError("len.__module__", Map.of(), "module attribute prohibited");
        assertError("[0] * 1000000000", Map.of(), "large list multiplication");
        assertError("2 ** 1000", Map.of(), "large exponent");
        assertError("is_not_empty(${large_list})",
                Map.of("large_list", java.util.stream.IntStream.rangeClosed(1, Constant.MAX_COLLECTION_SIZE + 1)
                        .boxed().toList()),
                "large collection variable");

        String tooLongExpression = "${a} > 0".repeat(Constant.MAX_EXPRESSION_LENGTH / 8 + 1);
        assertConstructorError(tooLongExpression, "length limit");

        String nested = "${a}";
        for (int i = 0; i < Constant.MAX_AST_DEPTH + 5; i++) {
            nested = "(" + nested + " > 0)";
        }
        assertError(nested, Map.of("a", 1), "nesting limit");
    }

    private static Object invoke(String expression, Map<String, Object> globals) {
        return new ExpressionCondition(expression).doInvoke(Map.of(), session(globals));
    }

    private static WorkflowRuntimeSession session(Map<String, Object> globals) {
        return new WorkflowRuntimeSession("workflow", null, "session",
                InMemoryState.create(null, globals, null, null, null), null);
    }

    private static void assertError(String expression, Map<String, Object> globals, String message) {
        try {
            invoke(expression, globals);
        } catch (BaseError expected) {
            return;
        }
        throw new AssertionError(message + ": expected BaseError");
    }

    private static void assertConstructorError(String expression, String message) {
        try {
            new ExpressionCondition(expression);
        } catch (BaseError expected) {
            return;
        }
        throw new AssertionError(message + ": expected BaseError");
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

    private static Map<String, Object> mapWithNull(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
