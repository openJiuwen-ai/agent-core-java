/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.launcher} package facade in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/__init__.py}.
 */
class OnlineLauncherPackageTest {

    @Test
    void exposesPythonModulePathAndAllSymbolsInOrder() {
        assertEquals(
                "openjiuwen/agent_evolving/agent_rl/online/launcher/__init__.py",
                OnlineLauncherPackage.PYTHON_MODULE
        );
        assertEquals(List.of("LauncherPaths", "run_online_rl_loop"), OnlineLauncherPackage.all());
        assertTrue(OnlineLauncherPackage.exports("LauncherPaths"));
        assertTrue(OnlineLauncherPackage.exports("run_online_rl_loop"));
        assertFalse(OnlineLauncherPackage.exports("missing"));
    }

    @Test
    void resolvesExportedTypeAndFunctionOwner() {
        assertSame(LauncherRunner.LauncherPaths.class, OnlineLauncherPackage.getAttribute("LauncherPaths"));
        assertSame(LauncherRunner.class, OnlineLauncherPackage.getAttribute("run_online_rl_loop"));
        assertEquals(null, OnlineLauncherPackage.typeFor("missing"));
    }

    @Test
    void unknownAttributeUsesPythonModuleErrorShape() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OnlineLauncherPackage.getAttribute("missing")
        );
        assertEquals(
                "module 'openjiuwen.agent_evolving.agent_rl.online.launcher' has no attribute 'missing'",
                exception.getMessage()
        );
    }
}
