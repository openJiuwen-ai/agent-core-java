/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalFunction.
 * Ported from Python: tests/unit_tests/core/foundation/tool/test_tool_decorator.py
 */
class LocalFunctionTest {

    // ============================== Construction tests ==============================

    @Nested
    @DisplayName("LocalFunction construction")
    class ConstructionTests {

        @Test
        @DisplayName("Create LocalFunction with valid card and function")
        void testValidConstruction() {
            ToolCard card = ToolCard.builder()
                    .name("add")
                    .description("Add two numbers")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "a", Map.of("type", "integer", "description", "first arg"),
                                    "b", Map.of("type", "integer", "description", "second arg")
                            ),
                            "required", new String[]{"a", "b"}
                    ))
                    .build();

            LocalFunction tool = new LocalFunction(card, inputs -> {
                int a = ((Number) inputs.get("a")).intValue();
                int b = ((Number) inputs.get("b")).intValue();
                return a + b;
            });

            assertNotNull(tool);
            assertEquals("add", tool.getCard().getName());
            assertEquals("Add two numbers", tool.getCard().getDescription());
        }

        @Test
        @DisplayName("Constructor with null func throws exception")
        void testNullFuncThrows() {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test")
                    .build();

            assertThrows(Throwable.class, () -> new LocalFunction(card, null));
        }

        @Test
        @DisplayName("Constructor with null card throws exception")
        void testNullCardThrows() {
            assertThrows(Throwable.class, () -> new LocalFunction(null, inputs -> "result"));
        }

        @Test
        @DisplayName("getFunc returns wrapped function")
        void testGetFunc() {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test")
                    .build();

            java.util.function.Function<Map<String, Object>, Object> func = inputs -> "hello";
            LocalFunction tool = new LocalFunction(card, func);
            assertSame(func, tool.getFunc());
        }
    }

    // ============================== Invoke tests ==============================

    @Nested
    @DisplayName("Invoke tests")
    class InvokeTests {

        @Test
        @DisplayName("Invoke subtraction function returns correct result")
        void testInvokeSubtraction() throws Exception {
            ToolCard card = ToolCard.builder()
                    .name("local_sub")
                    .description("local function for sub")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "a", Map.of("description", "first arg", "type", "integer"),
                                    "b", Map.of("description", "second arg", "type", "integer")
                            ),
                            "required", new String[]{"a", "b"}
                    ))
                    .build();

            LocalFunction sub = new LocalFunction(card, inputs -> {
                int a = ((Number) inputs.get("a")).intValue();
                int b = ((Number) inputs.get("b")).intValue();
                return a - b;
            });

            Object result = sub.invoke(Map.of("a", 5, "b", 1));

            assertEquals("local_sub", sub.getCard().getName());
            assertEquals("local function for sub", sub.getCard().getDescription());
            assertEquals(4, result);
        }

        @Test
        @DisplayName("Invoke addition function returns correct result")
        void testInvokeAddition() throws Exception {
            ToolCard card = ToolCard.builder()
                    .name("add")
                    .description("Add two numbers")
                    .build();

            LocalFunction add = new LocalFunction(card, inputs -> {
                int a = ((Number) inputs.get("a")).intValue();
                int b = ((Number) inputs.get("b")).intValue();
                return a + b;
            });

            Object result = add.invoke(Map.of("a", 3, "b", 7));
            assertEquals(10, result);
        }

        @Test
        @DisplayName("Invoke with complex input returns correct result")
        void testInvokeComplexInput() throws Exception {
            ToolCard card = ToolCard.builder()
                    .name("summarize")
                    .description("汇总商品信息")
                    .build();

            LocalFunction summarize = new LocalFunction(card, inputs -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> products = (List<Map<String, Object>>) inputs.get("products");
                double total = 0;
                for (Map<String, Object> p : products) {
                    total += ((Number) p.get("price")).doubleValue() * ((Number) p.get("sales")).intValue();
                }
                return total;
            });

            Map<String, Object> input = Map.of(
                    "title", "水果信息汇总",
                    "products", List.of(
                            Map.of("name", "苹果", "sales", 2, "price", 1.5),
                            Map.of("name", "香蕉", "sales", 4, "price", 1.0)
                    )
            );

            Object result = summarize.invoke(input);
            assertEquals(7.0, result);
        }

        @Test
        @DisplayName("toolInfo returns correct ToolInfo from card")
        void testToolInfo() {
            Map<String, Object> inputParams = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "a", Map.of("description", "first arg", "type", "integer"),
                            "b", Map.of("description", "second arg", "type", "integer")
                    ),
                    "required", new String[]{"a", "b"}
            );

            ToolCard card = ToolCard.builder()
                    .name("local_sub")
                    .description("local function for sub")
                    .inputParams(inputParams)
                    .build();

            LocalFunction sub = new LocalFunction(card, inputs -> 0);

            ToolInfo toolInfo = sub.getCard().toolInfo();
            assertEquals("local_sub", toolInfo.getName());
            assertEquals("local function for sub", toolInfo.getDescription());
            assertEquals(inputParams, toolInfo.getParameters());
        }
    }

    // ============================== Stream tests ==============================

    @Nested
    @DisplayName("Stream tests")
    class StreamTests {

        @Test
        @DisplayName("Stream rejects non-generator functions")
        void testStreamSingleResult() {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test")
                    .build();

            LocalFunction tool = new LocalFunction(card, inputs -> "single result");
            assertThrows(Throwable.class, () -> tool.stream(Map.of()));
        }

        @Test
        @DisplayName("Stream returns Iterator directly if function returns Iterator")
        void testStreamReturnsIteratorDirectly() throws Exception {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test")
                    .build();

            List<Object> items = List.of("a", "b", "c");
            LocalFunction tool = new LocalFunction(card, inputs -> items.iterator());

            Iterator<Object> iter = tool.stream(Map.of());
            List<Object> collected = new ArrayList<>();
            while (iter.hasNext()) {
                collected.add(iter.next());
            }
            assertEquals(items, collected);
        }

        @Test
        @DisplayName("Stream converts Iterable to iterator")
        void testStreamConvertsIterable() throws Exception {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test")
                    .build();

            List<Object> items = List.of(1, 2, 3);
            LocalFunction tool = new LocalFunction(card, inputs -> items);

            Iterator<Object> iter = tool.stream(Map.of());
            List<Object> collected = new ArrayList<>();
            while (iter.hasNext()) {
                collected.add(iter.next());
            }
            assertEquals(items, collected);
        }
    }
}
