/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolCard and ToolInfo.
 * Ported from Python: tests/unit_tests/core/foundation/tool/test_tool_decorator.py
 * (ToolInfo creation and card property tests)
 */
class ToolCardTest {

    @Nested
    @DisplayName("ToolCard tests")
    class ToolCardTests {

        @Test
        @DisplayName("ToolCard builder creates card with correct properties")
        void testToolCardBuilder() {
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

            assertEquals("local_sub", card.getName());
            assertEquals("local function for sub", card.getDescription());
            assertNotNull(card.getInputParams());
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
        @DisplayName("toolInfo() returns correct ToolInfo from ToolCard")
        void testToolInfoFromCard() {
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

            ToolInfo toolInfo = (ToolInfo) card.toolInfo();
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
                                                    "name", Map.of("type", "string", "description", "商品名称"),
                                                    "sales", Map.of("type", "integer", "default", 0, "description", "销量"),
                                                    "price", Map.of("type", "number", "default", 1.0, "description", "价格")
                                            )
                                    ),
                                    "description", "products"
                            )
                    ),
                    "required", new String[]{"title", "products"}
            );

            ToolInfo info = ToolInfo.builder()
                    .name("summarize")
                    .description("汇总商品信息")
                    .parameters(parameters)
                    .build();

            assertEquals("summarize", info.getName());
            assertEquals("汇总商品信息", info.getDescription());

            Map<String, Object> props = (Map<String, Object>) info.getParameters().get("properties");
            assertNotNull(props);
            assertTrue(props.containsKey("title"));
            assertTrue(props.containsKey("products"));

            Map<String, Object> productsSchema = (Map<String, Object>) props.get("products");
            assertEquals("array", productsSchema.get("type"));
        }

        @Test
        @DisplayName("ToolInfo equality comparison")
        void testToolInfoEquality() {
            Map<String, Object> params = Map.of(
                    "type", "object",
                    "properties", Map.of("x", Map.of("type", "string"))
            );

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
}
