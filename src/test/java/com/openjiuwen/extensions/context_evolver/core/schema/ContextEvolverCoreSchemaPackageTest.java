/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextEvolverCoreSchemaPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/core/schema/__init__.py",
                ContextEvolverCoreSchemaPackage.PYTHON_MODULE
        );
        assertEquals(Message.class, ContextEvolverCoreSchemaPackage.MESSAGE);
        assertEquals(Role.class, ContextEvolverCoreSchemaPackage.ROLE);
        assertEquals(VectorNode.class, ContextEvolverCoreSchemaPackage.VECTOR_NODE);
    }
}
