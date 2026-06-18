/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the legacy single-agent package bridge.
 *
 * <p>Mirrors Python's package exports and {@code _deprecated_class} helper in
 * {@code openjiuwen/core/single_agent/legacy/__init__.py}.</p>
 */
class LegacyPackageTest {

    @Test
    void exportsMirrorPythonAllOrder() {
        assertEquals("openjiuwen/core/single_agent/legacy/__init__.py", LegacyPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "LegacyReActAgent",
                "create_react_agent_config",
                "LegacyBaseAgent",
                "ControllerAgent",
                "WorkflowFactory",
                "workflow_provider",
                "AgentConfig",
                "LLMCallConfig",
                "IntentDetectionConfig",
                "ConstrainConfig",
                "DefaultResponse",
                "WorkflowAgentConfig",
                "MemoryConfig",
                "LegacyReActAgentConfig",
                "WorkflowSchema",
                "PluginSchema"
        ), LegacyPackage.exports());
    }

    @Test
    void exportResolutionKeepsExistingSchemaPackageAndFutureLegacySymbols() {
        assertEquals("com.openjiuwen.core.single_agent.legacy.agent.BaseAgent",
                LegacyPackage.resolveExport("LegacyBaseAgent"));
        assertEquals("com.openjiuwen.core.single_agent.legacy.react_agent.LegacyReActAgentFactory"
                        + ".createReactAgentConfig",
                LegacyPackage.resolveExport("create_react_agent_config"));
        assertEquals("com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema",
                LegacyPackage.resolveExport("WorkflowSchema"));
        assertNull(LegacyPackage.resolveExport("Unknown"));
    }

    @Test
    void exportsAreImmutableLikePythonAllTuple() {
        List<String> exports = LegacyPackage.exports();

        assertThrows(UnsupportedOperationException.class, () -> exports.add("extra"));
    }

    @Test
    void deprecationHelperMirrorsMessageTemplateAndSingleWrappingGuard() {
        assertEquals("openjiuwen.core.single_agent.agent.BaseAgent",
                LegacyDeprecation.alternativeFor("LegacyBaseAgent"));
        assertEquals("LegacyBaseAgent is deprecated and will be removed in the future. Please use "
                        + "openjiuwen.core.single_agent.agent.BaseAgent instead.",
                LegacyDeprecation.warningMessage("LegacyBaseAgent"));

        assertTrue(LegacyDeprecation.registerDeprecatedClass("LegacyBaseAgent"));
        assertFalse(LegacyDeprecation.registerDeprecatedClass("LegacyBaseAgent"));
        assertTrue(LegacyDeprecation.isDeprecatedWrapped("LegacyBaseAgent"));
        assertFalse(LegacyDeprecation.registerDeprecatedClass("Unknown"));
        assertNull(LegacyDeprecation.warningMessage("Unknown"));
    }
}
