/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.memory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestLongTermMemory} in
 * {@code tests/system_tests/memory/test_long_term_memory.py}.</p>
 */
class LongTermMemorySystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: skip system test";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void changeMemoryInstruction() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void memorySample() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void scopeConfigWorkflow() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void timestampInQueryResults() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void updateMemById() {
    }
}
