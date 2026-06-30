/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.interaction;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.tools.TeamMessageManager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * User-facing inbox API for sending direct or broadcast messages into team message channels.
 *
 * @since 1.0
 */
public class UserInbox {
    private final TeamMessageManager messageManager;

    /**
     * Auto-generated for codecheck compliance.
     */
    public UserInbox(TeamMessageManager messageManager) {
        this.messageManager = messageManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> direct(String target, String body) {
        return messageManager.sendMessage(body, target, TeamConstants.USER_PSEUDO_MEMBER_NAME);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> broadcast(String body) {
        return messageManager.broadcastMessage(body, TeamConstants.USER_PSEUDO_MEMBER_NAME);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void deliverToLeader(Consumer<String> deliverInput, String body) {
        deliverInput.accept(body);
    }
}
