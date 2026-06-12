/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link CoordinationHandlersPackage}.
 *
 * <p>Mirrors Python's package exports in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/__init__.py}.</p>
 */
class CoordinationHandlersPackageTest {

    @Test
    void exposesPythonModuleDescriptionAndAllOrder() {
        assertEquals(
                "openjiuwen/agent_teams/agent/coordination/handlers/__init__.py",
                CoordinationHandlersPackage.PYTHON_MODULE
        );
        assertEquals("Scenario-scoped coordination event handlers.", CoordinationHandlersPackage.DESCRIPTION);
        assertEquals(
                List.of(
                        "AgentLifecycleHandler",
                        "BaseCoordinationHandler",
                        "EventCallback",
                        "MemberHandler",
                        "MessageHandler",
                        "StaleTaskHandler",
                        "TaskBoardHandler",
                        "TeamCompletionHandler"
                ),
                CoordinationHandlersPackage.EXPORTED_SYMBOLS
        );
    }
}
