/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react} module in
 * {@code openjiuwen/core/workflow/components/llm/react/__init__.py}.
 */
class ReactPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/core/workflow/components/llm/react/__init__.py", ReactPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "ReActAgentComp",
                "ReActAgentCompConfig",
                "ReActAgentCompExecutable"
        ), ReactPackage.all());
        assertSame(ReactPackage.EXPORTED_SYMBOLS, ReactPackage.all());
    }

    @Test
    void resolvesExportedReActComponentTypes() {
        assertSame(ReActAgentComp.class, ReactPackage.typeFor("ReActAgentComp"));
        assertSame(ReActAgentCompConfig.class, ReactPackage.typeFor("ReActAgentCompConfig"));
        assertSame(ReActAgentCompExecutable.class, ReactPackage.typeFor("ReActAgentCompExecutable"));
        assertTrue(ReactPackage.exports("ReActAgentComp"));
        assertFalse(ReactPackage.exports("missing"));
    }

    @Test
    @Disabled("Skipped in Python source: skip system test")
    void reactAgentInWorkflowSkippedInPythonSource() {
    }

    @Test
    @Disabled("Skipped in Python source: skip system test")
    void reactAgentWithAddToolInWorkflowSkippedInPythonSource() {
    }

    @Test
    @Disabled("Skipped in Python source: skip system test")
    void reactAgentStreamWithAddToolInWorkflowSkippedInPythonSource() {
    }

    @Test
    @Disabled("Skipped in Python source: skip system test")
    void reactAgentCompStreamSkippedInPythonSource() {
    }
}
