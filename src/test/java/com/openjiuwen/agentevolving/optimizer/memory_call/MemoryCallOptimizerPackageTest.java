/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.memory_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/agent_evolving/optimizer/memory_call/__init__.py}.
 */
class MemoryCallOptimizerPackageTest {

    @Test
    void exposesPythonAllSymbols() {
        assertEquals(
                "openjiuwen/agent_evolving/optimizer/memory_call/__init__.py",
                MemoryCallOptimizerPackage.PYTHON_MODULE);
        assertSame(MemoryOptimizerBase.class, MemoryCallOptimizerPackage.MEMORY_OPTIMIZER_BASE);
        assertEquals(List.of("MemoryOptimizerBase"), MemoryCallOptimizerPackage.EXPORTED_SYMBOLS);
    }
}
