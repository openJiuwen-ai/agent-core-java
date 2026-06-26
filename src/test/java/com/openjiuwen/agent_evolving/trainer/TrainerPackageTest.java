/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's trainer package exports in
 * {@code openjiuwen/agent_evolving/trainer/__init__.py}.
 */
class TrainerPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/agent_evolving/trainer/__init__.py", TrainerPackage.PYTHON_MODULE);
        assertEquals(List.of("Trainer", "Progress", "Callbacks"), TrainerPackage.EXPORTED_SYMBOLS);
    }

    @Test
    void exportedTypesAreAvailable() {
        assertNotNull(Trainer.class);
        assertNotNull(Progress.class);
        assertNotNull(Callbacks.class);
    }
}
