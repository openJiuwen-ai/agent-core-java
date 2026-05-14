/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal send-message tool.
 *
 * <p>Mirrors Python's {@code SendMessageTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class SendMessageTool extends TeamTool {

    public SendMessageTool(TeamBackend team) {
        super(toolCard("team.send_message", "send_message", "Send a point-to-point or broadcast message."), team);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String content = inputs.get("content") != null ? String.valueOf(inputs.get("content")) : "";
        String to = inputs.get("to") != null ? String.valueOf(inputs.get("to")) : "";
        if (content.isBlank()) {
            return new TeamToolOutput(false, null, "content is required");
        }
        String type;
        String messageId;
        Object runtimeResult;
        if ("*".equals(to) || to.isBlank()) {
            Map<String, Object> broadcast = team.broadcastMessageToMembers(content, team.getMemberName());
            messageId = String.valueOf(broadcast.get("message_id"));
            type = "broadcast";
            runtimeResult = broadcast.get("triggered_members");
        } else {
            Map<String, Object> delivery = team.deliverMessage(content, to, team.getMemberName());
            messageId = String.valueOf(delivery.get("message_id"));
            runtimeResult = delivery.get("runtime_result");
            type = "message";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("message_id", messageId);
        data.put("from", team.getMemberName());
        data.put("to", "broadcast".equals(type) ? "*" : to);
        data.put("runtime_result", runtimeResult);
        return new TeamToolOutput(true, data, null);
    }
}
