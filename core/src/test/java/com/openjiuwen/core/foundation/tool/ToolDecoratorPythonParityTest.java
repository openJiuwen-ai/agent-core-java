/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestToolDecorator} in
 * {@code tests/unit_tests/core/foundation/tool/test_tool_decorator.py}.</p>
 */
class ToolDecoratorPythonParityTest {

    @Test
    void testToolWithVarPositional() throws Exception {
        LocalFunction onlyVarargs = ToolDecorator.tool("tool_with_only_with_var_positional",
                inputs -> sum(numbers(inputs.get("args"))));
        LocalFunction leadingArgs = ToolDecorator.tool("tool_with_var_positional",
                inputs -> number(inputs.get("a")) + number(inputs.get("b")) + sum(numbers(inputs.get("args"))));
        LocalFunction middleArgs = ToolDecorator.tool("tool_with_middle_with_var_positional",
                inputs -> number(inputs.get("a")) + number(inputs.get("b"))
                        + sum(numbers(inputs.get("args"))) + number(inputs.get("d")));

        assertEquals(6, onlyVarargs.invoke(Map.of("args", List.of(1, 2, 3))));
        assertEquals(9, leadingArgs.invoke(Map.of("a", 1, "b", 2, "args", List.of(1, 2, 3))));
        assertEquals(13, middleArgs.invoke(Map.of("a", 1, "b", 2, "args", List.of(1, 2, 3), "d", 4)));
    }

    @Test
    void testToolWithVarKeywords() throws Exception {
        LocalFunction keywordTool = ToolDecorator.tool("tool_with_var_keywords", inputs -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("a", inputs.get("a"));
            result.put("b", inputs.get("b"));
            inputs.forEach((key, value) -> {
                if (!"a".equals(key) && !"b".equals(key)) {
                    result.put(key, value);
                }
            });
            return result;
        });
        LocalFunction onlyKeywords = ToolDecorator.tool("tool_with_only_with_var_keywords",
                LinkedHashMap::new);

