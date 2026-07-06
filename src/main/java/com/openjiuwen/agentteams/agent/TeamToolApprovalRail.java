/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;

/**
 * Tool approval rail for team coordination.
 *
 * <p>Mirrors Python TeamToolApprovalRail: when a teammate calls a tool,
 * sends an approval request to the leader. The leader reviews and
 * responds via the approve_tool tool.</p>
 */
public class TeamToolApprovalRail extends ConfirmInterruptRail {

    private final String teamName;
    private final String memberName;
    private final String leaderMemberName;
    private final TeamMessageManager messageManager;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamToolApprovalRail(
            String teamName,
            String memberName,
            TeamDatabase db,
            Messager messager,
            String leaderMemberName,
            Iterable<String> toolNames) {
        super(toolNames);
        this.teamName = teamName;
        this.memberName = memberName;
        this.leaderMemberName = leaderMemberName;
        this.messageManager = new TeamMessageManager(teamName, memberName, db, messager);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    protected InterruptDecision resolveInterrupt(
            AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {

        String toolName = toolCall != null ? toolCall.getName() : "unknown";

        // First call: send approval request to leader and interrupt
        if (userInput == null) {
            String toolCallId = toolCall != null ? toolCall.getId() : "";
            String argsStr = toolCall != null && toolCall.getArguments() != null
                    ? String.valueOf(toolCall.getArguments()) : "{}";

            String message = "Teammate tool approval request.\n"
                    + "Member: " + memberName + "\n"
                    + "Tool: " + toolName + "\n"
                    + "Tool Call ID: " + toolCallId + "\n"
                    + "Arguments: " + argsStr + "\n"
                    + "Please review and call approve_tool.\n\n";

            Loggers.AGENT.info("Sending tool approval request to leader for {} (call_id: {})",
                    toolName, toolCallId);

            try {
                String messageId = messageManager.sendMessage(message, leaderMemberName).join();
                if (messageId == null || messageId.isBlank()) {
                    Loggers.AGENT.error("Failed to send approval request for {}", toolName);
                    return reject("Failed to send approval request to leader");
                }
            } catch (Exception e) {
                Loggers.AGENT.error("Failed to send approval request for {}: {}", toolName, e.getMessage());
                return reject("Failed to send approval request to leader: " + e.getMessage());
            }

            return interrupt(InterruptRequest.builder()
                    .message("Awaiting leader approval for tool: " + toolName)
                    .build());
        }

        // Resume: process leader's approval response
        try {
            String value = String.valueOf(userInput).trim().toLowerCase(java.util.Locale.ROOT);
            if ("false".equals(value) || "no".equals(value) || "reject".equals(value)) {
                Loggers.AGENT.info("Tool {} rejected by leader for member {}", toolName, memberName);
                return reject("Tool call rejected by leader");
            }
            Loggers.AGENT.info("Tool {} approved by leader for member {}", toolName, memberName);
            return approve();
        } catch (Exception e) {
            Loggers.AGENT.error("Failed to parse approval response for {}: {}", toolName, e.getMessage());
            return interrupt(InterruptRequest.builder()
                    .message("Invalid approval response format for tool: " + toolName)
                    .build());
        }
    }
}
