/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Java's LocalFunction/ToolCard equivalent of Python's @tool decorator.
 * <p>
 * Mirrors Python's {@code test_tool_decorator.py}.
 */
@DisplayName("Tool Decorator Tests")
class TestToolDecorator {

    @Test
    void testToolWithVarPositional() throws Exception {
        LocalFunction onlyArgs = tool("only_args", inputs ->
                ((List<?>) inputs.get("args")).stream().mapToInt(value -> ((Number) value).intValue()).sum());
        assertEquals(6, onlyArgs.invoke(Map.of("args", List.of(1, 2, 3))));

        LocalFunction withArgs = tool("with_args", inputs -> intValue(inputs, "a") + intValue(inputs, "b")
                + ((List<?>) inputs.get("args")).stream().mapToInt(value -> ((Number) value).intValue()).sum());
        assertEquals(9, withArgs.invoke(Map.of("a", 1, "b", 2, "args", List.of(1, 2, 3))));

        LocalFunction middleArgs = tool("middle_args", inputs -> intValue(inputs, "a") + intValue(inputs, "b")
                + intValue(inputs, "d")
                + ((List<?>) inputs.get("args")).stream().mapToInt(value -> ((Number) value).intValue()).sum());
        assertEquals(13, middleArgs.invoke(Map.of("a", 1, "b", 2, "args", List.of(1, 2, 3), "d", 4)));
    }

    @Test
    void testToolWithVarKeywords() throws Exception {
        LocalFunction kwargsTool = tool("kwargs", inputs -> new LinkedHashMap<>(inputs));

        assertEquals(Map.of("a", 1, "b", 2, "c", 3),
                kwargsTool.invoke(Map.of("a", 1, "b", 2, "c", 3)));
        assertEquals(Map.of("a", 1, "b", 2, "c", 3),
                kwargsTool.invoke(Map.of("a", 1, "b", 2, "c", 3)));
    }

    @Test
    void testToolWithMixVar() throws Exception {
        LocalFunction mixed = tool("mixed", inputs -> {
            int result = intValue(inputs, "a") + intValue(inputs, "b");
            result += ((List<?>) inputs.get("args")).stream().mapToInt(value -> ((Number) value).intValue()).sum();
            result += intValue(inputs, "c") + intValue(inputs, "d");
            return result;
        });

        assertEquals(16, mixed.invoke(Map.of("a", 1, "args", List.of(1, 2, 3), "b", 2, "c", 3, "d", 4)));
    }

    @Test
    void testTool() throws Exception {
        LocalFunction sub = new LocalFunction(subCard(), inputs -> intValue(inputs, "a") - intValue(inputs, "b"));

        assertEquals(4, sub.invoke(Map.of("a", 5, "b", 1)));
        assertEquals("local_sub", sub.getCard().getName());
        assertEquals("local function for sub", sub.getCard().getDescription());
        assertEquals(new ToolInfo("function", "local_sub", "local function for sub", subCard().getInputParams()),
                sub.getCard().toolInfo());
    }

