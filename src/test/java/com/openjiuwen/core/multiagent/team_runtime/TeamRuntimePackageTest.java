/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the multi-agent team runtime package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.multi_agent.team_runtime} in
 * {@code openjiuwen/core/multi_agent/team_runtime/__init__.py}.</p>
 */
class TeamRuntimePackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/core/multi_agent/team_runtime/__init__.py", TeamRuntimePackage.PYTHON_MODULE);
        assertEquals(List.of(
                "MessageEnvelope",
                "MessageBus",
                "MessageBusConfig",
                "TeamRuntime",
                "RuntimeConfig",
                "CommunicableAgent"
        ), TeamRuntimePackage.all());
    }

    @Test
    void resolvesLazyExportsToTranslatedTypes() {
        assertTrue(TeamRuntimePackage.exports("MessageEnvelope"));
        assertSame(MessageEnvelope.class, TeamRuntimePackage.resolveType("MessageEnvelope"));
        assertSame(MessageBus.class, TeamRuntimePackage.resolveType("MessageBus"));
        assertSame(MessageBusConfig.class, TeamRuntimePackage.resolveType("MessageBusConfig"));
        assertSame(TeamRuntime.class, TeamRuntimePackage.resolveType("TeamRuntime"));
        assertSame(RuntimeConfig.class, TeamRuntimePackage.resolveType("RuntimeConfig"));
        assertSame(CommunicableAgent.class, TeamRuntimePackage.resolveType("CommunicableAgent"));
        assertEquals(
                "openjiuwen.core.multi_agent.team_runtime.team_runtime.TeamRuntime",
                TeamRuntimePackage.sourceFor("TeamRuntime")
        );
    }

    @Test
    void unknownSymbolMatchesPythonAttributeErrorMessage() {
        assertFalse(TeamRuntimePackage.exports("Missing"));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TeamRuntimePackage.resolveType("Missing")
        );
        assertEquals(
                "module 'openjiuwen.core.multi_agent.team_runtime' has no attribute 'Missing'",
                exception.getMessage()
        );
    }
}
