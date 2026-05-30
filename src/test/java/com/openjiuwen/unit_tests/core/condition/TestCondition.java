/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.condition.AlwaysTrue;
import com.openjiuwen.core.workflow.condition.ArrayCondition;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import com.openjiuwen.core.workflow.condition.FuncCondition;
import com.openjiuwen.core.workflow.condition.NumberCondition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's {@code tests.unit_tests.core.condition.test_condition}.
 */
class TestCondition {

    private BaseSession mockSession;
    private WorkflowStateCollection mockState;

    @BeforeEach
    void setUp() {
        mockSession = mock(BaseSession.class);
        mockState = mock(WorkflowStateCollection.class);
        when(mockSession.state()).thenReturn(mockState);
        when(mockState.getInputs(any())).thenReturn(Map.of());
        when(mockState.get(Constant.INDEX)).thenReturn(0);
        when(mockState.getGlobal(any())).thenReturn(null);
    }

    @Test
    void testConditionBaseClass() {
        Condition condition = new Condition("test_input_schema") {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return true;
            }
        };

        assertEquals("test_input_schema", field(condition, "inputSchema"));

        when(mockState.getInputs("test_input_schema")).thenReturn(Map.of());
        assertTrue(condition.evaluate(mockSession));
        verify(mockState).getInputs("test_input_schema");

