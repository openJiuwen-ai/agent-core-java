/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the single-agent package bridge.
 *
 * <p>Mirrors Python's {@code __all__} and lazy export table in
 * {@code openjiuwen/core/single_agent/__init__.py}.</p>
 */
class SingleAgentPackageTest {

    @Test
    void exportsMirrorPythonAllOrder() {
        assertEquals("openjiuwen/core/single_agent/__init__.py", SingleAgentPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "AgentCard",
                "ReActAgent",
                "ReActAgentConfig",
                "ReActAgentEvolve",
                "Session",
                "create_agent_session",
                "BaseAgent",
                "AbilityManager",
                "LegacyBaseAgent",
                "AddAbilityResult"
        ), SingleAgentPackage.exports());
    }

    @Test
    void exportResolutionIncludesSessionFactoryAndLegacyCompatibilityName() {
        assertEquals("com.openjiuwen.core.single_agent.schema.AgentCard",
                SingleAgentPackage.resolveExport("AgentCard"));
        assertEquals("com.openjiuwen.core.session.AgentSession",
                SingleAgentPackage.resolveExport("Session"));
        assertEquals("com.openjiuwen.core.session.AgentSession.createAgentSession",
                SingleAgentPackage.resolveExport("create_agent_session"));
        assertEquals("com.openjiuwen.core.single_agent.legacy.LegacyBaseAgent",
                SingleAgentPackage.resolveExport("LegacyBaseAgent"));
        assertEquals("com.openjiuwen.core.single_agent.AddAbilityResult",
                SingleAgentPackage.resolveExport("AddAbilityResult"));
    }

    @Test
    void exportsAreImmutableLikePythonModuleExportLedger() {
        List<String> exports = SingleAgentPackage.exports();

        assertThrows(UnsupportedOperationException.class, () -> exports.add("extra"));
    }
}
