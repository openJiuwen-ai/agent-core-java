/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.ace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.ace} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/ace/__init__.py}.
 */
class AceRetrieveTaskPackageTest {

    @Test
    void exposesPythonModulePath() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/retrieve/task/ace/__init__.py",
                AceRetrieveTaskPackage.PYTHON_MODULE
        );
    }

    @Test
    void exposesRecallMemoryOpExport() {
        assertSame(RecallMemoryOp.class, AceRetrieveTaskPackage.RECALL_MEMORY_OP);
        assertEquals(List.of("RecallMemoryOp"), AceRetrieveTaskPackage.EXPORTED_SYMBOLS);
    }
}
