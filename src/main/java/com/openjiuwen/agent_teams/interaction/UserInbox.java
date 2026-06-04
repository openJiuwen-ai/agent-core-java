/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.tools.TeamBackend;

import java.util.Map;
import java.util.function.Function;

/**
 * User-side inbox for routing external input into the team runtime.
 *
 * <p>Mirrors Python's {@code UserInbox} in
 * {@code openjiuwen.agent_teams.interaction.user_inbox}.
 */
public class UserInbox {

    private final TeamBackend teamBackend;
    private final Function<String, Object> deliverToLeader;

    public UserInbox(TeamBackend teamBackend, Function<String, Object> deliverToLeader) {
        this.teamBackend = teamBackend;
        this.deliverToLeader = deliverToLeader;
    }

    public Object direct(String target, String body) {
        if (MentionParser.isReservedName(target) && !TeamConstants.USER_PSEUDO_MEMBER_NAME.equals(target)
                && !TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(target)) {
            return Map.of("error", "Target '" + target + "' is reserved by the runtime");
        }
        if (!teamBackend.hasMember(target)) {
            return Map.of("error", "Member '" + target + "' not found");
        }
        return teamBackend.sendMessage(body, target, TeamConstants.USER_PSEUDO_MEMBER_NAME);
    }

    public Object broadcast(String body) {
        return teamBackend.broadcastMessageToMembers(body, TeamConstants.USER_PSEUDO_MEMBER_NAME);
    }

    public Object deliverToLeader(String body) {
        return deliverToLeader.apply(body);
    }
}
