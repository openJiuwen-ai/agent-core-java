/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ToolCard and ToolInfo helper models.
 * <p>
 * Complements the Python {@code test_tool_decorator.py} mapping by covering
 * generated card metadata and ToolInfo conversion.
 */
class ToolCardTest {

    @Nested
    @DisplayName("ToolCard tests")
    class ToolCardTests {

        @Test
        @DisplayName("ToolCard builder creates card with correct properties")
        void testToolCardBuilder() {
            Map<String, Object> inputParams = twoArgSchema();

            ToolCard card = ToolCard.builder()
                    .name("local_sub")
                    .description("local function for sub")
                    .inputParams(inputParams)
                    .build();

            assertEquals("local_sub", card.getName());
            assertEquals("local function for sub", card.getDescription());
            assertEquals(inputParams, card.getInputParams());
        }

        @Test
        @DisplayName("ToolCard auto-generates id")
        void testToolCardAutoId() {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test card")
                    .build();

            assertNotNull(card.getId());
            assertFalse(card.getId().isEmpty());
        }

        @Test
        @DisplayName("ToolCard default inputParams is empty map")
        void testToolCardDefaultInputParams() {
            ToolCard card = ToolCard.builder()
                    .name("test")
                    .description("test")
                    .build();

            assertNotNull(card.getInputParams());
            assertTrue(card.getInputParams().isEmpty());
        }
    }

    @Nested
    @DisplayName("ToolInfo tests")
    class ToolInfoTests {

        @Test
        @DisplayName("toolInfo returns correct ToolInfo from ToolCard")
        void testToolInfoFromCard() {
            Map<String, Object> inputParams = twoArgSchema();

            ToolCard card = ToolCard.builder()
                    .name("local_sub")
                    .description("local function for sub")
                    .inputParams(inputParams)
                    .build();

            ToolInfo toolInfo = card.toolInfo();
            assertEquals("local_sub", toolInfo.getName());
            assertEquals("local function for sub", toolInfo.getDescription());
            assertEquals(inputParams, toolInfo.getParameters());
        }

        @Test
        @DisplayName("ToolInfo builder defaults")
        void testToolInfoDefaults() {
            ToolInfo info = ToolInfo.builder().build();
            assertEquals("function", info.getType());
            assertEquals("", info.getName());
            assertEquals("", info.getDescription());
            assertNotNull(info.getParameters());
            assertTrue(info.getParameters().isEmpty());
        }

        @Test
        @DisplayName("ToolInfo with complex nested parameters")
        @SuppressWarnings("unchecked")
        void testToolInfoComplexParams() {
            Map<String, Object> parameters = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "title", Map.of("type", "string", "description", "title"),
                            "products", Map.of(
                                    "type", "array",
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "name", Map.of("type", "string", "description", "product name"),
                                                    "sales", Map.of("type", "integer", "default", 0,
                                                            "description", "sales"),
                                                    "price", Map.of("type", "number", "default", 1.0,
                                                            "description", "price"))),
                                    "description", "products")),
                    "required", new String[]{"title", "products"});

            ToolInfo info = ToolInfo.builder()
                    .name("summarize")
                    .description("summarize product information")
                    .parameters(parameters)
                    .build();

            assertEquals("summarize", info.getName());
            assertEquals("summarize product information", info.getDescription());

            Map<String, Object> props = (Map<String, Object>) info.getParameters().get("properties");
            assertNotNull(props);
            assertTrue(props.containsKey("title"));
            assertTrue(props.containsKey("products"));
            assertEquals("array", ((Map<String, Object>) props.get("products")).get("type"));
        }

        @Test
        @DisplayName("ToolInfo equality comparison")
        void testToolInfoEquality() {
            Map<String, Object> params = Map.of(
                    "type", "object",
                    "properties", Map.of("x", Map.of("type", "string")));

            ToolInfo info1 = ToolInfo.builder()
                    .name("test")
                    .description("test tool")
                    .parameters(params)
                    .build();
            ToolInfo info2 = ToolInfo.builder()
                    .name("test")
                    .description("test tool")
                    .parameters(params)
                    .build();

            assertEquals(info1, info2);
        }
    }

    private Map<String, Object> twoArgSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("description", "first arg", "type", "integer"),
                        "b", Map.of("description", "second arg", "type", "integer")),
                "required", new String[]{"a", "b"});
    }
}
