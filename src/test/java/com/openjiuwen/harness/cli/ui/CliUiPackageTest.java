/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.harness.cli.ui} facade in
 * {@code openjiuwen/harness/cli/ui/__init__.py}.
 */
class CliUiPackageTest {
    @Test
    void exposesPythonModulePathAndAllSymbols() {
        assertEquals("openjiuwen/harness/cli/ui/__init__.py", CliUiPackage.PYTHON_MODULE);
        assertEquals(List.of("run_repl", "render_stream", "run_once"), CliUiPackage.all());
        assertTrue(CliUiPackage.exports("run_repl"));
        assertTrue(CliUiPackage.exports("render_stream"));
        assertTrue(CliUiPackage.exports("run_once"));
        assertFalse(CliUiPackage.exports("missing"));
    }

    @Test
    void resolvesExportedOwnerTypes() {
        assertSame(CliRepl.class, CliUiPackage.RUN_REPL_OWNER);
        assertSame(CliRenderer.class, CliUiPackage.RENDER_STREAM_OWNER);
        assertSame(CliRunner.class, CliUiPackage.RUN_ONCE_OWNER);
        assertSame(CliRepl.class, CliUiPackage.typeFor("run_repl"));
        assertSame(CliRenderer.class, CliUiPackage.typeFor("render_stream"));
        assertSame(CliRunner.class, CliUiPackage.typeFor("run_once"));
        assertEquals(null, CliUiPackage.typeFor("missing"));
    }
}
