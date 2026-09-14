/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.condition.test_condition} in
 * {@code tests/unit_tests/core/condition/test_condition.py}.</p>
 */
class ConditionPythonParityTest {

    @TestFactory
    Collection<DynamicTest> conditionPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestCondition::test_condition_base_class", this::conditionBaseClass);
        add(tests, "TestCondition::test_condition_with_tuple_result", this::conditionWithTupleResult);
        add(tests, "TestFuncCondition::test_func_condition_invoke", this::funcConditionInvoke);
        add(tests, "TestFuncCondition::test_func_condition_trace_info", this::funcConditionTraceInfo);
        add(tests, "TestAlwaysTrue::test_always_true_invoke", this::alwaysTrueInvoke);
        add(tests, "TestArrayCondition::test_array_condition_initialization", this::arrayConditionInitialization);
        add(tests, "TestArrayCondition::test_array_condition_invoke_within_limit", this::arrayConditionWithinLimit);
        add(tests, "TestArrayCondition::test_array_condition_invoke_beyond_limit", this::arrayConditionBeyondLimit);
        add(tests, "TestNumberCondition::test_number_condition_initialization", this::numberConditionInitialization);
        add(tests, "TestNumberCondition::test_number_condition_invoke_within_limit", this::numberConditionWithinLimit);
        add(tests, "TestNumberCondition::test_number_condition_invoke_beyond_limit", this::numberConditionBeyondLimit);
        add(tests, "TestExpressionCondition::test_expression_condition_initialization",
                this::expressionConditionInitialization);
        add(tests, "TestExpressionCondition::test_expression_condition_invoke_with_true_result",
                this::expressionConditionTrueResult);
        add(tests, "TestExpressionCondition::test_expression_condition_invoke_with_false_result",
                this::expressionConditionFalseResult);
        add(tests, "TestExpressionCondition::test_expression_condition_with_empty_expression",
                this::expressionConditionEmpty);
        add(tests, "TestExpressionCondition::test_expression_condition_trace_info",
                this::expressionConditionTraceInfo);
        add(tests, "TestExpressionCondition::test_expression_preprocessing_operators",
                () -> assertEquals(true, invoke("${a} > 5 && ${b} < 10 || ${c} == 3",
                        Map.of("a", 6, "b", 8, "c", 3))));
        add(tests, "TestExpressionCondition::test_expression_preprocessing_keywords",
                () -> assertEquals(true, invoke("${a} > 0 && true", Map.of("a", 4))));
        add(tests, "TestExpressionCondition::test_expression_preprocessing_functions",
                () -> assertEquals(true, invoke("is_empty(${empty_list}) && is_not_empty(${non_empty_list})",
                        Map.of("empty_list", List.of(), "non_empty_list", List.of(1, 2, 3)))));
        add(tests, "TestExpressionCondition::test_expression_arithmetic_operators",
                () -> assertEquals(true, invoke("${a} + ${b} > 10 && ${c} * ${d} < 20",
                        Map.of("a", 6, "b", 5, "c", 4, "d", 4))));
        add(tests, "TestExpressionCondition::test_expression_comparison_operators",
                this::expressionComparisonOperators);
        add(tests, "TestExpressionCondition::test_expression_boolean_operators",
                () -> assertEquals(true, invoke("(${a} > 5 and ${b} < 10) or ${c} == 3",
                        Map.of("a", 4, "b", 8, "c", 3))));
        add(tests, "TestExpressionCondition::test_expression_unary_operators",
                () -> assertEquals(true, invoke("-(${a}) > 0 && not(${b} > 10)", Map.of("a", -5, "b", 5))));
        add(tests, "TestExpressionCondition::test_expression_data_structures",
                () -> assertEquals(true, invoke("${a} == 2", Map.of("a", 2))));
        add(tests, "TestExpressionCondition::test_expression_func_calls",
                () -> assertEquals(true, invoke("len(${list}) == 3", Map.of("list", List.of(1, 2, 3)))));
        add(tests, "TestExpressionCondition::test_expression_syntax_error",
                () -> assertThrows(BaseError.class, () -> invoke("${a} > 5 &&", Map.of())));
        add(tests, "TestExpressionCondition::test_expression_eval_error",
                () -> assertThrows(BaseError.class, () -> invoke("${a} + 'string'", Map.of("a", 5))));
        add(tests, "TestExpressionCondition::test_disallowed_operations", this::disallowedOperations);
        add(tests, "TestExpressionCondition::test_complex_nested_expressions", this::complexNestedExpression);
        add(tests, "TestExpressionCondition::test_security_mechanism", this::securityMechanism);
        add(tests, "TestExpressionCondition::test_prevent_object_method_escape", this::preventObjectMethodEscape);
        add(tests, "TestExpressionCondition::test_prevent_special_method_access", this::preventSpecialMethodAccess);
        add(tests, "TestExpressionCondition::test_prevent_resource_exhaustion", this::preventResourceExhaustion);
        add(tests, "TestExpressionCondition::test_large_collection_protection", this::largeCollectionProtection);
        add(tests, "TestExpressionCondition::test_large_list_literal_protection", this::largeListLiteralProtection);
        add(tests, "TestExpressionCondition::test_expression_length_limit", this::expressionLengthLimit);
        add(tests, "TestExpressionCondition::test_expression_nesting_depth_limit", this::expressionNestingDepthLimit);
        add(tests, "TestExpressionCondition::test_ast_nesting_depth_limit", this::astNestingDepthLimit);
        add(tests, "TestExpressionCondition::test_string_comparison_with_true_literal",
                this::stringComparisonWithTrueLiteral);
        add(tests, "TestExpressionCondition::test_string_comparison_with_false_literal",
                this::stringComparisonWithFalseLiteral);
        add(tests, "TestExpressionCondition::test_string_literal_preservation_with_boolean_identifier",
                this::stringLiteralPreservationWithBooleanIdentifier);
        add(tests, "TestExpressionCondition::test_single_quote_string_literal_preservation",
                this::singleQuoteStringLiteralPreservation);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void conditionBaseClass() throws Exception {
        RecordingCondition condition = new RecordingCondition(true, "test_input_schema");
        assertEquals("test_input_schema", condition.inputSchema);
        assertEquals(true, condition.atomicInvoke(Map.of("session", sessionWithIo(Map.of("test_input_schema", Map.of())))));

        RecordingCondition withoutSchema = new RecordingCondition(true, null);
        assertEquals(true, withoutSchema.atomicInvoke(Map.of("session", session())));
        assertEquals(Map.of(), withoutSchema.lastInputs);
    }

