/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal approve-plan tool.
 *
 * <p>Mirrors Python's {@code ApprovePlanTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ApprovePlanTool extends TeamTool {

    public ApprovePlanTool(TeamBackend team) {
        super(toolCard("team.approve_plan", "approve_plan", "Approve or reject a teammate plan."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String memberName = inputs.get("member_name") != null ? String.valueOf(inputs.get("member_name")) : "";
        boolean approved = Boolean.TRUE.equals(inputs.get("approved"));
        if (memberName.isBlank()) {
            return new TeamToolOutput(false, null, "member_name is required");
        }
        boolean success = team.approvePlan(memberName, approved, inputs.get("feedback") != null
                ? String.valueOf(inputs.get("feedback")) : null);
        if (!success) {
            return new TeamToolOutput(false, null, "Failed to approve/reject plan");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member_name", memberName);
        data.put("approved", approved);
        return new TeamToolOutput(true, data, null);
    }
}
