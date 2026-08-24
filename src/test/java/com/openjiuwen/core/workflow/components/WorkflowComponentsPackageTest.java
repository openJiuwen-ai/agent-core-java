/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components;

import com.openjiuwen.core.session.NodeSessionApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the workflow components package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components} package facade in
 * {@code openjiuwen/core/workflow/components/__init__.py}.</p>
 */
class WorkflowComponentsPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        assertEquals("openjiuwen/core/workflow/components/__init__.py",
                WorkflowComponentsPackage.PYTHON_MODULE);
        assertIterableEquals(List.of("Session"), WorkflowComponentsPackage.all());
        assertSame(WorkflowComponentsPackage.EXPORTED_SYMBOLS, WorkflowComponentsPackage.all());
    }

    @Test
    void resolvesSessionExportSourceAndJavaType() {
        assertTrue(WorkflowComponentsPackage.exports("Session"));
        assertFalse(WorkflowComponentsPackage.exports("Workflow"));
        assertEquals("openjiuwen.core.session.node.Session", WorkflowComponentsPackage.sourceFor("Session"));
        assertSame(NodeSessionApi.class, WorkflowComponentsPackage.javaTypeFor("Session"));
    }
}