    private void conditionWithTupleResult() throws Exception {
        RecordingCondition condition = new RecordingCondition(new Object[]{true, Map.of("output_key", "output_value")},
                "test_input_schema");
        WorkflowRuntimeSession session = sessionWithIo(Map.of("test_input_schema", Map.of()));

        assertEquals(true, condition.atomicInvoke(Map.of("session", session)));
        assertEquals(Map.of("output_key", "output_value"), session.state().getOutputs(null));
    }

    private void funcConditionInvoke() {
        FuncCondition condition = new FuncCondition(() -> true);

        assertEquals(true, condition.doInvoke(Map.of(), session()));
    }

    private void funcConditionTraceInfo() {
        BooleanSupplier supplier = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }

            @Override
            public String toString() {
                return "test_func";
            }
        };

        assertEquals("test_func", new FuncCondition(supplier).traceInfo(session()));
    }

    private void alwaysTrueInvoke() {
        AlwaysTrue condition = new AlwaysTrue();

        assertEquals(true, condition.doInvoke(Map.of(), session()));
        assertEquals(true, condition.doInvoke(Map.of("key", "value"), session()));
    }

    private void arrayConditionInitialization() {
        Map<String, Object> arrays = Map.of("item", List.of(1, 2, 3));
        ArrayCondition condition = new ArrayCondition(arrays);

        assertEquals(arrays, condition.inputSchema);
    }

    private void arrayConditionWithinLimit() {
        ArrayCondition condition = new ArrayCondition(Map.of("item", "${input.item}",
                "another_item", "${input.another_item}"));
        @SuppressWarnings("unchecked")
        Object[] result = (Object[]) condition.doInvoke(
                Map.of("item", List.of(1, 2, 3), "another_item", List.of("a", "b", "c")),
                sessionWithIndex(0));

        assertEquals(true, result[0]);
        assertEquals(Map.of("item", 1, "another_item", "a"), result[1]);
    }

    private void arrayConditionBeyondLimit() {
        ArrayCondition condition = new ArrayCondition(Map.of("item", "${input.item}"));

        assertEquals(false, condition.doInvoke(Map.of("item", List.of(1, 2, 3)),
                sessionWithIndex(3)));
    }

    private void numberConditionInitialization() {
        NumberCondition condition = new NumberCondition(5);

        assertEquals(5, condition.inputSchema);
    }

    private void numberConditionWithinLimit() {
        NumberCondition condition = new NumberCondition("${input.limit}");

        assertEquals(true, condition.doInvoke(5, sessionWithIndex(2)));
    }

    private void numberConditionBeyondLimit() {
        NumberCondition condition = new NumberCondition("${input.limit}");

        assertEquals(false, condition.doInvoke(5, sessionWithIndex(5)));
    }

    private void expressionConditionInitialization() {
        String expression = "${a} > 5 && ${b} < 10";
        ExpressionCondition condition = new ExpressionCondition(expression);
        @SuppressWarnings("unchecked")
        Map<String, Object> traceInfo = (Map<String, Object>) condition.traceInfo(sessionWithGlobal(Map.of()));

        assertEquals(expression, traceInfo.get("bool_expression"));
    }

    private void expressionConditionTrueResult() {
        assertEquals(true, invoke("${a} > 5 && ${b} < 10", Map.of("a", 6, "b", 8)));
    }

    private void expressionConditionFalseResult() {
        assertEquals(false, invoke("${a} > 5 && ${b} < 10", Map.of("a", 4, "b", 8)));
    }

    private void expressionConditionEmpty() {
        assertEquals(true, new ExpressionCondition("").doInvoke(Map.of(), session()));
    }

    private void expressionConditionTraceInfo() {
        ExpressionCondition condition = new ExpressionCondition("${a} > 5 && ${b} < 10");
        @SuppressWarnings("unchecked")
        Map<String, Object> traceInfo = (Map<String, Object>) condition.traceInfo(sessionWithGlobal(Map.of("a", 6, "b", 8)));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) traceInfo.get("inputs");

        assertEquals("${a} > 5 && ${b} < 10", traceInfo.get("bool_expression"));
        assertTrue(inputs.containsKey("${a}"));
        assertTrue(inputs.containsKey("${b}"));
    }

    private void expressionComparisonOperators() {
        assertEquals(true, invoke("${a} == ${b} && ${c} != ${d}", Map.of("a", 5, "b", 5, "c", 10, "d", 20)));
        assertEquals(true, invoke("${a} in ${list} && ${b} not_in ${list}",
                Map.of("a", 1, "b", 4, "list", List.of(1, 2, 3))));
        assertEquals(true, invoke("${a} is None && ${b} is not None", mapWithNull("a", null, "b", "value")));
    }

    private void disallowedOperations() {
        assertThrows(BaseError.class, () -> invoke("disallowed_var > 5", Map.of()));
        assertThrows(BaseError.class, () -> invoke("${a}.disallowed_attr", Map.of("a", new Object())));
        assertThrows(BaseError.class, () -> invoke("str(${a})", Map.of("a", 5)));
    }

    private void complexNestedExpression() {
        assertEquals(true, invoke("(((${a} > 5 and ${b} < 10) or (${c} == 3 and ${d} != 4)) "
                        + "and (len(${list}) > 0))",
                Map.of("a", 4, "b", 8, "c", 3, "d", 5, "list", List.of(1, 2, 3))));
    }

    private void securityMechanism() {
        assertThrows(BaseError.class, () -> invoke("__import__('os').system('ls')", Map.of()));
        assertThrows(BaseError.class, () -> invoke("import('os').system('ls')", Map.of()));
        assertThrows(BaseError.class, () -> invoke("open('test.txt', 'r')", Map.of()));
        assertThrows(BaseError.class, () -> invoke("eval('2 + 2')", Map.of()));
    }

    private void preventObjectMethodEscape() {
        assertThrows(BaseError.class, () -> invoke("${obj}.__class__", Map.of("obj", new Object())));
        assertThrows(BaseError.class, () -> invoke("${obj}.__class__.__subclasses__()", Map.of("obj", new Object())));
    }

    private void preventSpecialMethodAccess() {
        assertThrows(BaseError.class, () -> invoke("len.__module__", Map.of()));
        assertThrows(BaseError.class, () -> invoke("${obj}.__dict__", Map.of("obj", new Object())));
    }

    private void preventResourceExhaustion() {
        assertThrows(BaseError.class, () -> invoke("[0] * 1000000000", Map.of()));
        assertThrows(BaseError.class, () -> invoke("dict([(i, i) for i in range(1000000000)])", Map.of()));
        assertThrows(BaseError.class, () -> invoke("${large_list}[0:2000]",
                Map.of("large_list", java.util.stream.IntStream.range(0, 100).boxed().toList())));
    }

    private void largeCollectionProtection() {
        assertThrows(BaseError.class, () -> invoke("is_not_empty([0] * (10 ** 10))", Map.of()));
        assertThrows(BaseError.class, () -> invoke("is_not_empty(${large_list})",
                Map.of("large_list", java.util.stream.IntStream.rangeClosed(1, Constant.MAX_COLLECTION_SIZE + 1)
                        .boxed().toList())));
        assertThrows(BaseError.class, () -> invoke("2 ** 1000", Map.of()));
    }

    private void largeListLiteralProtection() {
        String expression = "len([1, 2, 3, " + String.join(", ",
                java.util.stream.IntStream.rangeClosed(4, 100).mapToObj(String::valueOf).toList()) + "]) == 100";
        assertEquals(true, invoke(expression, Map.of()));
        assertThrows(BaseError.class, () -> invoke("${very_large_list} * 2",
                Map.of("very_large_list", java.util.stream.IntStream.rangeClosed(1, Constant.MAX_COLLECTION_SIZE + 1)
                        .boxed().toList())));
    }

    private void expressionLengthLimit() {
        assertEquals(false, new ExpressionCondition("${a} > 0").evaluate(sessionWithGlobal(Map.of("a", 0))));
        String tooLongExpression = "${a} > 0".repeat(Constant.MAX_EXPRESSION_LENGTH / 8 + 1);

        BaseError error = assertThrows(BaseError.class, () -> new ExpressionCondition(tooLongExpression));
        assertTrue(error.getMessage().contains("length exceeds maximum allowed length"));
    }

    private void expressionNestingDepthLimit() {
        assertThrows(BaseError.class, () -> invoke(deeplyNestedExpression(), Map.of("a", 1)));
    }

    private void astNestingDepthLimit() {
        assertEquals(true, invoke("(((${a} > 0) && (${b} < 100)))", Map.of("a", 5, "b", 50)));
        assertThrows(BaseError.class, () -> invoke(deeplyNestedExpression(), Map.of("a", 5)));
    }

    private void stringComparisonWithTrueLiteral() {
        ExpressionCondition condition = new ExpressionCondition("${a} ==\"true\"");

        assertEquals(true, condition.doInvoke(Map.of(), sessionWithGlobal(Map.of("a", "true"))));
        assertEquals(false, condition.doInvoke(Map.of(), sessionWithGlobal(Map.of("a", "false"))));
    }

    private void stringComparisonWithFalseLiteral() {
        ExpressionCondition condition = new ExpressionCondition("${a} ==\"false\"");

        assertEquals(true, condition.doInvoke(Map.of(), sessionWithGlobal(Map.of("a", "false"))));
        assertEquals(false, condition.doInvoke(Map.of(), sessionWithGlobal(Map.of("a", "true"))));
    }

    private void stringLiteralPreservationWithBooleanIdentifier() {
        assertEquals(true, invoke("${a} ==\"true\" && true", Map.of("a", "true")));
        assertEquals(true, invoke("${a} ==\"false\" && true", Map.of("a", "false")));
    }

    private void singleQuoteStringLiteralPreservation() {
        assertEquals(true, invoke("${a} =='true'", Map.of("a", "true")));
        assertEquals(true, invoke("${a} =='false'", Map.of("a", "false")));
        assertEquals(false, invoke("${a} =='true'", Map.of("a", "false")));
        assertEquals(true, invoke("${a} =='true' && true", Map.of("a", "true")));
    }

    private static Object invoke(String expression, Map<String, Object> globals) {
        return new ExpressionCondition(expression).doInvoke(Map.of(), sessionWithGlobal(globals));
    }

    private static String deeplyNestedExpression() {
        String nested = "${a}";
        for (int i = 0; i < Constant.MAX_AST_DEPTH + 5; i++) {
            nested = "(" + nested + " > 0)";
        }
        return nested;
    }

    private static WorkflowRuntimeSession session() {
        return sessionWithGlobal(Map.of());
    }

    private static WorkflowRuntimeSession sessionWithGlobal(Map<String, Object> globals) {
        return new WorkflowRuntimeSession("workflow", null, "session",
                InMemoryState.create(null, globals, null, null, null), null);
    }

    private static WorkflowRuntimeSession sessionWithIndex(Object index) {
        WorkflowRuntimeSession session = session();
        session.state().update(Map.of(Constant.INDEX, index));
        session.state().commit();
        return session;
    }

    private static WorkflowRuntimeSession sessionWithIo(Map<String, Object> ioState) {
        return new WorkflowRuntimeSession("workflow", null, "session",
                InMemoryState.create(ioState, null, null, null, null), null);
    }

    private static Map<String, Object> mapWithNull(String firstKey, Object firstValue,
                                                   String secondKey, Object secondValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }

    private static final class RecordingCondition extends Condition {
        private final Object result;
        private Object lastInputs;

        private RecordingCondition(Object result, Object inputSchema) {
            super(inputSchema);
            this.result = result;
        }

        @Override
        public Object doInvoke(Object inputs, com.openjiuwen.core.session.BaseSession session) {
            assertInstanceOf(Map.class, inputs);
            this.lastInputs = inputs;
            return result;
        }
    }
}
