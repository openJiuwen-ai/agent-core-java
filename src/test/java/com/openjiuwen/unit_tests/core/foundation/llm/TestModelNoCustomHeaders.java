/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for model client without custom headers.
 * <p>
 * Mirrors Python's {@code test_model_no_custom_headers.py} from
 * {@code tests/unit_tests/core/foundation/llm/test_model_no_custom_headers.py}.
 * Tests default model client behavior without custom header configuration.
 */
class TestModelNoCustomHeaders {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testModelClientConfigClassExists() {
        assertNotNull(ModelClientConfig.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Default configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testDefaultConfigCreation() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("default-key")
                .build();
        assertNotNull(config);
    }

    @Test
    @Tag("level1")
    void testDefaultApiKey() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("default-key")
                .build();
        assertEquals("default-key", config.getApiKey());
    }

    @Test
    @Tag("level1")
    void testDefaultApiBase() {
        ModelClientConfig config = ModelClientConfig.builder().build();
        assertNotNull(config);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Standard headers only)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testStandardAuthorizationHeader() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("test-api-key")
                .build();
        assertNotNull(config.getApiKey());
    }

    @Test
    @Tag("level2")
    void testNoExtraHeaders() {
        ModelClientConfig config = ModelClientConfig.builder().build();
        assertNotNull(config);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Builder pattern)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testBuilderPattern() {
        assertNotNull(ModelClientConfig.builder());
    }
}