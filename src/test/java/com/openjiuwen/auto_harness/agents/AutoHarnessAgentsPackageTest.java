/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.agents} in
 * {@code openjiuwen/auto_harness/agents/__init__.py}.
 */
class AutoHarnessAgentsPackageTest {

    @Test
    void exportsAgentFactoryFunctionsInPythonAllOrder() {
        assertEquals("openjiuwen/auto_harness/agents/__init__.py", AutoHarnessAgentsPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "create_activate_guide_agent",
                "create_assess_agent",
                "create_auto_harness_agent",
                "create_commit_agent",
                "create_design_ext_agent",
                "create_eval_agent",
                "create_learnings_agent",
                "create_plan_agent",
                "create_pr_draft_agent",
                "create_select_pipeline_agent"
        ), AutoHarnessAgentsPackage.ALL);
        assertSame(AutoHarnessAgentFactory.class, AutoHarnessAgentsPackage.FACTORY_CLASS);
    }
}
