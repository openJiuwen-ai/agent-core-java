/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the monitor package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.monitor} in
 * {@code openjiuwen/agent_teams/monitor/__init__.py}.</p>
 */
class MonitorPackageTest {

    @Test
    void moduleConstantMatchesPythonPackage() {
        assertThat(MonitorPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/monitor/__init__.py");
    }

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertThat(MonitorPackage.EXPORTED_SYMBOLS).isEqualTo(List.of(
                "create_monitor",
                "MemberInfo",
                "MessageInfo",
                "MonitorEvent",
                "MonitorEventType",
                "TaskInfo",
                "TeamInfo",
                "TeamMonitor",
                "TeamStreamLogger"
        ));
    }
}
