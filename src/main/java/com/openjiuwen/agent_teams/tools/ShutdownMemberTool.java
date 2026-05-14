/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal shutdown-member tool.
 *
 * <p>Mirrors Python's {@code ShutdownMemberTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ShutdownMemberTool extends TeamTool {

    public ShutdownMemberTool(TeamBackend team) {
        super(toolCard("team.shutdown_member", "shutdown_member", "Shutdown a team member."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String memberName = inputs.get("member_name") != null ? String.valueOf(inputs.get("member_name")) : "";
        boolean force = Boolean.TRUE.equals(inputs.get("force"));
        if (memberName.isBlank()) {
            return new TeamToolOutput(false, null, "member_name is required");
        }
        boolean success = team.shutdownMember(memberName, force);
        if (!success) {
            return new TeamToolOutput(false, null, "Member '" + memberName + "' not found");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member_name", memberName);
        data.put("force", force);
        return new TeamToolOutput(true, data, null);
    }
}
