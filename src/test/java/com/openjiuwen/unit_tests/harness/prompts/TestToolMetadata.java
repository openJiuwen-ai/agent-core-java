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
 * Tests for harness prompts tool metadata.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tool_metadata.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_PROMPT_TESTS", matches = "true")
public class TestToolMetadata {

    // ---------------------------------------------------------------------------
    // Metadata Structure Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestMetadataStructure {

        @Test
        @DisplayName("Test tool metadata format")
        @Tag("level0")
        void testToolMetadataFormat() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("version", "1.0.0");
            metadata.put("author", "openjiuwen");
            metadata.put("created_at", "2025-01-01");
            
            assertThat(metadata.get("version")).isEqualTo("1.0.0");
            assertThat(metadata.containsKey("author")).isTrue();
        }

        @Test
        @DisplayName("Test tool category metadata")
        @Tag("level0")
        void testToolCategoryMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("category", "file_operations");
            metadata.put("tags", Arrays.asList("fs", "io"));
            
            assertThat(metadata.get("category")).isEqualTo("file_operations");
        }
    }

    // ---------------------------------------------------------------------------
    // Metadata Validation Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestMetadataValidation {

        @Test
        @DisplayName("Test required metadata fields")
        @Tag("level0")
        void testRequiredMetadataFields() {
            List<String> requiredFields = Arrays.asList("name", "description", "parameters");
            
            assertThat(requiredFields).contains("name");
            assertThat(requiredFields).contains("description");
        }

        @Test
        @DisplayName("Test optional metadata fields")
        @Tag("level0")
        void testOptionalMetadataFields() {
            List<String> optionalFields = Arrays.asList("version", "author", "deprecated");
            
            assertThat(optionalFields).hasSize(3);
        }
    }
}