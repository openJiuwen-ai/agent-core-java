/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package export contract in
 * {@code openjiuwen/harness/tools/browser_move/utils/__init__.py}.
 */
class BrowserMoveUtilsPackageTest {

    @Test
    void exposesExactPythonModulePathAndConstants() {
        assertEquals("openjiuwen/harness/tools/browser_move/utils/__init__.py", BrowserMoveUtilsPackage.PYTHON_MODULE);
        assertEquals(BrowserMoveEnv.DEFAULT_MODEL_NAME, BrowserMoveUtilsPackage.DEFAULT_MODEL_NAME);
        assertEquals(BrowserMoveEnv.DEFAULT_BROWSER_TIMEOUT_S, BrowserMoveUtilsPackage.DEFAULT_BROWSER_TIMEOUT_S);
        assertEquals(BrowserMoveEnv.MISSING_API_KEY_MESSAGE, BrowserMoveUtilsPackage.MISSING_API_KEY_MESSAGE);
    }

    @Test
    void delegatesParsingAndCommandHelpers() {
        assertEquals(
                Map.of("ok", true),
                BrowserMoveUtilsPackage.extractJsonObject("```json\n{\"ok\": true}\n```")
        );
        assertEquals(List.of("npx", "-y", "@playwright/mcp@latest"), BrowserMoveUtilsPackage.parseCommandArgs(
                "npx -y @playwright/mcp@latest"
        ));
    }

    @Test
    void delegatesEnvHelpers() {
        assertEquals(BrowserMoveEnv.resolveModelName(), BrowserMoveUtilsPackage.resolveModelName());
        assertEquals(BrowserMoveEnv.resolveBrowserTimeoutS(), BrowserMoveUtilsPackage.resolveBrowserTimeoutS());
        assertFalse(BrowserMoveUtilsPackage.loadRepoDotenv());
        assertTrue(BrowserMoveUtilsPackage.resolveIntEnv(7, 1, "MISSING_TEST_ENV") >= 1);
    }
}