        assertEquals(Map.of("a", 1, "b", 2, "c", 3),
                keywordTool.invoke(Map.of("a", 1, "b", 2, "c", 3)));
        assertEquals(Map.of("a", 1, "b", 2, "c", 3),
                onlyKeywords.invoke(Map.of("a", 1, "b", 2, "c", 3)));
    }

    @Test
    void testToolWithMixVar() throws Exception {
        LocalFunction mixVar = ToolDecorator.tool("tool_with_mix_var", inputs -> {
            int result = number(inputs.get("a")) + number(inputs.get("b"));
            result += sum(numbers(inputs.get("args")));
            result += inputs.entrySet().stream()
                    .filter(entry -> !"a".equals(entry.getKey()))
                    .filter(entry -> !"b".equals(entry.getKey()))
                    .filter(entry -> !"args".equals(entry.getKey()))
                    .mapToInt(entry -> number(entry.getValue()))
                    .sum();
            return result;
        });

        assertEquals(16, mixVar.invoke(Map.of("a", 1, "args", List.of(1, 2, 3), "b", 2, "c", 3, "d", 4)));
    }

    @Test
    void testTool() throws Exception {
        LocalFunction sub = ToolDecorator.tool(inputs -> number(inputs.get("a")) - number(inputs.get("b")),
                ToolDecorator.Options.builder()
                        .card(ToolCard.builder()
                                .id("local_sub")
                                .name("local_sub")
                                .description("local function for sub")
                                .inputParams(subSchema())
                                .build())
                        .build());

        Object subResult = sub.invoke(Map.of("a", 5, "b", 1));
        assertEquals("local_sub", sub.getCard().getName());
        assertEquals("local function for sub", sub.getCard().getDescription());
        assertEquals(4, subResult);

        ToolInfo expected = ToolInfo.builder()
                .name("local_sub")
                .description("local function for sub")
                .parameters(subSchema())
                .build();
        assertEquals(expected, sub.getCard().toolInfo());
    }

    @Test
    void testAnnotated() throws Exception {
        LocalFunction summarize = summarizeTool();
        Map<String, Object> input = Map.of(
                "title", "fruit summary",
                "products", List.of(
                        Map.of(
                                "name", "apple",
                                "sales", 2,
                                "price", 1.5,
                                "is_season", true,
                                "color", List.of("red", "yellow"),
                                "note", Map.of("key", "remark", "value", 10)
                        ),
                        Map.of(
                                "name", "banana",
                                "sales", 4,
                                "price", 1,
                                "is_season", false,
                                "color", List.of("yellow"),
                                "note", Map.of("key", "remark", "value", 20)
                        )
                )
        );

        Object summarizeResult = summarize.invoke(input);

        assertEquals("summarize", summarize.getCard().getName());
        assertEquals("summarize product info", summarize.getCard().getDescription());
        assertEquals(7.0, ((Number) summarizeResult).doubleValue(), 0.0001);

        Map<String, Object> parameters = summarize.getCard().toolInfo().getParameters();
        assertEquals("object", parameters.get("type"));
        Map<String, Object> properties = castMap(parameters.get("properties"));
        assertTrue(properties.containsKey("title"));
        assertTrue(properties.containsKey("products"));
        Map<String, Object> productItems = castMap(castMap(properties.get("products")).get("items"));
        Map<String, Object> productProperties = castMap(productItems.get("properties"));
        assertTrue(productProperties.containsKey("note"));
        assertEquals(List.of("name", "is_season", "color", "note"), productItems.get("required"));
    }

    @Test
    void testLiteralModeParam() {
        LocalFunction readWriteTool = readWriteTool();
        Map<String, Object> parameters = readWriteTool.getCard().toolInfo().getParameters();
        Map<String, Object> properties = castMap(parameters.get("properties"));

        Map<String, Object> modeProperty = castMap(properties.get("mode"));
        assertEquals("string", modeProperty.get("type"));
        assertEquals(List.of("text", "bytes"), modeProperty.get("enum"));
    }

    @Test
    void testOptionalParamsWithoutDefaults() throws Exception {
        LocalFunction readWriteTool = readWriteTool();

        Object result = readWriteTool.invoke(Map.of("path", "test.txt", "content", "test content"));
        assertEquals("read_write_tool", readWriteTool.getCard().getName());
        assertTrue(String.valueOf(result).contains("Verified: path=test.txt"));

        Map<String, Object> parameters = readWriteTool.getCard().toolInfo().getParameters();
        List<String> required = castList(parameters.get("required"));
        assertTrue(required.contains("path"));
        assertTrue(required.contains("content"));
        assertFalse(required.contains("mode"));
        assertFalse(required.contains("head"));
        assertFalse(required.contains("tail"));
        assertFalse(required.contains("line_range"));
    }

    @Test
    void testUnionStrBytesParam() throws Exception {
        LocalFunction readWriteTool = readWriteTool();

        Object textResult = readWriteTool.invoke(Map.of("path", "test.txt", "content", "test content"));
        assertTrue(String.valueOf(textResult).contains("Verified: path=test.txt"));

        Object binaryResult = readWriteTool.invoke(Map.of(
                "path", "test.bin",
                "mode", "bytes",
                "content", new byte[] {0, 1, 2, 3}
        ));
        assertTrue(String.valueOf(binaryResult).contains("Verified: path=test.bin"));
        assertTrue(String.valueOf(binaryResult).contains("mode=bytes"));

        Map<String, Object> properties = castMap(readWriteTool.getCard().toolInfo().getParameters().get("properties"));
        Map<String, Object> contentProperty = castMap(properties.get("content"));
        List<Object> anyOf = castObjectList(contentProperty.get("anyOf"));
        assertEquals(2, anyOf.size());
        assertEquals(Map.of("type", "string"), anyOf.get(0));
        assertEquals(Map.of("type", "string", "format", "binary"), anyOf.get(1));
    }

    @Test
    void testTupleLineRangeParam() throws Exception {
        LocalFunction readWriteTool = readWriteTool();

        Object result = readWriteTool.invoke(Map.of(
                "path", "test.txt",
                "line_range", List.of(1, 10),
                "content", "test content"
        ));

        assertTrue(String.valueOf(result).contains("Verified: path=test.txt"));
        assertTrue(String.valueOf(result).contains("line_range=[1, 10]"));

        Map<String, Object> properties = castMap(readWriteTool.getCard().toolInfo().getParameters().get("properties"));
        Map<String, Object> lineRangeProperty = castMap(properties.get("line_range"));
        assertEquals("array", lineRangeProperty.get("type"));
        Map<String, Object> items = castMap(lineRangeProperty.get("items"));
        List<Object> itemsAnyOf = castObjectList(items.get("anyOf"));
        assertEquals(2, itemsAnyOf.size());
        assertEquals(Map.of("type", "integer"), itemsAnyOf.get(0));
        assertEquals(Map.of("type", "integer"), itemsAnyOf.get(1));
    }

    private static LocalFunction summarizeTool() {
        return ToolDecorator.tool(inputs -> {
            double total = 0.0;
            for (Object item : castObjectList(inputs.get("products"))) {
                Map<String, Object> product = castMap(item);
                total += ((Number) product.get("price")).doubleValue() * ((Number) product.get("sales")).doubleValue();
            }
            return total;
        }, ToolDecorator.Options.builder()
                .name("summarize")
                .description("summarize product info")
                .inputParams(summarizeSchema())
                .build());
    }

    private static LocalFunction readWriteTool() {
        return ToolDecorator.tool(inputs -> "Verified: path=" + inputs.get("path")
                + ", mode=" + inputs.get("mode")
                + ", head=" + inputs.get("head")
                + ", tail=" + inputs.get("tail")
                + ", line_range=" + inputs.get("line_range"),
                ToolDecorator.Options.builder()
                        .name("read_write_tool")
                        .description("Test function to verify tool parameter fixes")
                        .inputParams(readWriteSchema())
                        .build());
    }

    private static Map<String, Object> subSchema() {
        return linkedMap(
                "type", "object",
                "properties", linkedMap(
                        "a", linkedMap("description", "first arg", "type", "integer"),
                        "b", linkedMap("description", "second arg", "type", "integer")
                ),
                "required", List.of("a", "b")
        );
    }

    private static Map<String, Object> summarizeSchema() {
        return linkedMap(
                "type", "object",
                "properties", linkedMap(
                        "title", linkedMap("type", "string", "description", "title"),
                        "products", linkedMap(
                                "type", "array",
                                "items", linkedMap(
                                        "type", "object",
                                        "properties", linkedMap(
                                                "name", linkedMap("type", "string", "description", "name"),
                                                "sales", linkedMap("type", "integer", "description", "sales", "default", 0),
                                                "price", linkedMap("type", "number", "description", "price", "default", 1.0),
                                                "is_season", linkedMap("type", "boolean", "description", "is season"),
                                                "color", linkedMap("type", "array", "items", linkedMap("type", "string"),
                                                        "description", "color"),
                                                "note", linkedMap(
                                                        "type", "object",
                                                        "properties", linkedMap(
                                                                "key", linkedMap("type", "string", "description", "key"),
                                                                "value", linkedMap("type", "integer", "description", "value")
                                                        ),
                                                        "required", List.of("key", "value"),
                                                        "description", "note"
                                                )
                                        ),
                                        "required", List.of("name", "is_season", "color", "note")
                                ),
                                "description", "products"
                        )
                ),
                "additionalProperties", false,
                "title", "summarize",
                "required", List.of("title", "products")
        );
    }

    private static Map<String, Object> readWriteSchema() {
        return linkedMap(
                "type", "object",
                "properties", linkedMap(
                        "path", linkedMap("type", "string"),
                        "mode", linkedMap("type", "string", "enum", List.of("text", "bytes"), "default", "text"),
                        "head", linkedMap("type", "integer"),
                        "tail", linkedMap("type", "integer"),
                        "line_range", linkedMap("type", "array", "items", linkedMap(
                                "anyOf", List.of(
                                        Map.of("type", "integer"),
                                        Map.of("type", "integer")
                                )
                        )),
                        "content", linkedMap("anyOf", List.of(
                                Map.of("type", "string"),
                                Map.of("type", "string", "format", "binary")
                        ))
                ),
                "required", List.of("path", "content")
        );
    }

    private static int sum(List<Number> values) {
        return values.stream().mapToInt(Number::intValue).sum();
    }

    @SuppressWarnings("unchecked")
    private static List<Number> numbers(Object value) {
        return (List<Number>) value;
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castObjectList(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return values;
    }
}
