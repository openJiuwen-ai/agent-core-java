/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import java.util.List;

/**
 * Package facade for agent-team monitor exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.monitor} in
 * {@code openjiuwen/agent_teams/monitor/__init__.py}.</p>
 */
public final class MonitorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/monitor/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "create_monitor",
            "MemberInfo",
            "MessageInfo",
            "MonitorEvent",
            "MonitorEventType",
            "TaskInfo",
            "TeamInfo",
            "TeamMonitor",
            "TeamStreamLogger"
    );

    private MonitorPackage() {
    }
}
