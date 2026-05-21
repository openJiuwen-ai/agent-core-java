/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail that enforces read-only plan mode constraints.
 * <p>
 * Always registered; activates conditionally when the agent enters plan mode.
 * Registers enter_plan_mode / exit_plan_mode tools and filters visible
 * tools based on the current mode.
 * <p>
 * Mirrors Python's {@code AgentModeRail} in
 * {@code openjiuwen.harness.rails.agent_mode_rail}.
 */
public class AgentModeRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(AgentModeRail.class);

    /** Default tools allowed in plan mode. */
    public static final String[] DEFAULT_PLAN_MODE_ALLOWED_TOOLS = {
        "switch_mode", "enter_plan_mode", "exit_plan_mode",
        "ask_user", "task_tool", "read_file", "grep",
        "list_files", "glob", "bash", "write_file", "edit_file"
    };

    private boolean inPlanMode = false;

    public AgentModeRail() {
        super();
    }

    /** Check if currently in plan mode. */
    public boolean isInPlanMode() {
        return inPlanMode;
    }

    /** Enter plan mode. */
    public void enterPlanMode() {
        this.inPlanMode = true;
        LOG.info("[AgentModeRail] Entered plan mode");
    }

    /** Exit plan mode. */
    public void exitPlanMode() {
        this.inPlanMode = false;
        LOG.info("[AgentModeRail] Exited plan mode");
    }

    @Override
    public void init(Object agent) {
        LOG.info("[AgentModeRail] Initialized (plan mode enforcement rail)");
    }

    @Override
    public void uninit(Object agent) {
        this.inPlanMode = false;
        LOG.info("[AgentModeRail] Uninitialized");
    }
}
