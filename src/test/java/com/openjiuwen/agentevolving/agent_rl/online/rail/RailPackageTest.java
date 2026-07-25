/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.rail} package facade in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/__init__.py}.
 */
class RailPackageTest {

    @Test
    void exportedSymbolsMirrorPythonAllOrder() {
        assertEquals("openjiuwen/agent_evolving/agent_rl/online/rail/__init__.py", RailPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "OnlineTrajectoryConverter",
                "PerTurnSample",
                "RailV1Batch",
                "RLOnlineRail",
                "TrajectoryMeta",
                "TrajectoryUploader",
                "build_rl_online_rail_from_env",
                "is_rl_online_rail_enabled_from_env"
        ), RailPackage.all());
        assertSame(RailPackage.EXPORTED_SYMBOLS, RailPackage.all());
    }

    @Test
    void resolvesExportedTypesAndLeavesFactoryFunctionsUntyped() {
        assertSame(OnlineTrajectoryConverter.class, RailPackage.typeFor("OnlineTrajectoryConverter"));
        assertSame(PerTurnSample.class, RailPackage.typeFor("PerTurnSample"));
        assertSame(RailV1Batch.class, RailPackage.typeFor("RailV1Batch"));
        assertSame(RLOnlineRail.class, RailPackage.typeFor("RLOnlineRail"));
        assertSame(TrajectoryMeta.class, RailPackage.typeFor("TrajectoryMeta"));
        assertSame(TrajectoryUploader.class, RailPackage.typeFor("TrajectoryUploader"));
        assertTrue(RailPackage.exports("build_rl_online_rail_from_env"));
        assertTrue(RailPackage.exports("is_rl_online_rail_enabled_from_env"));
        assertFalse(RailPackage.exports("missing"));
    }
}
