/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import java.util.List;

/**
 * Public facade for auto-harness agent factory exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.agents} in
 * {@code openjiuwen/auto_harness/agents/__init__.py}.</p>
 */
public final class AutoHarnessAgentsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/agents/__init__.py";
    public static final List<String> ALL = List.of(
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
    );
    public static final Class<AutoHarnessAgentFactory> FACTORY_CLASS = AutoHarnessAgentFactory.class;

    private AutoHarnessAgentsPackage() {
    }
}
