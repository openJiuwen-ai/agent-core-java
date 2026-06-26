/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.tools} in
 * {@code openjiuwen/auto_harness/tools/__init__.py}.
 */
class AutoHarnessToolsPackageTest {

    @Test
    void exportsExperienceSearchTool() {
        assertEquals("openjiuwen/auto_harness/tools/__init__.py", AutoHarnessToolsPackage.PYTHON_MODULE);
        assertEquals(List.of("ExperienceSearchTool"), AutoHarnessToolsPackage.ALL);
        assertSame(ExperienceSearchTool.class, AutoHarnessToolsPackage.EXPERIENCE_SEARCH_TOOL);
    }
}
