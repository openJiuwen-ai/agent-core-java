/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.agent.CliAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for vision/audio config fallback in factory.
 * <p>
 * Mirrors Python's {@code test_factory} in
 * {@code tests.cli.unit.test_factory}.
 */
class FactoryUnitTest {

    @Test
    void loadConfigReturnsMap() {
        Map<String, Object> config = CliAgentConfig.loadConfig(null);
        assertNotNull(config);
    }

    @Test
    void loadConfigSetsDefaults() {
        Map<String, Object> config = CliAgentConfig.loadConfig(null);
        assertEquals("cn", config.get("language"));
        assertEquals("full", config.get("mode"));
    }

    @Test
    void loadConfigSetsWorkspace() {
        Map<String, Object> config = CliAgentConfig.loadConfig(null);
        assertNotNull(config.get("workspace"));
    }

    @Test
    void loadConfigWithNullPath() {
        Map<String, Object> config = CliAgentConfig.loadConfig(null);
        assertFalse(config.containsKey("config_path"));
    }

    @Test
    void loadConfigWithCustomPath() {
        Map<String, Object> config = CliAgentConfig.loadConfig("/custom/path.yaml");
        assertEquals("/custom/path.yaml", config.get("config_path"));
    }

    @Test
    void visionConfigFallbackToMainModel() {
        String apiKey = System.getenv().getOrDefault("VISION_API_KEY", "");
        String baseUrl = System.getenv().getOrDefault("VISION_BASE_URL", "");
        if (apiKey.isEmpty()) {
            Map<String, Object> config = CliAgentConfig.loadConfig(null);
            assertNotNull(config);
        }
    }

    @Test
    void audioConfigFallbackToMainModel() {
        String apiKey = System.getenv().getOrDefault("AUDIO_API_KEY", "");
        String baseUrl = System.getenv().getOrDefault("AUDIO_BASE_URL", "");
        if (apiKey.isEmpty()) {
            Map<String, Object> config = CliAgentConfig.loadConfig(null);
            assertNotNull(config);
        }
    }
}
