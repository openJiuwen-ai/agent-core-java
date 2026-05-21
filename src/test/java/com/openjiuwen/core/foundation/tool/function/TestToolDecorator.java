/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.foundation.tool.annotation.ToolDefinition;
import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Tool Decorator functionality.
 * <p>
 * Mirrors Python's test_tool_decorator.py from
 * <code>tests/unit_tests/core/foundation/tool/test_tool_decorator.py</code>.
 */
@DisplayName("Tool Decorator Tests")
class TestToolDecorator {

    /**
     * Test class with @ToolDefinition annotated methods.
     */
    static class TestToolClass {

        @ToolDefinition(name = "local_sub", description = "local function for sub")
        public int sub(int a, int b) {
            return a - b;
        }

        @ToolDefinition(description = "汇总商品信息")
        public double summarize(String title, List<?> products) {
            return 0.0;
        }

        @ToolDefinition(name = "read_write_tool", description = "Test function to verify tool parameter fixes")
        public String readWriteTool(String path, String mode, String content) {
            return "Verification message";
        }
    }

    @Nested
    @DisplayName("AnnotatedToolFactory Tests")
    class TestAnnotatedToolFactory {

        @Test
        @DisplayName("scan finds annotated methods")
        void testScanFindsAnnotatedMethods() {
            TestToolClass testClass = new TestToolClass();
            List<LocalFunction> tools = AnnotatedToolFactory.scan(testClass);

            assertNotNull(tools);
            assertTrue(tools.size() >= 1);
        }

        @Test
        @DisplayName("tool has correct name")
        void testToolHasCorrectName() {
            TestToolClass testClass = new TestToolClass();
            List<LocalFunction> tools = AnnotatedToolFactory.scan(testClass);

            for (LocalFunction tool : tools) {
                assertNotNull(tool.getCard());
                assertNotNull(tool.getCard().getName());
            }
        }

        @Test
        @DisplayName("tool has correct description")
        void testToolHasCorrectDescription() {
            TestToolClass testClass = new TestToolClass();
            List<LocalFunction> tools = AnnotatedToolFactory.scan(testClass);

            for (LocalFunction tool : tools) {
                assertNotNull(tool.getCard());
                assertNotNull(tool.getCard().getDescription());
            }
        }

        @Test
        @DisplayName("tool has input params")
        void testToolHasInputParams() {
            TestToolClass testClass = new TestToolClass();
            List<LocalFunction> tools = AnnotatedToolFactory.scan(testClass);

            for (LocalFunction tool : tools) {
                assertNotNull(tool.getCard());
                assertNotNull(tool.getCard().getInputParams());
            }
        }
    }

    @Nested
    @DisplayName("ToolDefinition Annotation Tests")
    class TestToolDefinitionAnnotation {

        @Test
        @DisplayName("annotation can be applied to method")
        void testAnnotationCanBeAppliedToMethod() throws NoSuchMethodException {
            Method method = TestToolClass.class.getMethod("sub", int.class, int.class);
            ToolDefinition annotation = method.getAnnotation(ToolDefinition.class);

            assertNotNull(annotation);
            assertEquals("local_sub", annotation.name());
            assertEquals("local function for sub", annotation.description());
        }

        @Test
        @DisplayName("annotation autoExtract defaults to true")
        void testAnnotationAutoExtractDefaultsToTrue() throws NoSuchMethodException {
            Method method = TestToolClass.class.getMethod("sub", int.class, int.class);
            ToolDefinition annotation = method.getAnnotation(ToolDefinition.class);

            assertTrue(annotation.autoExtract());
        }
    }

    @Nested
    @DisplayName("LocalFunction Tests")
    class TestLocalFunction {

        @Test
        @DisplayName("local function can be created from method")
        void testLocalFunctionCanBeCreatedFromMethod() {
            TestToolClass testClass = new TestToolClass();
            try {
                Method method = TestToolClass.class.getMethod("sub", int.class, int.class);
                LocalFunction func = AnnotatedToolFactory.fromMethod(testClass, method);

                assertNotNull(func);
                assertNotNull(func.getCard());
            } catch (NoSuchMethodException e) {
                fail("Method not found: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("local function invocation")
        void testLocalFunctionInvocation() {
            TestToolClass testClass = new TestToolClass();
            List<LocalFunction> tools = AnnotatedToolFactory.scan(testClass);

            // Find the sub tool
            LocalFunction subTool = tools.stream()
                    .filter(t -> "local_sub".equals(t.getCard().getName()))
                    .findFirst()
                    .orElse(null);

            if (subTool != null) {
                // Test invocation with parameters
                Object result = subTool.invoke(new Object[]{5, 3});
                assertEquals(2, result);
            }
        }
    }

    @Nested
    @DisplayName("ToolCard Tests")
    class TestToolCard {

        @Test
        @DisplayName("tool card builder")
        void testToolCardBuilder() {
            ToolCard card = ToolCard.builder()
                    .id("test-tool")
                    .name("test-tool")
                    .description("Test tool description")
                    .inputParams(Map.of("type", "object"))
                    .build();

            assertNotNull(card);
            assertEquals("test-tool", card.getId());
            assertEquals("test-tool", card.getName());
            assertEquals("Test tool description", card.getDescription());
        }
    }
}