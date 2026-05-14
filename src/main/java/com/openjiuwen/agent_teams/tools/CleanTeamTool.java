/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal clean-team tool.
 *
 * <p>Mirrors Python's {@code CleanTeamTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class CleanTeamTool extends TeamTool {

    public CleanTeamTool(TeamBackend team) {
        super(toolCard("team.clean_team", "clean_team", "Clean up a team after work completes."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("team_name", team.getTeamName());
        data.put("removed_paths", team.cleanTeam());
        return new TeamToolOutput(true, data, null);
    }
}
