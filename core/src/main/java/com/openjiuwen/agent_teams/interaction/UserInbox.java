/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.constants.TeamConstants;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * User-side inbox that routes external input into the team runtime.
 *
 * <p>Mirrors Python's {@code UserInbox} in
 * {@code openjiuwen/agent_teams/interaction/user_inbox.py}.</p>
 */
public final class UserInbox {

    private final MessageManagerView messageManager;

    public UserInbox(MessageManagerView messageManager) {
        this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
    }

    public CompletionStage<DeliverResult> direct(String target, String body) {
        return messageManager.sendMessage(body, target, TeamConstants.USER_PSEUDO_MEMBER_NAME)
                .thenApply(messageId -> messageId == null
                        ? DeliverResult.failure("send_failed:" + target)
                        : DeliverResult.success(messageId));
    }

    public CompletionStage<DeliverResult> broadcast(String body) {
        return messageManager.broadcastMessage(body, TeamConstants.USER_PSEUDO_MEMBER_NAME)
                .thenApply(messageId -> messageId == null
                        ? DeliverResult.failure("broadcast_failed")
                        : DeliverResult.success(messageId));
    }

    public static CompletionStage<DeliverResult> deliverToLeader(LeaderInput deliverInput, String body) {
        Objects.requireNonNull(deliverInput, "deliverInput");
        try {
            return deliverInput.deliverInput(body)
                    .handle((ignored, throwable) -> {
                        if (throwable == null) {
                            return DeliverResult.success();
                        }
                        return DeliverResult.failure("deliver_to_leader_failed:" + exceptionMessage(throwable));
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(
                    DeliverResult.failure("deliver_to_leader_failed:" + exceptionMessage(exception))
            );
        }
    }

    private static String exceptionMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    /**
     * Message persistence surface used by the user inbox.
     *
     * <p>Mirrors Python's {@code TeamMessageManager} calls in
     * {@code openjiuwen/agent_teams/interaction/user_inbox.py}.</p>
     */
    public interface MessageManagerView {
        CompletionStage<String> broadcastMessage(String content, String fromMemberName);

        CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName);
    }

    /**
     * Leader DeepAgent input function.
     *
     * <p>Mirrors Python's callable accepted by {@code deliver_to_leader} in
     * {@code openjiuwen/agent_teams/interaction/user_inbox.py}.</p>
     */
    @FunctionalInterface
    public interface LeaderInput {
        CompletionStage<Void> deliverInput(String body);
    }
}
