/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal build-team tool.
 *
 * <p>Mirrors Python's {@code BuildTeamTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class BuildTeamTool extends TeamTool {

    public BuildTeamTool(TeamBackend team) {
        super(toolCard("team.build_team", "build_team", "Create and initialize an agent team."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        team.registerPredefinedMembers();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("team_name", team.getTeamName());
        data.put("leader_member_name", team.getMemberName());
        data.put("started_members", team.startup());
        return new TeamToolOutput(true, data, null);
    }
}
