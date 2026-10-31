/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tests for repo {@code .env} loading in the browser runtime.
 *
 * <p>{@link BrowserMoveEnv#loadRepoDotenv(boolean)} stores entries as system
 * properties (the JVM cannot mutate the process environment), so it must not
 * dereference a missing {@code System.getenv} value, and the runtime settings
 * must read the property fallback. Python's {@code load_dotenv} writes
 * {@code os.environ}, which every {@code os.getenv} caller sees.</p>
 */
class BrowserMoveDotenvTest {

    @TempDir
    private Path tempDir;

    @Test
    void loadDotenvToleratesKeysMissingFromProcessEnv() throws Exception {
        String key = "JIUWENSWARM_DOTENV_PROBE";
        assumeTrue(System.getenv(key) == null, "process environment already defines the probe key");
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, key + "=probe-value\n");
        try {
            assertTrue(BrowserMoveEnv.loadDotenvFrom(dotenv, false));
            assertEquals("probe-value", System.getProperty(key));
            assertEquals("probe-value", BrowserMoveEnv.firstNonEmptyEnv(key));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void loadDotenvReturnsFalseForMissingFile() {
        assertFalse(BrowserMoveEnv.loadDotenvFrom(tempDir.resolve(".env"), false));
    }

    @Test
    void buildRuntimeSettingsReadsDotenvProperties() {
        assumeTrue(System.getenv("MODEL_NAME") == null, "process environment already defines MODEL_NAME");
        System.setProperty("MODEL_NAME", "dotenv-probe-model");
        try {
            RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of());
            assertEquals("dotenv-probe-model", settings.getModelName());
        } finally {
            System.clearProperty("MODEL_NAME");
        }
    }
}
