/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal build-team tool.
 *
 * <p>Mirrors Python's {@code BuildTeamTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class BuildTeamTool extends TeamTool {

    public BuildTeamTool(TeamBackend team) {
        super(toolCard("team.build_team", "build_team", "Create and initialize an agent team.", Map.of(
                "display_name", stringParam("Team display name"),
                "team_desc", stringParam("Team description"),
                "leader_display_name", stringParam("Leader display name"),
                "leader_desc", stringParam("Leader persona"),
                "enable_hitt", booleanParam("Enable human-in-the-team mode")
        ), List.of("display_name", "team_desc", "leader_display_name", "leader_desc")), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String displayName = stringValue(inputs.get("display_name"));
        String teamDesc = stringValue(inputs.get("team_desc"));
        String leaderDisplayName = stringValue(inputs.get("leader_display_name"));
        String leaderDesc = stringValue(inputs.get("leader_desc"));
        boolean enableHitt = Boolean.TRUE.equals(inputs.get("enable_hitt"))
                || "true".equalsIgnoreCase(stringValue(inputs.get("enable_hitt")));
        team.buildTeam(displayName, teamDesc, leaderDisplayName, leaderDesc, enableHitt);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("team_name", team.getTeamName());
        data.put("display_name", displayName);
        data.put("team_desc", teamDesc);
        data.put("leader_member_name", team.getMemberName());
        data.put("leader_display_name", leaderDisplayName);
        data.put("enable_hitt", enableHitt);
        return new TeamToolOutput(true, data, null);
    }
}
