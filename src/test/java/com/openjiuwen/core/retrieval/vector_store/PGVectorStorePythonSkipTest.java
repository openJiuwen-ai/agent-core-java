/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestPGVectorStore} in
 * {@code tests/unit_tests/core/retrieval/vector_store/test_pg_store.py}.
 */
class PGVectorStorePythonSkipTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: PGVector not installed";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void pgVectorStoreModuleSkippedWhenPgvectorUnavailable() {
    }
}
