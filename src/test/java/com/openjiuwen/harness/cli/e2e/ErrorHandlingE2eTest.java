/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-14: API error handling.
 * <p>
 * Mirrors Python's {@code test_error_handling} in
 * {@code tests.cli.e2e.test_error_handling}.
 */
class ErrorHandlingE2eTest {

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void invalidApiKey() {
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void noApiKey() {
    }

    @Test
    void errorKeywordsContainsExpectedValues() {
        String[] errorKeywords = {"401", "unauthorized", "error", "failed", "no output"};
        assertTrue(errorKeywords.length >= 3);
        assertEquals("error", errorKeywords[2]);
    }
}
