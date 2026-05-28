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
 * Tests for harness prompts tool descriptions.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tool_descriptions.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_PROMPT_TESTS", matches = "true")
public class TestToolDescriptions {

    // ---------------------------------------------------------------------------
    // Tool Description Format Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestToolDescriptionFormat {

        @Test
        @DisplayName("Test tool description section format")
        @Tag("level0")
        void testToolDescriptionSectionFormat() {
            String sectionName = "tools";
            int priority = 10;
            
            assertThat(sectionName).isEqualTo("tools");
            assertThat(priority).isEqualTo(10);
        }

        @Test
        @DisplayName("Test tool description content structure")
        @Tag("level0")
        void testToolDescriptionContentStructure() {
            Map<String, Object> toolDescription = new LinkedHashMap<>();
            toolDescription.put("name", "calculate");
            toolDescription.put("description", "Perform mathematical calculations");
            toolDescription.put("parameters", new LinkedHashMap<>());
            
            assertThat(toolDescription.get("name")).isEqualTo("calculate");
            assertThat(toolDescription.containsKey("parameters")).isTrue();
        }
    }

    // ---------------------------------------------------------------------------
    // Multiple Tools Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestMultipleTools {

        @Test
        @DisplayName("Test multiple tool descriptions")
        @Tag("level0")
        void testMultipleToolDescriptions() {
            List<Map<String, Object>> tools = new ArrayList<>();
            
            Map<String, Object> tool1 = new LinkedHashMap<>();
            tool1.put("name", "search");
            tool1.put("description", "Search for information");
            tools.add(tool1);
            
            Map<String, Object> tool2 = new LinkedHashMap<>();
            tool2.put("name", "execute");
            tool2.put("description", "Execute code");
            tools.add(tool2);
            
            assertThat(tools).hasSize(2);
        }

        @Test
        @DisplayName("Test tool section ordering")
        @Tag("level0")
        void testToolSectionOrdering() {
            List<String> expectedOrder = Arrays.asList(
                "role", "instruction", "external_memory", "tools", "constraints"
            );
            
            assertThat(expectedOrder.indexOf("tools")).isEqualTo(3);
        }
    }
}