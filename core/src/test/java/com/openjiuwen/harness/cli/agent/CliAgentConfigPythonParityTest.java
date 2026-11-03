/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.cli.unit.test_config} in
 * {@code tests/cli/unit/test_config.py}.
 */
class CliAgentConfigPythonParityTest {

    @Test
    void testDefaultValues() {
        Map<String, Object> config = CliAgentConfig.defaultConfig();

        assertEquals("OpenAI", config.get("provider"));
        assertEquals("gpt-4o", config.get("model"));
        assertEquals(8192, config.get("max_tokens"));
        assertEquals(30, config.get("max_iterations"));
        assertEquals("https://api.openai.com/v1", config.get("api_base"));
        assertEquals("", config.get("server_url"));
    }

    @Test
    void testValidateNoApiKey() {
        Map<String, Object> config = validConfig();
        config.put("api_key", "");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliAgentConfig.validate(config));

        assertTrue(error.getMessage().contains("API key"));
    }

    @Test
    void testValidateSmallMaxTokens() {
        Map<String, Object> config = validConfig();
        config.put("max_tokens", 32);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliAgentConfig.validate(config));

        assertTrue(error.getMessage().contains("dangerously small"));
    }

    @Test
    void testValidateMaxTokensBoundary() {
        Map<String, Object> config = validConfig();
        config.put("max_tokens", 256);

        assertDoesNotThrow(() -> CliAgentConfig.validate(config));
    }

    @Test
    void testValidateBadMaxIterations() {
        Map<String, Object> config = validConfig();
        config.put("max_iterations", 0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliAgentConfig.validate(config));

        assertTrue(error.getMessage().contains("max_iterations"));
    }

    @Test
    void testValidateWithServerUrl() {
        Map<String, Object> config = validConfig();
        config.put("api_key", "");
        config.put("server_url", "http://localhost:8080");

        assertDoesNotThrow(() -> CliAgentConfig.validate(config));
    }

    @Test
    void testValidateSuccess() {
        assertDoesNotThrow(() -> CliAgentConfig.validate(validConfig()));
    }

    @Test
    void testValidateErrorMentionsSettingsJson() {
        Map<String, Object> config = validConfig();
        config.put("api_key", "");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliAgentConfig.validate(config));

        assertTrue(error.getMessage().contains("settings.json"));
    }

    @Test
    void testMissingFile(@TempDir Path tempDir) {
        assertTrue(CliAgentConfig.loadSettingsJson(tempDir.resolve("nope.json")).isEmpty());
    }

    @Test
    void testValidFile(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"apiKey\": \"sk-test\", \"model\": \"gpt-4o\"}");

        Map<String, Object> result = CliAgentConfig.loadSettingsJson(settings);

        assertEquals(Map.of("apiKey", "sk-test", "model", "gpt-4o"), result);
    }

    @Test
    void testMalformedJson(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{invalid json");

        assertTrue(CliAgentConfig.loadSettingsJson(settings).isEmpty());
    }

    @Test
    void testNonDictJson(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "[\"a\", \"b\"]");

        assertTrue(CliAgentConfig.loadSettingsJson(settings).isEmpty());
    }

    @Test
    void testEmptyFile(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "");

        assertTrue(CliAgentConfig.loadSettingsJson(settings).isEmpty());
    }

    @Test
    void testCreatesNewFile(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("sub").resolve("settings.json");

        CliAgentConfig.saveSettingsJson(Map.of("apiKey", "sk-test"), settings);

        assertEquals("sk-test", CliAgentConfig.loadSettingsJson(settings).get("apiKey"));
    }

    @Test
    void testMergesExisting(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"model\": \"gpt-4o\", \"apiKey\": \"old\"}");

        CliAgentConfig.saveSettingsJson(Map.of("apiKey", "new"), settings);
        Map<String, Object> result = CliAgentConfig.loadSettingsJson(settings);

        assertEquals("new", result.get("apiKey"));
        assertEquals("gpt-4o", result.get("model"));
    }

    @Test
    void testReturnsPath(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");

        Path result = CliAgentConfig.saveSettingsJson(Map.of("a", 1), settings);

        assertEquals(settings, result);
    }

    @Test
    void testEnvOverride(@TempDir Path tempDir) {
        Map<String, Object> config = CliAgentConfig.loadConfig(
                Map.of(),
                Map.of(
                        "OPENJIUWEN_MODEL", "qwen-max",
                        "OPENJIUWEN_PROVIDER", "DashScope",
                        "OPENJIUWEN_API_KEY", "test-key"),
                tempDir.resolve("missing-settings.json"));

        assertEquals("qwen-max", config.get("model"));
        assertEquals("DashScope", config.get("provider"));
    }

    @Test
    void testCliArgsOverrideEnv(@TempDir Path tempDir) {
        Map<String, Object> config = CliAgentConfig.loadConfig(
                Map.of("model", "gpt-4o-mini"),
                Map.of(
                        "OPENJIUWEN_MODEL", "qwen-max",
                        "OPENJIUWEN_API_KEY", "test-key"),
                tempDir.resolve("missing-settings.json"));

        assertEquals("gpt-4o-mini", config.get("model"));
    }

    @Test
    void testLoadConfigValidates(@TempDir Path tempDir) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CliAgentConfig.loadConfig(Map.of(), Map.of(), tempDir.resolve("missing-settings.json")));

        assertTrue(error.getMessage().contains("API key"));
    }

    @Test
    void testSettingsJsonOverridesDefaults(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"apiKey\": \"from-json\", \"model\": \"qwen\"}");

        Map<String, Object> config = CliAgentConfig.loadConfig(Map.of(), Map.of(), settings);

        assertEquals("from-json", config.get("api_key"));
        assertEquals("qwen", config.get("model"));
    }

    @Test
    void testEnvOverridesSettingsJson(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"apiKey\": \"from-json\", \"model\": \"from-json\"}");

        Map<String, Object> config = CliAgentConfig.loadConfig(
                Map.of(),
                Map.of(
                        "OPENJIUWEN_API_KEY", "from-env",
                        "OPENJIUWEN_MODEL", "from-env"),
                settings);

        assertEquals("from-env", config.get("api_key"));
        assertEquals("from-env", config.get("model"));
    }

    @Test
    void testCliOverridesSettingsJson(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"apiKey\": \"from-json\"}");

        Map<String, Object> config = CliAgentConfig.loadConfig(
                Map.of("apiKey", "from-cli"),
                Map.of(),
                settings);

        assertEquals("from-cli", config.get("api_key"));
    }

    @Test
    void testSettingsJsonMaxTokens(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"apiKey\": \"k\", \"maxTokens\": 4096}");

        Map<String, Object> config = CliAgentConfig.loadConfig(Map.of(), Map.of(), settings);

        assertEquals(4096, config.get("max_tokens"));
    }

    @Test
    void testSettingsJsonMaxIterations(@TempDir Path tempDir) throws IOException {
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, "{\"apiKey\": \"k\", \"maxIterations\": 15}");

        Map<String, Object> config = CliAgentConfig.loadConfig(Map.of(), Map.of(), settings);

        assertEquals(15, config.get("max_iterations"));
    }

    private static Map<String, Object> validConfig() {
        Map<String, Object> config = new LinkedHashMap<>(CliAgentConfig.defaultConfig());
        config.put("api_key", "sk-test-key");
        return config;
    }
}
