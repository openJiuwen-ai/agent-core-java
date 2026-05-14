/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.tools.TeamBackend;

import java.util.List;

/**
 * Human-agent-side inbox for speaking into the team runtime.
 *
 * <p>Mirrors Python's {@code HumanAgentInbox} in
 * {@code openjiuwen.agent_teams.interaction.human_agent_inbox}.
 */
public class HumanAgentInbox {

    private final TeamBackend teamBackend;

    public HumanAgentInbox(TeamBackend teamBackend) {
        this.teamBackend = teamBackend;
    }

    public Object send(String body) {
        return sendAs(null, body);
    }

    public Object sendAs(String sender, String body) {
        List<String> humanAgents = teamBackend.humanAgentNames();
        if (humanAgents.isEmpty()) {
            throw new HumanAgentNotEnabledError(
                    "No human-agent member is registered on this team; create the team with enable_hitt=true"
            );
        }
        String resolvedSender = sender;
        if (resolvedSender == null || resolvedSender.isBlank()) {
            resolvedSender = humanAgents.get(0);
        }
        if (!humanAgents.contains(resolvedSender)) {
            throw new UnknownHumanAgentError(
                    "Sender '" + resolvedSender + "' is not a registered human-agent member"
            );
        }
        if (!TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(resolvedSender) && MentionParser.isReservedName(resolvedSender)) {
            throw new UnknownHumanAgentError(
                    "Sender '" + resolvedSender + "' collides with a reserved runtime member name"
            );
        }
        return teamBackend.broadcastMessageToMembers(body, resolvedSender);
    }
}
