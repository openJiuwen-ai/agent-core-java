/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.dreaming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's export checks in
 * {@code openjiuwen/core/memory/dreaming/__init__.py}.
 */
class MemoryDreamingPackageTest {

    @Test
    void exportsDreamingOrchestrator() {
        assertEquals("openjiuwen/core/memory/dreaming/__init__.py", MemoryDreamingPackage.PYTHON_MODULE);
        assertEquals(DreamingOrchestrator.class, MemoryDreamingPackage.DREAMING_ORCHESTRATOR);
    }
}
