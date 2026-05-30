/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal spawn-member tool.
 *
 * <p>Mirrors Python's {@code SpawnMemberTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class SpawnMemberTool extends TeamTool {

    public SpawnMemberTool(TeamBackend team) {
        super(toolCard("team.spawn_member", "spawn_member", "Create a new team member.", Map.of(
                "member_name", stringParam("Member name"),
                "display_name", stringParam("Display name"),
                "desc", stringParam("Member description"),
                "prompt", stringParam("Startup prompt")
        ), List.of("member_name", "display_name")), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String memberName = stringValue(inputs.get("member_name"));
        String displayName = stringValue(inputs.get("display_name"));
        String desc = stringValue(inputs.get("desc"));
        String prompt = stringValue(inputs.get("prompt"));
        if (memberName.isBlank() || displayName.isBlank()) {
            return new TeamToolOutput(false, null, "member_name and display_name are required");
        }
        AgentCard card = new AgentCard();
        assignField(card, "name", memberName);
        assignField(card, "description", desc.isBlank() ? displayName : desc);
        TeamMember member = team.spawnMember(
                memberName,
                displayName,
                card,
                desc,
                prompt,
                MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member_name", member.getMemberName());
        data.put("display_name", member.getDisplayName());
        data.put("status", member.getStatus().name().toLowerCase());
        return new TeamToolOutput(true, data, null);
    }

}
