/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.harness.rails.interrupt.ApproveResult;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.RejectResult;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Tool approval rail for team coordination.
 * <p>
 * Mirrors Python TeamToolApprovalRail: when a teammate calls a tool,
 * sends an approval request to the leader. The leader reviews and
 * responds via the approve_tool tool.
 * </p>
 *
 * @since 0.1.7
 */
public class TeamToolApprovalRail extends ConfirmInterruptRail {
    private final String teamName;
    private final String memberName;
    private final String leaderMemberName;
    private final TeamMessageManager messageManager;

    /**
     * TeamToolApprovalRail.
     *
     * @param teamName teamName
     * @param memberName memberName
     * @param db db
     * @param messager messager
     * @param leaderMemberName leaderMemberName
     * @param toolNames toolNames
     * @since 0.1.7
     */
    public TeamToolApprovalRail(String teamName, String memberName, TeamDatabase db, Messager messager,
            String leaderMemberName, Iterable<String> toolNames) {
        super(asCollection(toolNames));
        this.teamName = teamName;
        this.memberName = memberName;
        this.leaderMemberName = leaderMemberName;
        this.messageManager = new TeamMessageManager(teamName, memberName, db, messager);
    }

    private static Collection<String> asCollection(Iterable<String> toolNames) {
        if (toolNames instanceof Collection<String> collection) {
            return collection;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        if (toolNames != null) {
            toolNames.forEach(names::add);
        }
        return names;
    }

    /**
     * resolveInterrupt.
     *
     * @param ctx ctx
     * @param toolCall toolCall
     * @param userInput userInput
     * @param autoConfirmConfig autoConfirmConfig
     * @return the result
     * @since 0.1.7
     */
    @Override
    public InterruptDecision resolveInterrupt(
            com.openjiuwen.harness.rails.CallbackContext ctx,
            Object toolCall,
            Object userInput,
            Map<String, Object> autoConfirmConfig) {
        String toolName = readToolName(toolCall);

        // First call: send approval request to leader and interrupt
        if (userInput == null) {
            String toolCallId = readToolCallId(toolCall);
            String argsStr = readToolArguments(toolCall);

            String message = "Teammate tool approval request.\n" + "Member: " + memberName + "\n" + "Tool: " + toolName
                    + "\n" + "Tool Call ID: " + toolCallId + "\n" + "Arguments: " + argsStr + "\n"
                    + "Please review and call approve_tool.\n\n";

            Loggers.AGENT.info("Sending tool approval request to leader for {} (call_id: {})", toolName, toolCallId);

            try {
                String messageId = messageManager.sendMessage(message, leaderMemberName).join();
                if (messageId == null || messageId.isBlank()) {
                    Loggers.AGENT.error("Failed to send approval request for {}", toolName);
                    return reject("Failed to send approval request to leader");
                }
            } catch (CompletionException e) {
                Loggers.AGENT.error("Failed to send approval request for {}: {}", toolName, e.getMessage());
                return reject("Failed to send approval request to leader: " + e.getMessage());
            }

            return interrupt(new InterruptRequest(
                    "Awaiting leader approval for tool: " + toolName, Map.of(), ""));
        }

        // Resume: process leader's approval response
        String value = String.valueOf(userInput).trim().toLowerCase(java.util.Locale.ROOT);
        if ("false".equals(value) || "no".equals(value) || "reject".equals(value)) {
            Loggers.AGENT.info("Tool {} rejected by leader for member {}", toolName, memberName);
            return reject("Tool call rejected by leader");
        }
        Loggers.AGENT.info("Tool {} approved by leader for member {}", toolName, memberName);
        return approve();
    }

    private static String readToolName(Object toolCall) {
        if (toolCall instanceof com.openjiuwen.core.foundation.llm.schema.ToolCall call) {
            return call.getName() != null ? call.getName() : "unknown";
        }
        return toolCall != null ? String.valueOf(toolCall) : "unknown";
    }

    private static String readToolCallId(Object toolCall) {
        if (toolCall instanceof com.openjiuwen.core.foundation.llm.schema.ToolCall call) {
            return call.getId() != null ? call.getId() : "";
        }
        return "";
    }

    private static String readToolArguments(Object toolCall) {
        if (toolCall instanceof com.openjiuwen.core.foundation.llm.schema.ToolCall call
                && call.getArguments() != null) {
            return String.valueOf(call.getArguments());
        }
        return "{}";
    }

    private static InterruptDecision interrupt(InterruptRequest request) {
        return new InterruptResult(request);
    }

    private static InterruptDecision approve() {
        return new ApproveResult();
    }

    private static InterruptDecision reject(String toolResult) {
        return new RejectResult(toolResult);
    }
}