    @Test
    void testAnnotated() throws Exception {
        LocalFunction summarize = new LocalFunction(summarizeCard(), inputs -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) inputs.get("products");
            return products.stream()
                    .mapToDouble(product -> ((Number) product.get("price")).doubleValue()
                            * ((Number) product.get("sales")).intValue())
                    .sum();
        });

        double total = (double) summarize.invoke(Map.of("title", "fruit",
                "products", List.of(
                        product("apple", 2, 1.5),
                        product("banana", 4, 1.0))));

        assertEquals("summarize", summarize.getCard().getName());
        assertEquals(7.0, total);
        assertEquals("object", summarize.getCard().toolInfo().getParameters().get("type"));
    }

    @Test
    void testLiteralModeParam() {
        Map<String, Object> properties = properties(readWriteTool());
        @SuppressWarnings("unchecked")
        Map<String, Object> mode = (Map<String, Object>) properties.get("mode");

        assertEquals("string", mode.get("type"));
        assertEquals(List.of("text", "bytes"), mode.get("enum"));
    }

    @Test
    void testOptionalParamsWithoutDefaults() throws Exception {
        LocalFunction tool = readWriteTool();
        String result = (String) tool.invoke(Map.of("path", "test.txt", "content", "test content"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) tool.getCard().toolInfo().getParameters().get("required");

        assertTrue(result.contains("Verified: path=test.txt"));
        assertTrue(required.contains("path"));
        assertTrue(required.contains("content"));
        assertFalse(required.contains("mode"));
        assertFalse(required.contains("head"));
        assertFalse(required.contains("tail"));
        assertFalse(required.contains("line_range"));
    }

    @Test
    void testUnionStrBytesParam() throws Exception {
        LocalFunction tool = readWriteTool();
        String textResult = (String) tool.invoke(Map.of("path", "test.txt", "content", "test content"));
        String bytesResult = (String) tool.invoke(Map.of("path", "test.bin", "mode", "bytes", "content", new byte[]{0, 1, 2, 3}));
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) properties(tool).get("content");

        assertTrue(textResult.contains("Verified: path=test.txt"));
        assertTrue(bytesResult.contains("mode=bytes"));
        assertEquals(List.of(Map.of("type", "string"), Map.of("type", "string", "format", "binary")),
                content.get("anyOf"));
    }

    @Test
    void testTupleLineRangeParam() throws Exception {
        LocalFunction tool = readWriteTool();
        String result = (String) tool.invoke(Map.of("path", "test.txt", "line_range", List.of(1, 10), "content", "test content"));
        @SuppressWarnings("unchecked")
        Map<String, Object> lineRange = (Map<String, Object>) properties(tool).get("line_range");
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) lineRange.get("items");

        assertTrue(result.contains("line_range=[1, 10]"));
        assertEquals("array", lineRange.get("type"));
        assertEquals(List.of(Map.of("type", "integer"), Map.of("type", "integer")), items.get("anyOf"));
    }

    private static LocalFunction tool(String name, java.util.function.Function<Map<String, Object>, Object> function) {
        return new LocalFunction(ToolCard.builder().id(name).name(name).description(name).build(), function);
    }

    private static ToolCard subCard() {
        return ToolCard.builder()
                .id("local_sub")
                .name("local_sub")
                .description("local function for sub")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("description", "first arg", "type", "integer"),
                                "b", Map.of("description", "second arg", "type", "integer")),
                        "required", List.of("a", "b")))
                .build();
    }

    private static ToolCard summarizeCard() {
        return ToolCard.builder()
                .id("summarize")
                .name("summarize")
                .description("summarize product information")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "title"),
                                "products", Map.of("type", "array", "description", "products")),
                        "required", List.of("title", "products")))
                .build();
    }

    private static LocalFunction readWriteTool() {
        ToolCard card = ToolCard.builder()
                .id("read_write_tool")
                .name("read_write_tool")
                .description("Test function to verify tool parameter fixes")
                .inputParams(readWriteSchema())
                .build();
        return new LocalFunction(card, inputs -> "Verified: path=" + inputs.get("path")
                + ", mode=" + inputs.getOrDefault("mode", "text")
                + ", head=" + inputs.get("head")
                + ", tail=" + inputs.get("tail")
                + ", line_range=" + inputs.get("line_range"));
    }

    private static Map<String, Object> readWriteSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string"));
        properties.put("mode", Map.of("type", "string", "enum", List.of("text", "bytes")));
        properties.put("head", Map.of("type", "integer", "nullable", true));
        properties.put("tail", Map.of("type", "integer", "nullable", true));
        properties.put("line_range", Map.of("type", "array",
                "items", Map.of("anyOf", List.of(Map.of("type", "integer"), Map.of("type", "integer")))));
        properties.put("content", Map.of("anyOf", List.of(
                Map.of("type", "string"),
                Map.of("type", "string", "format", "binary"))));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("path", "content"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(LocalFunction tool) {
        return (Map<String, Object>) tool.getCard().toolInfo().getParameters().get("properties");
    }

    private static Map<String, Object> product(String name, int sales, double price) {
        return Map.of("name", name, "sales", sales, "price", price, "is_season", true,
                "color", List.of("red"), "note", Map.of("key", "note", "value", 1));
    }

    private static int intValue(Map<String, Object> inputs, String key) {
        return ((Number) inputs.get(key)).intValue();
    }
}
