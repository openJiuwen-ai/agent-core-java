/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's package surface in
 * {@code openjiuwen/core/operator/memory_call/__init__.py}.
 */
class MemoryCallPackageTest {

    @Test
    void exposesPythonPackageBridge() {
        assertEquals(
                "openjiuwen/core/operator/memory_call/__init__.py",
                MemoryCallPackage.PYTHON_MODULE
        );
        assertEquals(
                "Memory invocation operator: MemoryCallOperator with enabled/retries tunables.",
                MemoryCallPackage.DESCRIPTION
        );
        assertEquals(List.of("MemoryCallOperator"), MemoryCallPackage.EXPORTED_SYMBOLS);
        assertSame(MemoryCallOperator.class, MemoryCallPackage.MEMORY_CALL_OPERATOR);
    }
}
