/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.interaction;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Inbox for human-agent members, validating sender identity before delivering messages.
 *
 * @since 1.0
 */
public class HumanAgentInbox {
    private final TeamBackend team;
    private final TeamMessageManager messageManager;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HumanAgentInbox(TeamBackend team, TeamMessageManager messageManager) {
        this.team = team;
        this.messageManager = messageManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> send(String body, String to, String sender) {
        String resolvedSender = resolveSender(sender);
        if (to == null) {
            return messageManager.broadcastMessage(body, resolvedSender);
        }
        return messageManager.sendMessage(body, to, resolvedSender);
    }

    private String resolveSender(String sender) {
        Set<String> names = team.humanAgentNames();
        if (names.isEmpty()) {
            throw new HumanAgentNotEnabledError(
                    "No human-agent member is registered on this team; create the team with humanAgentEnabled=true"
            );
        }
        if (sender == null) {
            if (names.contains(TeamConstants.HUMAN_AGENT_MEMBER_NAME)) {
                return TeamConstants.HUMAN_AGENT_MEMBER_NAME;
            }
            return sortedNames(names).get(0);
        }
        if (!names.contains(sender)) {
            throw new UnknownHumanAgentError(
                    "'" + sender + "' is not a registered human-agent member; registered members: " + sortedNames(names)
            );
        }
        return sender;
    }

    private List<String> sortedNames(Set<String> names) {
        List<String> values = new ArrayList<>(names);
        values.sort(String::compareTo);
        return values;
    }
}
