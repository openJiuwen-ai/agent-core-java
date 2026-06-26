/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.experience;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.experience} in
 * {@code openjiuwen/auto_harness/experience/__init__.py}.
 */
class AutoHarnessExperiencePackageTest {

    @Test
    void exportsExperienceTypesInPythonOrder() {
        assertEquals("openjiuwen/auto_harness/experience/__init__.py", AutoHarnessExperiencePackage.PYTHON_MODULE);
        assertEquals(List.of("ActiveContextSynthesizer", "ExperienceStore"), AutoHarnessExperiencePackage.ALL);
        assertSame(ActiveContextSynthesizer.class, AutoHarnessExperiencePackage.ACTIVE_CONTEXT_SYNTHESIZER);
        assertSame(ExperienceStore.class, AutoHarnessExperiencePackage.EXPERIENCE_STORE);
    }
}
