/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.util.ArrayList;
import java.util.List;

/**
 * Teammate-side approval marker rail.
 *
 * <p>Mirrors Python's {@code TeamToolApprovalRail} registration path used by
 * {@code openjiuwen.agent_teams.agent.team_agent} when a teammate spec declares
 * approval-required tools.</p>
 */
public class TeamToolApprovalRail extends AgentRail {

    private final List<String> approvalRequiredTools;

    public TeamToolApprovalRail(List<String> approvalRequiredTools) {
        this.approvalRequiredTools = approvalRequiredTools != null
                ? new ArrayList<>(approvalRequiredTools) : new ArrayList<>();
    }

    public List<String> getApprovalRequiredTools() {
        return new ArrayList<>(approvalRequiredTools);
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        // The approval decision is handled by team tools; this rail keeps the
        // teammate's DeepAgent callback surface aligned with Python.
    }
}
