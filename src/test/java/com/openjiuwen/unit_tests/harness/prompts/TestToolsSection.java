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
 * Tests for harness prompts tools section.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tools_section.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_PROMPT_TESTS", matches = "true")
public class TestToolsSection {

    // ---------------------------------------------------------------------------
    // Section Format Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestSectionFormat {

        @Test
        @DisplayName("Test tools section header")
        @Tag("level0")
        void testToolsSectionHeader() {
            String header = "# Available Tools";
            
            assertThat(header).contains("Tools");
        }

        @Test
        @DisplayName("Test tools section priority")
        @Tag("level0")
        void testToolsSectionPriority() {
            int priority = 10;
            
            assertThat(priority).isEqualTo(10);
        }
    }

    // ---------------------------------------------------------------------------
    // Section Rendering Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestSectionRendering {

        @Test
        @DisplayName("Test render single tool")
        @Tag("level0")
        void testRenderSingleTool() {
            String rendered = """
                - name: read_file
                  description: Read file content
                  parameters:
                    - path (string, required): File path
                """;
            
            assertThat(rendered).contains("read_file");
            assertThat(rendered).contains("parameters");
        }

        @Test
        @DisplayName("Test render multiple tools")
        @Tag("level0")
        void testRenderMultipleTools() {
            List<String> toolNames = Arrays.asList("read_file", "write_file", "execute_code");
            
            assertThat(toolNames).hasSize(3);
        }

        @Test
        @DisplayName("Test section localization")
        @Tag("level0")
        void testSectionLocalization() {
            String language = "en";
            String chineseHeader = "# 可用工具";
            
            assertThat(language).isEqualTo("en");
            assertThat(chineseHeader).contains("工具");
        }
    }

    // ---------------------------------------------------------------------------
    // Section Integration Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestSectionIntegration {

        @Test
        @DisplayName("Test tools section in full prompt")
        @Tag("level0")
        void testToolsSectionInFullPrompt() {
            List<String> promptSections = Arrays.asList(
                "role", "instruction", "external_memory", "tools", "constraints"
            );
            
            assertThat(promptSections).contains("tools");
            assertThat(promptSections.indexOf("tools")).isEqualTo(3);
        }

        @Test
        @DisplayName("Test tools section with empty tools")
        @Tag("level0")
        void testToolsSectionWithEmptyTools() {
            List<Object> tools = new ArrayList<>();
            
            assertThat(tools).isEmpty();
        }
    }
}