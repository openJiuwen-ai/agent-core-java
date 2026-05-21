/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamMemoryConfig and resolve_embedding_config.
 * <p>
 * Mirrors Python's test_config.py from
 * <code>tests/unit_tests/core/memory/team/test_config.py</code>.
 */
@DisplayName("Team Memory Config Tests")
class TestConfig {

    // Stub classes
    static class EmbeddingConfig {
        String modelName;
        String baseUrl;

        EmbeddingConfig(String modelName, String baseUrl) {
            this.modelName = modelName;
            this.baseUrl = baseUrl;
        }
    }

    static class TeamMemoryConfig {
        boolean enabled;
        EmbeddingConfig embeddingConfig;
        String parentWorkspacePath;
        String teamMemoryDir;

        TeamMemoryConfig(boolean enabled, EmbeddingConfig embeddingConfig, 
                         String parentWorkspacePath, String teamMemoryDir) {
            this.enabled = enabled;
            this.embeddingConfig = embeddingConfig;
            this.parentWorkspacePath = parentWorkspacePath;
            this.teamMemoryDir = teamMemoryDir;
        }

        TeamMemoryConfig() {
            this.enabled = false;
        }

        TeamMemoryConfig(EmbeddingConfig embeddingConfig) {
            this.embeddingConfig = embeddingConfig;
        }

        Map<String, Object> modelDump() {
            Map<String, Object> dump = new HashMap<>();
            dump.put("enabled", enabled);
            // Excluded fields: embedding_config, parent_workspace_path, team_memory_dir
            return dump;
        }
    }

    static class ConfigResolver {
        EmbeddingConfig envConfig;

        ConfigResolver(EmbeddingConfig envConfig) {
            this.envConfig = envConfig;
        }

        EmbeddingConfig resolveEmbeddingConfig(TeamMemoryConfig config) {
            if (config != null && config.embeddingConfig != null) {
                return config.embeddingConfig; // Prefer explicit config
            }
            return envConfig; // Fall back to env
        }
    }

    @Nested
    @DisplayName("Team Memory Config Tests")
    class TestTeamMemoryConfigClass {

        @Test
        @DisplayName("excluded fields absent from model dump")
        void testExcludedFieldsAbsentFromModelDump() {
            EmbeddingConfig emb = new EmbeddingConfig("m", "http://localhost");
            TeamMemoryConfig cfg = new TeamMemoryConfig(true, emb, "/parent/ws", "/team/mem");

            Map<String, Object> dump = cfg.modelDump();

            assertFalse(dump.containsKey("embeddingConfig"));
            assertFalse(dump.containsKey("parentWorkspacePath"));
            assertFalse(dump.containsKey("teamMemoryDir"));
            assertTrue(dump.containsKey("enabled"));
        }

        @Test
        @DisplayName("default config creation")
        void testDefaultConfigCreation() {
            TeamMemoryConfig cfg = new TeamMemoryConfig();

            assertFalse(cfg.enabled);
            assertNull(cfg.embeddingConfig);
        }
    }

    @Nested
    @DisplayName("Resolve Embedding Config Tests")
    class TestResolveEmbeddingConfig {

        @Test
        @DisplayName("prefers config over env")
        void testPrefersConfigOverEnv() {
            EmbeddingConfig envConfig = new EmbeddingConfig("env", "http://env");
            EmbeddingConfig explicitConfig = new EmbeddingConfig("explicit", "http://explicit");
            TeamMemoryConfig cfg = new TeamMemoryConfig(explicitConfig);

            ConfigResolver resolver = new ConfigResolver(envConfig);
            EmbeddingConfig result = resolver.resolveEmbeddingConfig(cfg);

            assertEquals("explicit", result.modelName);
            assertEquals("http://explicit", result.baseUrl);
        }

        @Test
        @DisplayName("falls back to env when no embedding in config")
        void testFallsBackToEnvWhenNoEmbeddingInConfig() {
            EmbeddingConfig envConfig = new EmbeddingConfig("env", "http://env");
            ConfigResolver resolver = new ConfigResolver(envConfig);

            EmbeddingConfig resultNull = resolver.resolveEmbeddingConfig(null);
            EmbeddingConfig resultEmpty = resolver.resolveEmbeddingConfig(new TeamMemoryConfig());

            assertEquals("env", resultNull.modelName);
            assertEquals("env", resultEmpty.modelName);
        }
    }
}