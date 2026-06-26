/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.extensions.checkpointer} collection skip in
 * {@code tests/unit_tests/extensions/checkpointer/conftest.py}.</p>
 */
class RedisCheckpointerCollectionPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: need redis local environment to run";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void checkpointerCollectionSkippedByConftest() {
    }
}
