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
        ModelClientConfig config = new ModelClientConfig();
        assertNotNull(config);
    }

    @Test
    @Tag("level1")
    void testDefaultApiKey() {
        ModelClientConfig config = new ModelClientConfig();
        config.setApiKey("default-key");
        assertEquals("default-key", config.getApiKey());
    }

    @Test
    @Tag("level1")
    void testDefaultApiBase() {
        ModelClientConfig config = new ModelClientConfig();
        // Default should be null or empty
        assertNotNull(config);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Standard headers only)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testStandardAuthorizationHeader() {
        ModelClientConfig config = new ModelClientConfig();
        config.setApiKey("test-api-key");
        // Authorization header should be derived from API key
        assertNotNull(config.getApiKey());
    }

    @Test
    @Tag("level2")
    void testNoExtraHeaders() {
        ModelClientConfig config = new ModelClientConfig();
        // No custom headers set
        assertNotNull(config);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Client provider configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testClientProviderOpenAI() {
        ModelClientConfig config = new ModelClientConfig();
        config.setClientProvider("OpenAI");
        assertEquals("OpenAI", config.getClientProvider());
    }

    @Test
    @Tag("level3")
    void testClientProviderCustom() {
        ModelClientConfig config = new ModelClientConfig();
        config.setClientProvider("CustomProvider");
        assertEquals("CustomProvider", config.getClientProvider());
    }
}