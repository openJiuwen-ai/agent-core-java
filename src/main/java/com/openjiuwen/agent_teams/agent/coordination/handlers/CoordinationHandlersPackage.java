/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import java.util.List;

/**
 * Public facade for scenario-scoped coordination event handlers.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.agent.coordination.handlers}
 * package initializer in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/__init__.py}.</p>
 */
public final class CoordinationHandlersPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_teams/agent/coordination/handlers/__init__.py";
    public static final String DESCRIPTION = "Scenario-scoped coordination event handlers.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "AgentLifecycleHandler",
            "BaseCoordinationHandler",
            "EventCallback",
            "MemberHandler",
            "MessageHandler",
            "StaleTaskHandler",
            "TaskBoardHandler",
            "TeamCompletionHandler"
    );

    private CoordinationHandlersPackage() {
    }
}
