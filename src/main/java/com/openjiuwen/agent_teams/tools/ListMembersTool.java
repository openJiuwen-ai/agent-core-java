/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.agent.TeamMember;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal list-members tool.
 *
 * <p>Mirrors Python's {@code ListMembersTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ListMembersTool extends TeamTool {

    public ListMembersTool(TeamBackend team) {
        super(toolCard("team.list_members", "list_members", "List current team members."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (TeamMember member : team.listMembers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("member_name", member.getMemberName());
            row.put("display_name", member.getDisplayName());
            row.put("status", member.getStatus().name().toLowerCase());
            row.put("execution_status", member.getExecutionStatus().name().toLowerCase());
            members.add(row);
        }
        return new TeamToolOutput(true, members, null);
    }
}
