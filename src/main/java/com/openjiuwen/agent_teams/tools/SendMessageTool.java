/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal send-message tool.
 *
 * <p>Mirrors Python's {@code SendMessageTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class SendMessageTool extends TeamTool {

    private final boolean validateMembers;

    public SendMessageTool(TeamBackend team) {
        this(team, false);
    }

    public SendMessageTool(TeamBackend team, boolean validateMembers) {
        super(toolCard("team.send_message", "send_message", "Send a point-to-point or broadcast message.", Map.of(
                "to", stringParam("Recipient member name or * for broadcast"),
                "content", stringParam("Message content"),
                "summary", stringParam("Short summary")
        ), List.of("to", "content")), team);
        this.validateMembers = validateMembers;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String content = stringValue(inputs.get("content"));
        String to = stringValue(inputs.get("to"));
        String summary = stringValue(inputs.get("summary"));
        if (to.isBlank()) {
            return new TeamToolOutput(false, null, "'to' is required");
        }
        if (content.isBlank()) {
            return new TeamToolOutput(false, null, "'content' is required");
        }
        String type;
        String messageId;
        Object runtimeResult;
        if ("*".equals(to)) {
            Map<String, Object> broadcast = team.broadcastMessageToMembers(content, team.getMemberName());
            messageId = String.valueOf(broadcast.get("message_id"));
            type = "broadcast";
            runtimeResult = broadcast.get("triggered_members");
        } else {
            if (validateMembers && !team.hasMember(to)) {
                return new TeamToolOutput(false, null, "Member '" + to + "' not found");
            }
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
        data.put("summary", summary.isBlank() ? null : summary);
        data.put("runtime_result", runtimeResult);
        return new TeamToolOutput(true, data, null);
    }

    @Override
    public String mapResult(TeamToolOutput output) {
        if (!output.isSuccess() || !(output.getData() instanceof Map<?, ?> data)) {
            return super.mapResult(output);
        }
        if ("broadcast".equals(data.get("type"))) {
            return "Broadcast sent from " + data.get("from");
        }
        return "Message sent from " + data.get("from") + " to " + data.get("to");
    }
}
