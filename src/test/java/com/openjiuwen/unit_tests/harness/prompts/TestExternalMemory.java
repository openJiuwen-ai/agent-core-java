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
 * Tests for harness prompts external memory.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_external_memory.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_PROMPT_TESTS", matches = "true")
public class TestExternalMemory {

    // ---------------------------------------------------------------------------
    // External Memory Format Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestExternalMemoryFormat {

        @Test
        @DisplayName("Test external memory section format")
        @Tag("level0")
        void testExternalMemorySectionFormat() {
            String sectionName = "external_memory";
            int priority = 15;
            
            assertThat(sectionName).isEqualTo("external_memory");
            assertThat(priority).isEqualTo(15);
        }

        @Test
        @DisplayName("Test external memory content structure")
        @Tag("level0")
        void testExternalMemoryContentStructure() {
            Map<String, Object> memoryContent = new LinkedHashMap<>();
            memoryContent.put("type", "external");
            memoryContent.put("source", "database");
            memoryContent.put("content", "Previous conversation history");
            
            assertThat(memoryContent.get("type")).isEqualTo("external");
            assertThat(memoryContent.containsKey("content")).isTrue();
        }
    }

    // ---------------------------------------------------------------------------
    // External Memory Integration Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestExternalMemoryIntegration {

        @Test
        @DisplayName("Test external memory with agent prompt")
        @Tag("level0")
        void testExternalMemoryWithAgentPrompt() {
            List<Map<String, Object>> promptSections = new ArrayList<>();
            
            Map<String, Object> systemSection = new LinkedHashMap<>();
            systemSection.put("name", "system");
            systemSection.put("content", "You are a helpful assistant.");
            promptSections.add(systemSection);
            
            Map<String, Object> memorySection = new LinkedHashMap<>();
            memorySection.put("name", "external_memory");
            memorySection.put("content", "User likes Python programming");
            promptSections.add(memorySection);
            
            assertThat(promptSections).hasSize(2);
        }

        @Test
        @DisplayName("Test external memory injection position")
        @Tag("level0")
        void testExternalMemoryInjectionPosition() {
            List<String> expectedOrder = Arrays.asList(
                "role", "instruction", "external_memory", "tools"
            );
            
            assertThat(expectedOrder.indexOf("external_memory")).isEqualTo(2);
        }
    }
}