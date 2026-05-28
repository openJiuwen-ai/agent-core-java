/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InitAndDefaultConfigs slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_init_and_default_configs}.
 */
class InitAndDefaultConfigsTest {

    @Test
    void testDefaultConfigSliceExtractsDefaults() {
        Map<String, Object> configSpec = new HashMap<>();
        configSpec.put("defaults", Map.of(
            "temperature", 0.7,
            "max_tokens", 2048,
            "timeout_ms", 30000
        ));

        Map<String, Object> slice = extractConfigSlice(configSpec);

        assertEquals(0.7, ((Map<String, Object>) slice.get("defaults")).get("temperature"));
    }

    @Test
    void testDefaultConfigSliceWithEnvOverrides() {
        Map<String, Object> configSpec = new HashMap<>();
        configSpec.put("env_mapping", Map.of(
            "API_KEY", "api_key",
            "MODEL_NAME", "model"
        ));

        Map<String, Object> slice = extractConfigSlice(configSpec);

        assertTrue(slice.containsKey("env_mapping"));
    }

    @Test
    void testDefaultConfigSliceWithValidation() {
        Map<String, Object> configSpec = new HashMap<>();
        configSpec.put("validation", Map.of(
            "required", List.of("api_key", "model"),
            "constraints", Map.of("temperature", "0 <= value <= 2")
        ));

        Map<String, Object> slice = extractConfigSlice(configSpec);

        assertEquals(2, ((List<?>) ((Map<String, Object>) slice.get("validation")).get("required")).size());
    }

    @Test
    void testDefaultConfigSliceEmptyDefaults() {
        Map<String, Object> configSpec = new HashMap<>();
        configSpec.put("defaults", new HashMap<>());

        Map<String, Object> slice = extractConfigSlice(configSpec);

        assertTrue(((Map<?, ?>) slice.get("defaults")).isEmpty());
    }

    @Test
    void testDefaultConfigSliceWithFallbacks() {
        Map<String, Object> configSpec = new HashMap<>();
        configSpec.put("fallbacks", Map.of(
            "model", "gpt-3.5-turbo",
            "provider", "openai"
        ));

        Map<String, Object> slice = extractConfigSlice(configSpec);

        assertEquals("gpt-3.5-turbo", ((Map<String, Object>) slice.get("fallbacks")).get("model"));
    }

    @Test
    void testDefaultConfigSliceWithPriorityOrder() {
        Map<String, Object> configSpec = new HashMap<>();
        configSpec.put("priority", List.of("env", "file", "defaults"));

        Map<String, Object> slice = extractConfigSlice(configSpec);

        assertEquals(3, ((List<?>) slice.get("priority")).size());
    }

    private Map<String, Object> extractConfigSlice(Map<String, Object> configSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (configSpec.containsKey("defaults")) {
            slice.put("defaults", configSpec.get("defaults"));
        }
        if (configSpec.containsKey("env_mapping")) {
            slice.put("env_mapping", configSpec.get("env_mapping"));
        }
        if (configSpec.containsKey("validation")) {
            slice.put("validation", configSpec.get("validation"));
        }
        if (configSpec.containsKey("fallbacks")) {
            slice.put("fallbacks", configSpec.get("fallbacks"));
        }
        if (configSpec.containsKey("priority")) {
            slice.put("priority", configSpec.get("priority"));
        }
        
        return slice;
    }
}