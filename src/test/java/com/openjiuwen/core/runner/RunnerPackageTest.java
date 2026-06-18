/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the core runner package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.runner} package facade in
 * {@code openjiuwen/core/runner/__init__.py}.</p>
 */
class RunnerPackageTest {

    @Test
    void exportedSymbolsMirrorPythonGetattr() {
        assertEquals("openjiuwen/core/runner/__init__.py", RunnerPackage.PYTHON_MODULE);
        assertEquals(List.of("Runner"), RunnerPackage.all());
        assertTrue(RunnerPackage.exports("Runner"));
        assertFalse(RunnerPackage.exports("Missing"));
    }

    @Test
    void sourceAndJavaNamesPreserveLazyImportTarget() {
        assertEquals("openjiuwen.core.runner.runner.Runner", RunnerPackage.sourceFor("Runner"));
        assertEquals("com.openjiuwen.core.runner.Runner", RunnerPackage.javaSymbolNameFor("Runner"));
        assertNull(RunnerPackage.sourceFor("Missing"));
        assertNull(RunnerPackage.javaSymbolNameFor("Missing"));
    }

    @Test
    void resolveTypeIsDeferredUntilRunnerClassExists() {
        RunnerPackage.resolveType("Runner").ifPresent(type ->
                assertEquals("com.openjiuwen.core.runner.Runner", type.getName()));
        assertTrue(RunnerPackage.resolveType("Missing").isEmpty());
    }

    @Test
    void getAttrRejectsUnknownAttributeLikePython() {
        NoSuchElementException error = assertThrows(
                NoSuchElementException.class,
                () -> RunnerPackage.getAttr("Missing")
        );
        assertTrue(error.getMessage().contains("has no attribute 'Missing'"));
    }
}
