/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestAsyncSqlDbStore.test_basic} in
 * {@code tests/unit_tests/core/memory/test_sql_db_store.py}.</p>
 */
class SqlDbStorePythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: need aiosqlite";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void basicSqlDbStoreRequiresAiosqlite() {
    }
}
