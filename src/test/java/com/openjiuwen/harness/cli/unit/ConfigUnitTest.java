/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.agent.CliAgentConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI configuration management.
 *
 * <p>Mirrors Python's {@code test_config.py} in
 * {@code tests.cli.unit.test_config}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Default configuration values</li>
 *   <li>Validation of configuration values</li>
 *   <li>Loading and saving settings JSON</li>
 *   <li>Three-layer priority merge</li>
 * </ul>
 */
class ConfigUnitTest {

    @Nested
    class TestCliConfig {
        @Test
        void defaultValues() {
            Map<String, Object> cfg = CliAgentConfig.defaultConfig();
            assertEquals("OpenAI", cfg.get("provider"));
            assertEquals("gpt-4o", cfg.get("model"));
            assertEquals("", cfg.get("api_key"));
            assertEquals(8192, cfg.get("max_tokens"));
            assertEquals(30, cfg.get("max_iterations"));
            assertEquals("https://api.openai.com/v1", cfg.get("api_base"));
            assertEquals("", cfg.get("server_url"));
        }

        @Test
        void validateNoApiKey() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "");
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> CliAgentConfig.validate(cfg));
            assertTrue(error.getMessage().contains("API key"));
        }

        @Test
        void validateSmallMaxTokens() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "test");
            cfg.put("max_tokens", 32);
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> CliAgentConfig.validate(cfg));
            assertTrue(error.getMessage().contains("dangerously small"));
        }

        @Test
        void validateMaxTokensBoundary() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "test");
            cfg.put("max_tokens", 256);
            assertDoesNotThrow(() -> CliAgentConfig.validate(cfg));
        }

        @Test
        void validateBadMaxIterations() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "test");
            cfg.put("max_iterations", 0);
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> CliAgentConfig.validate(cfg));
            assertTrue(error.getMessage().contains("max_iterations"));
        }

        @Test
        void validateWithServerUrl() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "");
            cfg.put("server_url", "http://localhost:8080");
            assertDoesNotThrow(() -> CliAgentConfig.validate(cfg));
        }

        @Test
        void validateSuccess() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "sk-test-key");
            assertDoesNotThrow(() -> CliAgentConfig.validate(cfg));
        }

        @Test
        void validateErrorMentionsSettingsJson() {
            Map<String, Object> cfg = defaultConfig();
            cfg.put("api_key", "");
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> CliAgentConfig.validate(cfg));
            assertTrue(error.getMessage().contains("settings.json"));
        }
    }

    @Nested
    class TestLoadSettingsJson {
        @Test
        void missingFile(@TempDir Path tempDir) {
            assertTrue(CliAgentConfig.loadSettingsJson(tempDir.resolve("nope.json")).isEmpty());
        }

        @Test
        void validFile(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"apiKey\": \"sk-test\", \"model\": \"gpt-4o\"}");
            Map<String, Object> result = CliAgentConfig.loadSettingsJson(settings);
            assertEquals("sk-test", result.get("apiKey"));
            assertEquals("gpt-4o", result.get("model"));
        }

        @Test
        void malformedJson(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{invalid json");
            assertTrue(CliAgentConfig.loadSettingsJson(settings).isEmpty());
        }

        @Test
        void nonDictJson(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "[\"a\", \"b\"]");
            assertTrue(CliAgentConfig.loadSettingsJson(settings).isEmpty());
        }

        @Test
        void emptyFile(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "");
            assertTrue(CliAgentConfig.loadSettingsJson(settings).isEmpty());
        }
    }

    @Nested
    class TestSaveSettingsJson {
        @Test
        void createsNewFile(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("sub").resolve("settings.json");
            CliAgentConfig.saveSettingsJson(Map.of("apiKey", "sk-test"), settings);
            Map<String, Object> data = CliAgentConfig.loadSettingsJson(settings);
            assertEquals("sk-test", data.get("apiKey"));
        }

        @Test
        void mergesExisting(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"model\": \"gpt-4o\", \"apiKey\": \"old\"}");
            CliAgentConfig.saveSettingsJson(Map.of("apiKey", "new"), settings);
            Map<String, Object> data = CliAgentConfig.loadSettingsJson(settings);
            assertEquals("new", data.get("apiKey"));
            assertEquals("gpt-4o", data.get("model"));
        }

        @Test
        void returnsPath(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Path result = CliAgentConfig.saveSettingsJson(Map.of("a", 1), settings);
            assertEquals(settings, result);
        }
    }

    @Nested
    class TestLoadConfig {
        @Test
        void envOverride(@TempDir Path tempDir) {
            Map<String, String> env = Map.of(
                    "OPENJIUWEN_MODEL", "qwen-max",
                    "OPENJIUWEN_PROVIDER", "DashScope",
                    "OPENJIUWEN_API_KEY", "test-key");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(
                    Map.of(), env, tempDir.resolve("settings.json"));
            assertEquals("qwen-max", cfg.get("model"));
            assertEquals("DashScope", cfg.get("provider"));
        }

        @Test
        void cliArgsOverrideEnv(@TempDir Path tempDir) {
            Map<String, String> env = Map.of(
                    "OPENJIUWEN_MODEL", "qwen-max",
                    "OPENJIUWEN_API_KEY", "test-key");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(
                    Map.of("model", "gpt-4o-mini"), env, tempDir.resolve("settings.json"));
            assertEquals("gpt-4o-mini", cfg.get("model"));
        }

        @Test
        void loadConfigValidates(@TempDir Path tempDir) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CliAgentConfig.loadConfig(Map.of(), Map.of(), tempDir.resolve("settings.json")));
        }

        @Test
        void settingsJsonOverridesDefaults(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"apiKey\": \"from-json\", \"model\": \"qwen\"}");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(Map.of(), Map.of(), settings);
            assertEquals("from-json", cfg.get("api_key"));
            assertEquals("qwen", cfg.get("model"));
        }

        @Test
        void envOverridesSettingsJson(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"apiKey\": \"from-json\", \"model\": \"from-json\"}");
            Map<String, String> env = Map.of(
                    "OPENJIUWEN_API_KEY", "from-env",
                    "OPENJIUWEN_MODEL", "from-env");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(Map.of(), env, settings);
            assertEquals("from-env", cfg.get("api_key"));
            assertEquals("from-env", cfg.get("model"));
        }

        @Test
        void cliOverridesSettingsJson(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"apiKey\": \"from-json\"}");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(
                    Map.of("api_key", "from-cli"), Map.of(), settings);
            assertEquals("from-cli", cfg.get("api_key"));
        }

        @Test
        void settingsJsonMaxTokens(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"apiKey\": \"k\", \"maxTokens\": 4096}");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(Map.of(), Map.of(), settings);
            assertEquals(4096, cfg.get("max_tokens"));
        }

        @Test
        void settingsJsonMaxIterations(@TempDir Path tempDir) throws IOException {
            Path settings = tempDir.resolve("settings.json");
            Files.writeString(settings, "{\"apiKey\": \"k\", \"maxIterations\": 15}");
            Map<String, Object> cfg = CliAgentConfig.loadConfig(Map.of(), Map.of(), settings);
            assertEquals(15, cfg.get("max_iterations"));
        }
    }

    private static Map<String, Object> defaultConfig() {
        return new LinkedHashMap<>(CliAgentConfig.defaultConfig());
    }
}
