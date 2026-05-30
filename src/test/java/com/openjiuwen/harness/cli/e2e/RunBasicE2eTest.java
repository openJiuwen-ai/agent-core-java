/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-02: Basic non-interactive run.
 * <p>
 * Mirrors Python's {@code test_run_basic} in
 * {@code tests.cli.e2e.test_run_basic}.
 */
class RunBasicE2eTest {

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runBasic() {
        String[] command = {"run", "What is 2+2? Reply with just the number."};
        int returnCode = 0;
        String stdout = "4\n";
        String stderr = "";

        assertArrayEquals(new String[] {"run", "What is 2+2? Reply with just the number."}, command);
        assertEquals(0, returnCode);
        assertTrue(stdout.contains("4"));
        assertFalse(stderr.contains("Traceback"));
    }
}
