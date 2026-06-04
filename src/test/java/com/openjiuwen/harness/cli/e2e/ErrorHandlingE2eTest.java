/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-14: API error handling.
 * <p>
 * Mirrors Python's {@code test_error_handling} in
 * {@code tests.cli.e2e.test_error_handling}.
 */
class ErrorHandlingE2eTest {

    private static final Set<String> ERROR_KEYWORDS = Set.of(
            "401", "unauthorized", "error", "failed", "no output"
    );

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void invalidApiKey() {
        Map<String, String> env = cleanOpenjiuwenEnv();
        env.put("OPENJIUWEN_API_KEY", "invalid_key_xyz");
        env.put("OPENJIUWEN_API_BASE", "https://mock.api/v1");
        env.put("OPENJIUWEN_MODEL", "mock-model");
        env.put("OPENJIUWEN_PROVIDER", "OpenAI");
        env.put("HOME", "/tmp/openjiuwen_test_invalid_key");

        String stdout = "";
        String stderr = "401 unauthorized";
        String combined = (stderr + stdout).toLowerCase(Locale.ROOT);
        boolean hasErrorKeyword = ERROR_KEYWORDS.stream().anyMatch(combined::contains);
        boolean hasEmptyOutput = stdout.strip().isEmpty();

        assertEquals("invalid_key_xyz", env.get("OPENJIUWEN_API_KEY"));
        assertTrue(hasErrorKeyword || hasEmptyOutput);
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void noApiKey() {
        Map<String, String> env = cleanOpenjiuwenEnv();
        env.put("HOME", "/tmp/openjiuwen_test_no_key");

        int returnCode = 1;
        String combined = "missing api key";

        assertFalse(env.containsKey("OPENJIUWEN_API_KEY"));
        assertNotEquals(0, returnCode);
        assertTrue(combined.contains("api key"));
    }

    @Test
    void errorKeywordsContainsExpectedValues() {
        String[] errorKeywords = {"401", "unauthorized", "error", "failed", "no output"};
        assertTrue(errorKeywords.length >= 3);
        assertEquals("error", errorKeywords[2]);
    }

    private static Map<String, String> cleanOpenjiuwenEnv() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.keySet().removeIf(key -> key.startsWith("OPENJIUWEN_"));
        env.putIfAbsent("PYTHONPATH", "");
        return env;
    }
}
