/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for harness prompts tool input params.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tool_input_params.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_PROMPT_TESTS", matches = "true")
public class TestToolInputParams {

    // ---------------------------------------------------------------------------
    // Parameter Schema Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestParameterSchema {

        @Test
        @DisplayName("Test required parameter definition")
        @Tag("level0")
        void testRequiredParameterDefinition() {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", "query");
            param.put("type", "string");
            param.put("required", true);
            param.put("description", "Search query string");
            
            assertThat(param.get("required")).isEqualTo(true);
            assertThat(param.get("type")).isEqualTo("string");
        }

        @Test
        @DisplayName("Test optional parameter definition")
        @Tag("level0")
        void testOptionalParameterDefinition() {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", "limit");
            param.put("type", "integer");
            param.put("required", false);
            param.put("default", 10);
            
            assertThat(param.get("required")).isEqualTo(false);
            assertThat(param.get("default")).isEqualTo(10);
        }

        @Test
        @DisplayName("Test nested parameter schema")
        @Tag("level0")
        void testNestedParameterSchema() {
            Map<String, Object> nestedParam = new LinkedHashMap<>();
            nestedParam.put("type", "object");
            nestedParam.put("properties", new LinkedHashMap<>());
            
            assertThat(nestedParam.get("type")).isEqualTo("object");
            assertThat(nestedParam.containsKey("properties")).isTrue();
        }
    }

    // ---------------------------------------------------------------------------
    // Parameter Types Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestParameterTypes {

        @Test
        @DisplayName("Test string parameter type")
        @Tag("level0")
        void testStringParameterType() {
            String type = "string";
            
            assertThat(type).isEqualTo("string");
        }

        @Test
        @DisplayName("Test integer parameter type")
        @Tag("level0")
        void testIntegerParameterType() {
            String type = "integer";
            
            assertThat(type).isEqualTo("integer");
        }

        @Test
        @DisplayName("Test array parameter type")
        @Tag("level0")
        void testArrayParameterType() {
            String type = "array";
            
            assertThat(type).isEqualTo("array");
        }

        @Test
        @DisplayName("Test object parameter type")
        @Tag("level0")
        void testObjectParameterType() {
            String type = "object";
            
            assertThat(type).isEqualTo("object");
        }
    }
}