/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.harness.cli.agent.CliAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared fixtures and bootstrap tests for CLI tests.
 * <p>
 * Mirrors Python's {@code conftest} in
 * {@code tests.cli.conftest}.
 */
class CliConftestTest {

    @Test
    void cliConfigWithApiKey() {
        Map<String, Object> config = CliAgentConfig.loadConfig(null);
        assertNotNull(config);
        assertEquals("cn", config.get("language"));
        assertEquals("full", config.get("mode"));
    }

    @Test
    void cliConfigWithConfigPath() {
        Map<String, Object> config = CliAgentConfig.loadConfig("/path/to/config.yaml");
        assertEquals("/path/to/config.yaml", config.get("config_path"));
    }

    @Test
    void cliConfigDefaultsAreSet() {
        Map<String, Object> config = CliAgentConfig.loadConfig(null);
        assertTrue(config.containsKey("language"));
        assertTrue(config.containsKey("mode"));
        assertTrue(config.containsKey("workspace"));
    }
}