        Condition noSchemaCondition = new Condition() {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return true;
            }
        };
        assertNull(field(noSchemaCondition, "inputSchema"));
        clearInvocations(mockState);

        assertTrue(noSchemaCondition.evaluate(mockSession));
        verify(mockState, never()).getInputs(any());
    }

    @Test
    void testConditionWithTupleResult() {
        Map<String, Object> outputs = Map.of("output_key", "output_value");
        Condition condition = new Condition("test_input_schema") {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return new Object[]{true, outputs};
            }
        };

        when(mockState.getInputs("test_input_schema")).thenReturn(Map.of());
        assertTrue(condition.evaluate(mockSession));
        verify(mockState).setOutputs(outputs);
    }

    @Test
    void testFuncConditionInvoke() {
        FuncCondition funcCondition = new FuncCondition(() -> true);

        Object result = funcCondition.doInvoke(Map.of(), mockSession);

        assertEquals(true, result);
    }

    @Test
    void testFuncConditionTraceInfo() {
        BooleanSupplier testFunc = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }

            @Override
            public String toString() {
                return "test_func";
            }
        };
        FuncCondition funcCondition = new FuncCondition(testFunc);

        assertEquals("test_func", funcCondition.traceInfo(mockSession));
    }

    @Test
    void testAlwaysTrueInvoke() {
        AlwaysTrue alwaysTrue = new AlwaysTrue();

        assertEquals(true, alwaysTrue.doInvoke(Map.of(), mockSession));
        assertEquals(true, alwaysTrue.doInvoke(Map.of("key", "value"), mockSession));
    }

    @Test
    void testArrayConditionInitialization() {
        Map<String, Object> arrays = new LinkedHashMap<>();
        arrays.put("item", List.of(1, 2, 3));

        ArrayCondition arrayCondition = new ArrayCondition(arrays);

        assertEquals(arrays, field(arrayCondition, "arrays"));
        assertEquals(arrays, field(arrayCondition, "inputSchema"));
    }

    @Test
    void testArrayConditionInvokeWithinLimit() {
        when(mockState.get(Constant.INDEX)).thenReturn(0);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("item", List.of(1, 2, 3));
        inputs.put("another_item", List.of("a", "b", "c"));
        Map<String, Object> arrays = new LinkedHashMap<>();
        arrays.put("item", "${input.item}");
        arrays.put("another_item", "${input.another_item}");
        ArrayCondition arrayCondition = new ArrayCondition(arrays);

        Object[] result = assertInstanceOf(Object[].class, arrayCondition.doInvoke(inputs, mockSession));

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("item", 1);
        updates.put("another_item", "a");
        assertEquals(true, result[0]);
        assertEquals(updates, result[1]);
        verify(mockState).update(updates);
    }

    @Test
    void testArrayConditionInvokeBeyondLimit() {
        when(mockState.get(Constant.INDEX)).thenReturn(3);
        Map<String, Object> inputs = Map.of("item", List.of(1, 2, 3));
        ArrayCondition arrayCondition = new ArrayCondition(Map.of("item", "${input.item}"));

        Object result = arrayCondition.doInvoke(inputs, mockSession);

        assertEquals(false, result);
    }

    @Test
    void testNumberConditionInitialization() {
        int limit = 5;
        NumberCondition numberCondition = new NumberCondition(limit);

        assertEquals(limit, field(numberCondition, "limit"));
        assertEquals(limit, field(numberCondition, "inputSchema"));
    }

    @Test
    void testNumberConditionInvokeWithinLimit() {
        when(mockState.get(Constant.INDEX)).thenReturn(2);
        NumberCondition numberCondition = new NumberCondition("${input.limit}");

        Object result = numberCondition.doInvoke(5, mockSession);

        assertEquals(true, result);
    }

    @Test
    void testNumberConditionInvokeBeyondLimit() {
        when(mockState.get(Constant.INDEX)).thenReturn(5);
        NumberCondition numberCondition = new NumberCondition("${input.limit}");

        Object result = numberCondition.doInvoke(5, mockSession);

        assertEquals(false, result);
    }

    @Test
    void testExpressionConditionInitialization() {
        String expression = "${a} > 5 && ${b} < 10";
        ExpressionCondition exprCondition = new ExpressionCondition(expression);

        assertEquals(expression, field(exprCondition, "expression"));
    }

    @Test
    void testExpressionConditionInvokeWithTrueResult() {
        assertTrue(evaluate("${a} > 5 && ${b} < 10", Map.of("a", 6, "b", 8)));
    }

    @Test
    void testExpressionConditionInvokeWithFalseResult() {
        assertFalse(evaluate("${a} > 5 && ${b} < 10", Map.of("a", 4, "b", 8)));
    }

    @Test
    void testExpressionConditionWithEmptyExpression() {
        ExpressionCondition exprCondition = new ExpressionCondition("");

        assertEquals(true, exprCondition.doInvoke(Map.of(), mockSession));
    }

    @Test
    void testExpressionConditionTraceInfo() {
        String expression = "${a} > 5 && ${b} < 10";
        setGlobals(Map.of("a", 6, "b", 8));
        ExpressionCondition exprCondition = new ExpressionCondition(expression);

        @SuppressWarnings("unchecked")
        Map<String, Object> traceInfo = (Map<String, Object>) exprCondition.traceInfo(mockSession);

        assertEquals(expression, traceInfo.get("bool_expression"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) traceInfo.get("inputs");
        assertTrue(inputs.containsKey("${a}"));
        assertTrue(inputs.containsKey("${b}"));
    }

    @Test
    void testExpressionPreprocessingOperators() {
        assertTrue(evaluate("${a} > 5 && ${b} < 10 || ${c} == 3", Map.of("a", 6, "b", 8, "c", 3)));
    }

    @Test
    void testExpressionPreprocessingKeywords() {
        assertTrue(evaluate("${a} > 0 && true", Map.of("a", 4)));
    }

    @Test
    void testExpressionPreprocessingFunctions() {
        Map<String, Object> globals = Map.of("empty_list", List.of(), "non_empty_list", List.of(1, 2, 3));

        assertTrue(evaluate("is_empty(${empty_list}) && is_not_empty(${non_empty_list})", globals));
    }

    @Test
    void testExpressionArithmeticOperators() {
        Map<String, Object> globals = Map.of("a", 6, "b", 5, "c", 4, "d", 4);

        assertTrue(evaluate("${a} + ${b} > 10 && ${c} * ${d} < 20", globals));
    }

    @Test
    void testExpressionComparisonOperators() {
        assertTrue(evaluate("${a} == ${b} && ${c} != ${d}", Map.of("a", 5, "b", 5, "c", 10, "d", 20)));
        assertTrue(evaluate("${a} in ${list} && ${b} not_in ${list}",
                Map.of("a", 1, "b", 4, "list", List.of(1, 2, 3))));
        assertTrue(evaluate("${a} is None && ${b} is not None", mapWithNull("a", null, "b", "value")));
    }

    @Test
    void testExpressionBooleanOperators() {
        assertTrue(evaluate("(${a} > 5 and ${b} < 10) or ${c} == 3", Map.of("a", 4, "b", 8, "c", 3)));
    }

    @Test
    void testExpressionUnaryOperators() {
        assertTrue(evaluate("-(${a}) > 0 && not(${b} > 10)", Map.of("a", -5, "b", 5)));
    }

    @Test
    void testExpressionDataStructures() {
        assertTrue(evaluate("${a} == 2", Map.of("a", 2)));
    }

    @Test
    void testExpressionFuncCalls() {
        assertTrue(evaluate("len(${list}) == 3", Map.of("list", List.of(1, 2, 3))));
    }

    @Test
    void testExpressionSyntaxError() {
        assertExpressionError("${a} > 5 &&", Map.of());
    }

    @Test
    void testExpressionEvalError() {
        assertExpressionError("${a} + 'string'", Map.of("a", 5));
    }

    @Test
    void testDisallowedOperations() {
        assertExpressionError("disallowed_var > 5", Map.of());
        assertExpressionError("${a}.disallowed_attr", Map.of("a", new Object()));
        assertExpressionError("str(${a})", Map.of("a", 5));
    }

    @Test
    void testComplexNestedExpressions() {
        String expression = "(((${a} > 5 and ${b} < 10) or (${c} == 3 and ${d} != 4)) and (len(${list}) > 0))";
        Map<String, Object> globals = Map.of("a", 4, "b", 8, "c", 3, "d", 5, "list", List.of(1, 2, 3));

        assertTrue(evaluate(expression, globals));
    }

    @Test
    void testSecurityMechanism() {
        assertExpressionError("__import__('os').system('ls')", Map.of());
        assertExpressionError("import('os').system('ls')", Map.of());
        assertExpressionError("open('test.txt', 'r')", Map.of());
        assertExpressionError("eval('2 + 2')", Map.of());
    }

    @Test
    void testPreventObjectMethodEscape() {
        BaseError classError = assertExpressionError("${obj}.__class__", Map.of("obj", new Object()));
        assertTrue(classError.toString().contains("prohibited")
                || classError.toString().contains("Disallowed operation"));

        BaseError subclassesError = assertExpressionError("${obj}.__class__.__subclasses__()",
                Map.of("obj", new Object()));
        assertTrue(subclassesError.toString().contains("prohibited")
                || subclassesError.toString().contains("Disallowed operation"));
    }

    @Test
    void testPreventSpecialMethodAccess() {
        BaseError moduleError = assertExpressionError("len.__module__", Map.of());
        assertTrue(moduleError.toString().contains("expression evaluation error"));

        BaseError dictError = assertExpressionError("${obj}.__dict__", Map.of("obj", new Object()));
        assertTrue(dictError.toString().contains("expression evaluation error"));
    }

    @Test
    void testPreventResourceExhaustion() {
        assertExpressionError("[0] * 1000000000", Map.of());

        assertDoesNotThrow(() -> {
            try {
                new ExpressionCondition("dict([(i, i) for i in range(1000000000)])").doInvoke(Map.of(), mockSession);
            } catch (BaseError ignored) {
            }
        });

        setGlobals(Map.of("large_list", IntStream.range(0, 100).boxed().toList()));
        assertDoesNotThrow(() -> {
            try {
                new ExpressionCondition("${large_list}[0:2000]").doInvoke(Map.of(), mockSession);
            } catch (BaseError ignored) {
            }
        });
    }

    @Test
    void testLargeCollectionProtection() {
        assertExpressionError("is_not_empty([0] * (10 ** 10))", Map.of());

        List<Integer> largeList = IntStream.rangeClosed(1, Constant.MAX_COLLECTION_SIZE + 1).boxed().toList();
        assertExpressionError("is_not_empty(${large_list})", Map.of("large_list", largeList));

        assertExpressionError("2 ** 1000", Map.of());
    }

    @Test
    void testLargeListLiteralProtection() {
        String tail = IntStream.rangeClosed(4, 100)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));
        String expression = "len([1, 2, 3, " + tail + "]) == 100";
        assertTrue(evaluate(expression, Map.of()));

        List<Integer> veryLargeList = IntStream.rangeClosed(1, Constant.MAX_COLLECTION_SIZE + 1).boxed().toList();
        assertExpressionError("${very_large_list} * 2", Map.of("very_large_list", veryLargeList));
    }

    @Test
    void testExpressionLengthLimit() {
        ExpressionCondition safeCondition = new ExpressionCondition("${a} > 0");
        try {
            safeCondition.doInvoke(Map.of(), mockSession);
        } catch (BaseError e) {
            if (e.toString().contains("length exceeds maximum allowed length")) {
                fail("Expression within length limit was rejected");
            }
        }

        String tooLongExpression = "${a} > 0".repeat(Constant.MAX_EXPRESSION_LENGTH / 8 + 1);
        BaseError error = assertThrows(BaseError.class, () -> new ExpressionCondition(tooLongExpression));
        assertTrue(error.toString().contains("length exceeds maximum allowed length"));
    }

    @Test
    void testExpressionNestingDepthLimit() {
        String tooDeepNesting = tooDeepExpression();
        BaseError error = assertExpressionError(tooDeepNesting, Map.of("a", 1));

        assertTrue(error.toString().contains("nesting depth exceeds maximum allowed depth"));
    }

    @Test
    void testAstNestingDepthLimit() {
        String normalExpression = "(((${a} > 0) && (${b} < 100)))";
        assertTrue(evaluate(normalExpression, Map.of("a", 5, "b", 50)));

        BaseError error = assertExpressionError(tooDeepExpression(), Map.of("a", 5));
        assertTrue(error.toString().contains("nesting depth exceeds maximum allowed depth"));
    }

    @Test
    void testStringComparisonWithTrueLiteral() {
        ExpressionCondition exprCondition = new ExpressionCondition("${a} ==\"true\"");

        setGlobals(Map.of("a", "true"));
        assertEquals(true, exprCondition.doInvoke(Map.of(), mockSession));

        setGlobals(Map.of("a", "false"));
        assertEquals(false, exprCondition.doInvoke(Map.of(), mockSession));
    }

    @Test
    void testStringComparisonWithFalseLiteral() {
        ExpressionCondition exprCondition = new ExpressionCondition("${a} ==\"false\"");

        setGlobals(Map.of("a", "false"));
        assertEquals(true, exprCondition.doInvoke(Map.of(), mockSession));

        setGlobals(Map.of("a", "true"));
        assertEquals(false, exprCondition.doInvoke(Map.of(), mockSession));
    }

    @Test
    void testStringLiteralPreservationWithBooleanIdentifier() {
        setGlobals(Map.of("a", "true"));
        assertTrue((Boolean) new ExpressionCondition("${a} ==\"true\" && true").doInvoke(Map.of(), mockSession));

        setGlobals(Map.of("a", "false"));
        assertTrue((Boolean) new ExpressionCondition("${a} ==\"false\" && true").doInvoke(Map.of(), mockSession));
    }

    @Test
    void testSingleQuoteStringLiteralPreservation() {
        setGlobals(Map.of("a", "true"));
        assertTrue(evaluate("${a} =='true'", Map.of("a", "true")));

        setGlobals(Map.of("a", "false"));
        assertTrue(evaluate("${a} =='false'", Map.of("a", "false")));
        assertFalse(evaluate("${a} =='true'", Map.of("a", "false")));

        assertTrue(evaluate("${a} =='true' && true", Map.of("a", "true")));
    }

    private boolean evaluate(String expression, Map<String, Object> globals) {
        setGlobals(globals);
        return (Boolean) new ExpressionCondition(expression).doInvoke(Map.of(), mockSession);
    }

    private BaseError assertExpressionError(String expression, Map<String, Object> globals) {
        setGlobals(globals);
        return assertThrows(BaseError.class,
                () -> new ExpressionCondition(expression).doInvoke(Map.of(), mockSession));
    }

    private void setGlobals(Map<String, Object> values) {
        doAnswer(invocation -> {
            Object key = invocation.getArgument(0);
            return key == null ? null : values.get(key);
        }).when(mockState).getGlobal(any());
    }

    private static Map<String, Object> mapWithNull(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static String tooDeepExpression() {
        String nested = "${a}";
        for (int i = 0; i < Constant.MAX_AST_DEPTH + 5; i++) {
            nested = "(" + nested + " > 0)";
        }
        return nested;
    }

    private static Object field(Object target, String fieldName) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        fail("Field not found: " + fieldName);
        return null;
    }
}
