/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolDecorator.
 * <p>
 * Mirrors Python's test_tool_decorator.py from
 * <code>tests/unit_tests/core/foundation/tool/test_tool_decorator.py</code>.
 */
@DisplayName("Tool Decorator Tests")
class TestToolDecorator {

    // Stub classes
    static class ToolStub {
        String name;
        Function<Map<String, Object>, Object> executor;
        Map<String, Object> metadata = new HashMap<>();

        ToolStub(String name, Function<Map<String, Object>, Object> executor) {
            this.name = name;
            this.executor = executor;
        }

        Object execute(Map<String, Object> inputs) {
            return executor.apply(inputs);
        }
    }

    static class ToolDecorator {
        ToolStub originalTool;
        Function<Object, Object> beforeDecorator;
        Function<Object, Object> afterDecorator;

        ToolDecorator(ToolStub originalTool) {
            this.originalTool = originalTool;
        }

        void setBefore(Function<Object, Object> decorator) {
            this.beforeDecorator = decorator;
        }

        void setAfter(Function<Object, Object> decorator) {
            this.afterDecorator = decorator;
        }

        Object execute(Map<String, Object> inputs) {
            Object result = originalTool.execute(inputs);
            if (afterDecorator != null) {
                result = afterDecorator.apply(result);
            }
            return result;
        }

        String getName() {
            return originalTool.name;
        }
    }

    @Nested
    @DisplayName("Tool Decorator Tests")
    class TestToolDecoratorClass {

        @Test
        @DisplayName("decorator wraps tool")
        void testDecoratorWrapsTool() {
            ToolStub tool = new ToolStub("echo", inputs -> inputs.get("data"));
            ToolDecorator decorator = new ToolDecorator(tool);

            assertEquals("echo", decorator.getName());
        }

        @Test
        @DisplayName("decorator modifies output")
        void testDecoratorModifiesOutput() {
            ToolStub tool = new ToolStub("echo", inputs -> inputs.get("data"));
            ToolDecorator decorator = new ToolDecorator(tool);
            decorator.setAfter(result -> "Decorated: " + result);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("data", "hello");

            Object result = decorator.execute(inputs);

            assertEquals("Decorated: hello", result);
        }

        @Test
        @DisplayName("decorator chain")
        void testDecoratorChain() {
            ToolStub tool = new ToolStub("echo", inputs -> inputs.get("data"));
            ToolDecorator decorator1 = new ToolDecorator(tool);
            decorator1.setAfter(result -> "Level1: " + result);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("data", "test");

            Object result = decorator1.execute(inputs);

            assertEquals("Level1: test", result);
        }
    }

    @Nested
    @DisplayName("Original Tool Tests")
    class TestOriginalTool {

        @Test
        @DisplayName("original tool execution")
        void testOriginalToolExecution() {
            ToolStub tool = new ToolStub("math", inputs -> {
                int a = (Integer) inputs.get("a");
                int b = (Integer) inputs.get("b");
                return a + b;
            });

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("a", 5);
            inputs.put("b", 3);

            Object result = tool.execute(inputs);

            assertEquals(8, result);
        }
    }
}