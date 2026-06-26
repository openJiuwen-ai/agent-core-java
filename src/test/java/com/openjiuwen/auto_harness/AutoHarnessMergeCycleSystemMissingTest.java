/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestL4ActivateReplay} in
 * {@code tests/system_tests/auto_harness/test_e2e_merge_cycle.py}.</p>
 */
class AutoHarnessMergeCycleSystemMissingTest {
    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: Missing product: "
            + "C:\\Users\\Administrator\\Downloads\\auto-harness\\runtime_extensions\\799685328dc4"
            + "\\finance_excel_processor";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testMultiDesignMergeActivateQuery() {
        // Python skips this L4 replay when the required local runtime extension product is absent.
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testSingleDesignActivateQuery() {
        // Python skips this L4 replay when the required local runtime extension product is absent.
    }
}
