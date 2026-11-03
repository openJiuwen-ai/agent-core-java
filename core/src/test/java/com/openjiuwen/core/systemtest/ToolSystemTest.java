/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for the Tool framework (LocalFunction).
 * Corresponds to the tool-building patterns in Python's react_agent examples.
 */
@Tag("system-test")
class ToolSystemTest {
    @Test
    @DisplayName("LocalFunction tool invocation with simple inputs")
    void testLocalFunctionInvoke() throws Exception {
        ToolCard card = ToolCard.builder().id("add_tool").name("add").description("Adds two numbers together")
                .inputParams(Map.of("type", "object", "properties",
                        Map.of("a", Map.of("type", "number", "description", "First number"), "b",
                                Map.of("type", "number", "description", "Second number")),
                        "required", List.of("a", "b")))
                .build();

        LocalFunction tool = new LocalFunction(card, inputs -> {
            Number a = (Number) inputs.get("a");
            Number b = (Number) inputs.get("b");
            return a.doubleValue() + b.doubleValue();
        });

        Object result = tool.invoke(Map.of("a", 3, "b", 5));
        assertNotNull(result);
        assertEquals(8.0, result);
        System.out.println("[Tool Add] Result: " + result);
    }

    @Test
    @DisplayName("LocalFunction tool returning map result")
    void testLocalFunctionMapResult() throws Exception {
        ToolCard card =
            ToolCard.builder().id("weather_tool").name("get_weather").description("Gets the weather for a city")
                    .inputParams(Map.of("type", "object", "properties",
                            Map.of("city", Map.of("type", "string", "description", "City name")), "required",
                            List.of("city")))
                    .build();

        LocalFunction tool = new LocalFunction(card, inputs -> {
            String city = (String) inputs.get("city");
            return Map.of("city", city, "temperature", 22, "condition", "sunny");
        });

        Object result = tool.invoke(Map.of("city", "北京"));
        assertNotNull(result);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("北京", resultMap.get("city"));
        assertEquals(22, resultMap.get("temperature"));
        System.out.println("[Tool Weather] Result: " + resultMap);
    }

    @Test
    @DisplayName("ToolCard generates valid ToolInfo")
    void testToolCardInfo() {
        ToolCard card = ToolCard.builder().id("test_tool").name("test").description("A test tool")
                .inputParams(Map.of("type", "object", "properties", Map.of("input", Map.of("type", "string")),
                        "required", List.of("input")))
                .build();

        ToolInfo info = card.toolInfo();
        assertNotNull(info, "ToolInfo should not be null");
        System.out.println("[ToolCard Info] " + info);
    }

    @Test
    @DisplayName("LocalFunction tool with streaming result")
    void testLocalFunctionStream() throws Exception {
        ToolCard card = ToolCard.builder().id("list_tool").name("list_items").description("Lists items").build();

        LocalFunction tool = new LocalFunction(card, inputs -> {
            List<String> items = List.of("item1", "item2", "item3");
            return items.iterator();
        });

        Iterator<Object> stream = tool.stream(Map.of());
        assertNotNull(stream);
        int count = 0;
        while (stream.hasNext()) {
            Object item = stream.next();
            assertNotNull(item);
            count++;
        }
        assertEquals(3, count, "Should stream 3 items");
    }

    @Test
    @DisplayName("Tool with empty id should throw error")
    void testToolIdValidation() {
        ToolCard card = ToolCard.builder().id("").name("bad_tool").description("Should fail").build();

        assertThrows(Exception.class, () -> new LocalFunction(card, inputs -> null), "Tool with empty id should throw");
    }
}
