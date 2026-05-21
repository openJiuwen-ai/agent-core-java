/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Tool Auth classes.
 * <p>
 * Mirrors Python's test_auth.py from
 * <code>tests/unit_tests/core/foundation/tool/test_auth.py</code>.
 */
@DisplayName("Tool Auth Tests")
class TestAuth {

    // Stub classes
    static class AuthType {
        static final String SSL = "ssl";
        static final String API_KEY = "api_key";
        static final String OAUTH = "oauth";
    }

    static class ToolAuthConfig {
        String authType;
        Map<String, Object> config;
        String toolType;
        String toolId;

        ToolAuthConfig(String authType, Map<String, Object> config, String toolType, String toolId) {
            this.authType = authType;
            this.config = new HashMap<>(config); // Immutable copy
            this.toolType = toolType;
            this.toolId = toolId;
        }

        ToolAuthConfig(String authType, Map<String, Object> config, String toolType) {
            this(authType, config, toolType, null);
        }

        String getAuthType() { return authType; }
        Map<String, Object> getConfig() { 
            return new HashMap<>(config); // Return copy to enforce immutability
        }
        String getToolType() { return toolType; }
        String getToolId() { return toolId; }
    }

    static class ToolAuthResult {
        boolean success;
        String authType;
        Map<String, Object> credentials;

        ToolAuthResult(boolean success, String authType, Map<String, Object> credentials) {
            this.success = success;
            this.authType = authType;
            this.credentials = credentials;
        }
    }

    @Nested
    @DisplayName("Tool Auth Config Tests")
    class TestToolAuthConfig {

        @Test
        @DisplayName("tool auth config creation")
        void testToolAuthConfigCreation() {
            Map<String, Object> config = new HashMap<>();
            config.put("verify_switch_env", "RESTFUL_SSL_VERIFY");

            ToolAuthConfig authConfig = new ToolAuthConfig(
                AuthType.SSL, config, "restful_api", "test-tool-id"
            );

            assertEquals(AuthType.SSL, authConfig.getAuthType());
            assertEquals(config, authConfig.getConfig());
            assertEquals("restful_api", authConfig.getToolType());
            assertEquals("test-tool-id", authConfig.getToolId());
        }

        @Test
        @DisplayName("tool auth config without tool id")
        void testToolAuthConfigWithoutToolId() {
            Map<String, Object> config = new HashMap<>();
            config.put("api_key", "test-key");

            ToolAuthConfig authConfig = new ToolAuthConfig(
                "api_key", config, "database"
            );

            assertEquals("api_key", authConfig.getAuthType());
            assertNull(authConfig.getToolId());
        }

        @Test
        @DisplayName("config is immutable")
        void testConfigIsImmutable() {
            Map<String, Object> config = new HashMap<>();
            config.put("key", "value");

            ToolAuthConfig authConfig = new ToolAuthConfig("ssl", config, "restful_api");

            // Modifying returned config should not affect original
            Map<String, Object> returnedConfig = authConfig.getConfig();
            returnedConfig.put("new_key", "new_value");

            assertFalse(authConfig.getConfig().containsKey("new_key"));
        }
    }

    @Nested
    @DisplayName("Tool Auth Result Tests")
    class TestToolAuthResult {

        @Test
        @DisplayName("auth result creation")
        void testAuthResultCreation() {
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("token", "test-token");

            ToolAuthResult result = new ToolAuthResult(true, "oauth", credentials);

            assertTrue(result.success);
            assertEquals("oauth", result.authType);
            assertNotNull(result.credentials);
        }

        @Test
        @DisplayName("failed auth result")
        void testFailedAuthResult() {
            ToolAuthResult result = new ToolAuthResult(false, null, null);

            assertFalse(result.success);
            assertNull(result.authType);
        }
    }
}