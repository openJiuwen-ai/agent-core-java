/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code __all__} facade in
 * {@code openjiuwen/harness/cli/rails/__init__.py}.
 */
class HarnessCliRailsPackageTest {

    @Test
    void exposesPythonModulePathAndAllSymbols() {
        assertEquals("openjiuwen/harness/cli/rails/__init__.py", HarnessCliRailsPackage.PYTHON_MODULE);
        assertEquals(List.of("TokenTrackingRail", "ToolTrackingRail"), HarnessCliRailsPackage.all());
        assertTrue(HarnessCliRailsPackage.exports("TokenTrackingRail"));
        assertTrue(HarnessCliRailsPackage.exports("ToolTrackingRail"));
        assertFalse(HarnessCliRailsPackage.exports("AgentRail"));
    }

    @Test
    void resolvesExportedRailTypes() {
        assertSame(TokenTrackingRail.class, HarnessCliRailsPackage.TOKEN_TRACKING_RAIL);
        assertSame(ToolTrackingRail.class, HarnessCliRailsPackage.TOOL_TRACKING_RAIL);
        assertSame(TokenTrackingRail.class, HarnessCliRailsPackage.typeFor("TokenTrackingRail"));
        assertSame(ToolTrackingRail.class, HarnessCliRailsPackage.typeFor("ToolTrackingRail"));
        assertEquals(null, HarnessCliRailsPackage.typeFor("missing"));
    }
}
