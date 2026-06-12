/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the coordination package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.agent.coordination} in
 * {@code openjiuwen/agent_teams/agent/coordination/__init__.py}.</p>
 */
class CoordinationPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals(
                "openjiuwen/agent_teams/agent/coordination/__init__.py",
                CoordinationPackage.PYTHON_MODULE
        );
        assertEquals("TeamAgent coordination subsystem.", CoordinationPackage.DESCRIPTION);
        assertEquals(List.of(
                "AgentLifecycleHandler",
                "BaseCoordinationHandler",
                "CoordinationEvent",
                "CoordinationKernel",
                "DispatcherHost",
                "EventBus",
                "EventDispatcher",
                "InnerEventMessage",
                "InnerEventType",
                "MemberHandler",
                "MessageHandler",
                "StaleTaskHandler",
                "TaskBoardHandler",
                "WakeCallback"
        ), CoordinationPackage.EXPORTED_SYMBOLS);
    }
}
