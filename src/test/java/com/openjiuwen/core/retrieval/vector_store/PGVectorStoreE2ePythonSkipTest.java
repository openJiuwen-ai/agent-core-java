/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.vector_store.test_pg_e2e} in
 * {@code tests/unit_tests/core/retrieval/vector_store/test_pg_e2e.py}.</p>
 */
class PGVectorStoreE2ePythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: PGVector not installed";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void pgVectorE2eRequiresPgvector() {
    }
}
