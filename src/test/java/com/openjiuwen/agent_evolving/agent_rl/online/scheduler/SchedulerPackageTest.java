/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.scheduler} package facade in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/__init__.py}.
 */
class SchedulerPackageTest {

    @Test
    void exportedSymbolsMirrorPythonAllOrder() {
        assertEquals(
                "openjiuwen/agent_evolving/agent_rl/online/scheduler/__init__.py",
                SchedulerPackage.PYTHON_MODULE
        );
        assertEquals(List.of(
                "OnlineTrainingScheduler",
                "PPOTrainingExecutor"
        ), SchedulerPackage.all());
        assertSame(SchedulerPackage.EXPORTED_SYMBOLS, SchedulerPackage.all());
    }

    @Test
    void resolvesExportedTypes() {
        assertSame(OnlineTrainingScheduler.class, SchedulerPackage.typeFor("OnlineTrainingScheduler"));
        assertSame(PpoTrainingExecutor.class, SchedulerPackage.typeFor("PPOTrainingExecutor"));
        assertTrue(SchedulerPackage.exports("OnlineTrainingScheduler"));
        assertTrue(SchedulerPackage.exports("PPOTrainingExecutor"));
        assertFalse(SchedulerPackage.exports("PpoTrainingExecutor"));
        assertFalse(SchedulerPackage.exports("missing"));
    }
}
