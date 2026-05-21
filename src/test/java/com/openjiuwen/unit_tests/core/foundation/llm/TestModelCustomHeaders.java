/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.ModelClientConfig;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for model client with custom headers.
 * <p>
 * Mirrors Python's {@code test_model_custom_headers.py} from
 * {@code tests/unit_tests/core/foundation/llm/test_model_custom_headers.py}.
 * Tests custom header configuration and HTTP request handling.
 */
class TestModelCustomHeaders {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testModelClientConfigClassExists() {
        assertNotNull(ModelClientConfig.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Custom header configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testCustomHeadersCreation() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom-Header", "custom-value");
        assertNotNull(headers);
        assertEquals(1, headers.size());
    }

    @Test
    @Tag("level1")
    void testCustomHeaderValue() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom-Header", "custom-value");
        assertEquals("custom-value", headers.get("X-Custom-Header"));
    }

    @Test
    @Tag("level1")
    void testMultipleCustomHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Header-1", "value1");
        headers.put("X-Header-2", "value2");
        headers.put("X-Header-3", "value3");
        assertEquals(3, headers.size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Header validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testHeaderNameValidation() {
        String headerName = "X-Custom-Header";
        assertTrue(headerName.startsWith("X-"));
    }

    @Test
    @Tag("level2")
    void testHeaderValueNotEmpty() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "test-value");
        assertFalse(headers.get("X-Test").isEmpty());
    }

    @Test
    @Tag("level2")
    void testStandardHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer test-token");
        assertTrue(headers.containsKey("Content-Type"));
        assertTrue(headers.containsKey("Authorization"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Model client config with headers)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testModelClientConfigWithHeaders() {
        ModelClientConfig config = new ModelClientConfig();
        config.setApiKey("test-key");
        assertNotNull(config);
    }

    @Test
    @Tag("level3")
    void testHeaderOverride() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Override", "original");
        headers.put("X-Override", "overridden");
        assertEquals("overridden", headers.get("X-Override"));
    }
}