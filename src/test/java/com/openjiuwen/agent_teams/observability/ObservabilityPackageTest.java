/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.observability} in
 * {@code openjiuwen/agent_teams/observability/__init__.py}.
 */
class ObservabilityPackageTest {

    @AfterEach
    void tearDown() {
        ObservabilitySetup.shutdownObservability();
    }

    @Test
    void exportsPublicApiNamesInPythonOrder() {
        assertEquals("openjiuwen/agent_teams/observability/__init__.py", ObservabilityPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "ObservabilityConfig",
                "ObservabilityRail",
                "attach_to_team_agent",
                "detach_from_team_agent",
                "init_observability",
                "shutdown_observability"
        ), ObservabilityPackage.ALL);
    }

    @Test
    void delegatesSetupLifecycle() {
        ObservabilityConfig config = new ObservabilityConfig();
        config.setEnabled(false);

        assertDoesNotThrow(() -> ObservabilityPackage.initObservability(config));
        assertDoesNotThrow(ObservabilityPackage::shutdownObservability);
        assertDoesNotThrow(() -> ObservabilityPackage.attachToTeamAgent(null));
        assertDoesNotThrow(() -> ObservabilityPackage.detachFromTeamAgent(null));
        assertInstanceOf(ObservabilityRail.class, new ObservabilityRail());
    }
}
