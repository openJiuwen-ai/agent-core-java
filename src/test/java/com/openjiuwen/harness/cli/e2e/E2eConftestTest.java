/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared constants and helpers for CLI E2E tests.
 * <p>
 * Mirrors Python's {@code conftest} in
 * {@code tests.cli.e2e.conftest}.
 */
class E2eConftestTest {

    private static final String API_KEY = System.getenv().getOrDefault("OPENJIUWEN_API_KEY", "");
    private static final String API_BASE = System.getenv().getOrDefault("OPENJIUWEN_API_BASE", "https://api.openai.com/v1");
    private static final String MODEL = System.getenv().getOrDefault("OPENJIUWEN_MODEL", "gpt-4o");
    private static final String PROVIDER = System.getenv().getOrDefault("OPENJIUWEN_PROVIDER", "OpenAI");
    private static final int LLM_TIMEOUT = 120;

    @Test
    void e2eEnvHasApiBase() {
        assertNotNull(API_BASE);
        assertFalse(API_BASE.isEmpty());
    }

    @Test
    void e2eEnvHasModel() {
        assertNotNull(MODEL);
        assertFalse(MODEL.isEmpty());
    }

    @Test
    void e2eEnvHasProvider() {
        assertNotNull(PROVIDER);
        assertFalse(PROVIDER.isEmpty());
    }

    @Test
    void llmTimeoutIsReasonable() {
        assertTrue(LLM_TIMEOUT > 0);
        assertTrue(LLM_TIMEOUT <= 300);
    }

    @Test
    void buildE2eEnvContainsRequiredKeys() {
        Map<String, String> env = buildE2eEnv();
        assertTrue(env.containsKey("OPENJIUWEN_API_KEY"));
        assertTrue(env.containsKey("OPENJIUWEN_API_BASE"));
        assertTrue(env.containsKey("OPENJIUWEN_MODEL"));
        assertTrue(env.containsKey("OPENJIUWEN_PROVIDER"));
    }

    @Test
    void projectRootIsResolvable() {
        String projectRoot = getProjectRoot();
        assertNotNull(projectRoot);
    }

    private Map<String, String> buildE2eEnv() {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("OPENJIUWEN_API_KEY", API_KEY);
        env.put("OPENJIUWEN_API_BASE", API_BASE);
        env.put("OPENJIUWEN_MODEL", MODEL);
        env.put("OPENJIUWEN_PROVIDER", PROVIDER);
        return env;
    }

    private String getProjectRoot() {
        return Paths.get("").toAbsolutePath().toString();
    }
}
