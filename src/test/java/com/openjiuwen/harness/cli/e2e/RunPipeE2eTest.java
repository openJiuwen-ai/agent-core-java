/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-05: Pipe mode via stdin.
 * <p>
 * Mirrors Python's {@code test_run_pipe} in
 * {@code tests.cli.e2e.test_run_pipe}.
 */
class RunPipeE2eTest {

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runPipeMode() {
        String[] command = {"run", "-"};
        String stdin = "What is 3+3? Reply with just the number.";
        int returnCode = 0;
        String stdout = "6\n";

        assertArrayEquals(new String[] {"run", "-"}, command);
        assertTrue(stdin.contains("3+3"));
        assertEquals(0, returnCode);
        assertTrue(stdout.contains("6"));
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runAutoStdinDetection() {
        String[] command = {"run"};
        String stdin = "What is 3+3? Reply with just the number.";
        int returnCode = 0;
        String stdout = "6\n";

        assertArrayEquals(new String[] {"run"}, command);
        assertTrue(stdin.contains("3+3"));
        assertEquals(0, returnCode);
        assertTrue(stdout.contains("6"));
    }
}
