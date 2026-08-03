/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import java.util.List;

/**
 * Public coordination package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.agent.coordination} in
 * {@code openjiuwen/agent_teams/agent/coordination/__init__.py}.</p>
 */
public final class CoordinationPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/agent/coordination/__init__.py";
    public static final String DESCRIPTION = "TeamAgent coordination subsystem.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
    );

    private CoordinationPackage() {
    }
}
