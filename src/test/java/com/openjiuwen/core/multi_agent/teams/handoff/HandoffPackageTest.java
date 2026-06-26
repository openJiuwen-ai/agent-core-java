/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.core.multi_agent.teams.handoff} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/__init__.py}.
 */
class HandoffPackageTest {

    @Test
    void exportsHandoffSymbolsInPythonAllOrder() {
        assertEquals("openjiuwen/core/multi_agent/teams/handoff/__init__.py", HandoffPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "HandoffTeam",
                "HandoffOrchestrator",
                "TeamInterruptSignal",
                "HandoffConfig",
                "HandoffTeamConfig",
                "HandoffRoute",
                "HandoffSignal",
                "extract_handoff_signal",
                "HANDOFF_TARGET_KEY",
                "HANDOFF_MESSAGE_KEY",
                "HANDOFF_REASON_KEY"
        ), HandoffPackage.all());
        assertTrue(HandoffPackage.exports("extract_handoff_signal"));
        assertTrue(HandoffPackage.exports("HANDOFF_TARGET_KEY"));
        assertSame(HandoffTeam.class, HandoffPackage.typeFor("HandoffTeam"));
        assertSame(HandoffOrchestrator.class, HandoffPackage.typeFor("HandoffOrchestrator"));
        assertSame(TeamInterruptSignal.class, HandoffPackage.typeFor("TeamInterruptSignal"));
        assertSame(HandoffConfig.class, HandoffPackage.typeFor("HandoffConfig"));
        assertSame(HandoffTeamConfig.class, HandoffPackage.typeFor("HandoffTeamConfig"));
        assertSame(HandoffRoute.class, HandoffPackage.typeFor("HandoffRoute"));
        assertSame(HandoffSignal.class, HandoffPackage.typeFor("HandoffSignal"));
        assertEquals("__handoff_to__", HandoffSignal.HANDOFF_TARGET_KEY);
        assertEquals("__handoff_message__", HandoffSignal.HANDOFF_MESSAGE_KEY);
        assertEquals("__handoff_reason__", HandoffSignal.HANDOFF_REASON_KEY);
    }
}
