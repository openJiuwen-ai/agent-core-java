/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoHarnessCliSupportTest {

    @Test
    void requestFromMapCopiesFields() {
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("task", "fix bug");
        kwargs.put("dry_run", true);
        kwargs.put("budget", 3.5);
        kwargs.put("goal", "stabilize tests");

        AutoHarnessRunRequest request = AutoHarnessRunRequest.fromMap(kwargs);
        assertEquals("fix bug", request.getTask());
        assertEquals(true, request.isDryRun());
        assertEquals(3.5, request.getBudget());
        assertEquals("stabilize tests", request.getGoal());
    }

    @Test
    void resolveTasksReturnsNullWhenTaskMissing() {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        assertNull(AutoHarnessCliSupport.resolveTasks(request));
    }

    @Test
    void resolveTasksReturnsSingletonWhenTaskPresent() {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setTask("improve docs");
        assertEquals(List.of("improve docs"), AutoHarnessCliSupport.resolveTasks(request));
    }

    @Test
    void buildPathsMatchCliConvention() {
        String cliHome = "/tmp/openjiuwen-home";
        assertEquals("/tmp/openjiuwen-home/auto_harness",
                AutoHarnessCliSupport.buildDataDir(cliHome).replace('\\', '/'));
        assertEquals("/tmp/openjiuwen-home/auto_harness/config.yaml",
                AutoHarnessCliSupport.buildConfigPath(cliHome).replace('\\', '/'));
    }

    @Test
    void applyRequestReturnsSameConfigWhenNoDirectMappingNeeded() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setBudget(7.25);
        assertEquals(config, AutoHarnessCliSupport.applyRequest(config, request));
    }
}
