/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal approve-tool-call tool.
 *
 * <p>Mirrors Python's {@code ApproveToolCallTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class ApproveToolCallTool extends TeamTool {

    public ApproveToolCallTool(TeamBackend team) {
        super(toolCard("team.approve_tool", "approve_tool", "Approve or reject a teammate tool call."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String memberName = inputs.get("member_name") != null ? String.valueOf(inputs.get("member_name")) : "";
        String toolCallId = inputs.get("tool_call_id") != null ? String.valueOf(inputs.get("tool_call_id")) : "";
        boolean approved = Boolean.TRUE.equals(inputs.get("approved"));
        if (memberName.isBlank() || toolCallId.isBlank()) {
            return new TeamToolOutput(false, null, "member_name and tool_call_id are required");
        }
        boolean success = team.approveTool(memberName, toolCallId, approved,
                inputs.get("feedback") != null ? String.valueOf(inputs.get("feedback")) : null);
        if (!success) {
            return new TeamToolOutput(false, null, "Failed to approve/reject tool call");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member_name", memberName);
        data.put("tool_call_id", toolCallId);
        data.put("approved", approved);
        return new TeamToolOutput(true, data, null);
    }
}
