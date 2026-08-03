/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen/harness/tools/browser_move/__init__.py}.
 */
class BrowserMovePackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/harness/tools/browser_move/__init__.py", BrowserMovePackage.PYTHON_MODULE);
    }

    @Test
    void resolvesProjectRootFromPackageLocation() {
        assertTrue(Files.exists(BrowserMovePackage.REPO_ROOT.resolve("pom.xml")));
        assertTrue(Files.exists(BrowserMovePackage.REPO_ROOT.resolve("src/main/java/com/openjiuwen")));
    }
}
