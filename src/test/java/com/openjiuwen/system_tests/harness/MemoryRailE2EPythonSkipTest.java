/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_memory_rail_e2e} module in
 * {@code tests/system_tests/harness/test_memory_rail_e2e.py}.</p>
 */
class MemoryRailE2EPythonSkipTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: need llm and embedding";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void memoryRailBasicInvoke() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void writeMemoryTool() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void memorySearchTool() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void readMemoryTool() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void editMemoryTool() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void memoryGetTool() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void writeMemoryAppendMode() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void updateUserProfile() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void writeMemoryMdFile() {
    }
}
