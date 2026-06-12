/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package export surface in
 * {@code openjiuwen/extensions/context_evolver/core/op/__init__.py}.
 */
class OpPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/extensions/context_evolver/core/op/__init__.py", OpPackage.PYTHON_MODULE);
        assertEquals(List.of("BaseOp", "SequentialOp", "ParallelOp"), OpPackage.EXPORTED_SYMBOLS);
        assertEquals(BaseOp.class, OpPackage.BASE_OP);
        assertEquals(SequentialOp.class, OpPackage.SEQUENTIAL_OP);
        assertEquals(ParallelOp.class, OpPackage.PARALLEL_OP);
        assertEquals(BaseOp.class, OpPackage.EXPORTED_TYPES.get("BaseOp"));
        assertEquals(SequentialOp.class, OpPackage.EXPORTED_TYPES.get("SequentialOp"));
        assertEquals(ParallelOp.class, OpPackage.EXPORTED_TYPES.get("ParallelOp"));
    }
}
