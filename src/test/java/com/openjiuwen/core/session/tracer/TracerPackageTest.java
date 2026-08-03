/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code openjiuwen.core.session.tracer} package exports in
 * {@code openjiuwen/core/session/tracer/__init__.py}.
 */
class TracerPackageTest {

    @Test
    void pythonModuleAndExportsMirrorTracerPackageInit() {
        assertEquals("openjiuwen/core/session/tracer/__init__.py", TracerPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "Tracer",
                "TracerWorkflowUtils",
                "decorate_model_with_trace",
                "decorate_tool_with_trace",
                "decorate_workflow_with_trace"
        ), TracerPackage.EXPORTED_SYMBOLS);

        assertSame(Tracer.class, TracerPackage.EXPORTED_TYPES.get("Tracer"));
        assertSame(TracerWorkflowUtils.class, TracerPackage.EXPORTED_TYPES.get("TracerWorkflowUtils"));
        assertSame(TracerDecorator.class, TracerPackage.EXPORTED_TYPES.get("decorate_model_with_trace"));
        assertSame(TracerDecorator.class, TracerPackage.EXPORTED_TYPES.get("decorate_tool_with_trace"));
        assertSame(TracerDecorator.class, TracerPackage.EXPORTED_TYPES.get("decorate_workflow_with_trace"));
    }

    @Test
    void exportedTypesAreImmutableLikePythonAllTupleSurface() {
        assertThrows(UnsupportedOperationException.class, () ->
                TracerPackage.EXPORTED_TYPES.put("unexpected", TracerPackageTest.class));
    }
}
