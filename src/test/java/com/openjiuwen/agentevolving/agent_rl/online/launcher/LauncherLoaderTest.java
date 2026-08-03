/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherLoaderTest {

    @Test
    void loadRuntimeConfigMergesNestedYamlAndCliOverrides(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("online.yaml");
        Files.writeString(config, """
                inference:
                  model_path: /models/base
                  model_name: BaseModel
                  existing_url: http://inference.local
                judge:
                  existing_url: http://judge.local
                gateway:
                  port: 19003
                  redis_url: redis://localhost:6379/0
                jiuwen:
                  enabled: false
                training:
                  threshold: 8
                """);
        Map<String, Object> cliOverrides = new LinkedHashMap<>();
        setNestedValue(cliOverrides, "training.threshold", 9);
        setNestedValue(cliOverrides, "gateway.record_dir", "cli-records");

        LauncherLoader.RuntimeConfigResult result = LauncherLoader.loadRuntimeConfig(config.toString(), cliOverrides);

        @SuppressWarnings("unchecked")
        Map<String, Object> training = (Map<String, Object>) result.config().get("training");
        @SuppressWarnings("unchecked")
        Map<String, Object> gateway = (Map<String, Object>) result.config().get("gateway");
        OnlineRLConfig validated = result.validatedConfig();
        assertEquals(config.toAbsolutePath().normalize(), result.resolvedPath());
        assertEquals(9, ((Number) training.get("threshold")).intValue());
        assertEquals("cli-records", gateway.get("record_dir"));
        assertEquals("/models/base", validated.getInference().getModelPath());
        assertEquals("BaseModel", validated.getInference().getModelName());
        assertEquals("redis://localhost:6379/0", validated.getGateway().getRedisUrl());
    }

    @Test
    void loadConfigRaisesWhenConfigFileDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.yaml");

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> LauncherLoader.loadConfig(missing.toString(), validCliOverrides())
        );

        assertTrue(exception.getMessage().contains("Config file not found"));
    }

    @Test
    void loadConfigValidatesRequiredRuntimeFields() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        setNestedValue(overrides, "inference.existing_url", "http://inference.local");
        setNestedValue(overrides, "judge.existing_url", "http://judge.local");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LauncherLoader.loadConfig(null, overrides)
        );

        assertTrue(exception.getMessage().contains("gateway.port is required"));
    }

    private static Map<String, Object> validCliOverrides() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        setNestedValue(overrides, "inference.existing_url", "http://inference.local");
        setNestedValue(overrides, "judge.existing_url", "http://judge.local");
        setNestedValue(overrides, "gateway.port", 19003);
        setNestedValue(overrides, "gateway.redis_url", "redis://localhost:6379/0");
        setNestedValue(overrides, "jiuwen.enabled", false);
        return overrides;
    }

    @SuppressWarnings("unchecked")
    private static void setNestedValue(Map<String, Object> root, String dottedPath, Object value) {
        String[] segments = dottedPath.split("\\.");
        Map<String, Object> current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            current = (Map<String, Object>) current.computeIfAbsent(segments[index], ignored -> new LinkedHashMap<>());
        }
        current.put(segments[segments.length - 1], value);
    }
}
