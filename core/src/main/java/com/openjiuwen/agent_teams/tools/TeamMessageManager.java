/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.BroadcastEvent;
import com.openjiuwen.agent_teams.schema.MessageEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.tools.database.MessageDao;
import com.openjiuwen.agent_teams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team message manager for direct and broadcast team communication.
 *
 * <p>Mirrors Python's {@code TeamMessageManager} in
 * {@code openjiuwen/agent_teams/tools/message_manager.py}.</p>
 */
public class TeamMessageManager {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final String teamName;
    private final String memberName;
    private final MessageStore messageStore;
    private final Messager messager;

    public TeamMessageManager(String teamName, String memberName, TeamDatabase database, Messager messager) {
        this(teamName, memberName, messageStoreFrom(database), messager);
    }

    public TeamMessageManager(String teamName, String memberName, InMemoryTeamDatabase database, Messager messager) {
        this(teamName, memberName, messageStoreFrom(database), messager);
    }

    public TeamMessageManager(String teamName, String memberName, MessageStore messageStore, Messager messager) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.messageStore = Objects.requireNonNull(messageStore, "messageStore");
        this.messager = messager;
    }

    public CompletionStage<String> sendMessage(String content, String toMemberName) {
        return sendMessage(content, toMemberName, null);
    }

    public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
        String sender = selectSender(fromMemberName);
        String messageId = UUID.randomUUID().toString();
        return messageStore.createMessage(messageId, teamName, sender, content, toMemberName, false, false)
                .thenCompose(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        TEAM_LOGGER.error("Failed to create message %s", messageId);
                        return CompletableFuture.completedFuture(null);
                    }
                    EventMessage eventMessage = EventMessage.fromEvent(messageEvent(messageId, sender, toMemberName));
                    return publishEvent(messageId, eventMessage).thenApply(ignored -> {
                        TEAM_LOGGER.debug("Message sent from %s to %s: %s", sender, toMemberName, messageId);
                        return messageId;
                    });
                });
    }

    public CompletionStage<String> broadcastMessage(String content) {
        return broadcastMessage(content, null);
    }

    public CompletionStage<String> broadcastMessage(String content, String fromMemberName) {
        String sender = selectSender(fromMemberName);
        String messageId = UUID.randomUUID().toString();
        return messageStore.createMessage(messageId, teamName, sender, content, null, true, false)
                .thenCompose(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        TEAM_LOGGER.error("Failed to create broadcast message %s", messageId);
                        return CompletableFuture.completedFuture(null);
                    }
                    EventMessage eventMessage = EventMessage.fromEvent(broadcastEvent(messageId, sender));
                    return publishEvent(messageId, eventMessage).thenApply(ignored -> {
                        TEAM_LOGGER.debug("Broadcast message sent from %s: %s", sender, messageId);
                        return messageId;
                    });
                });
    }

    public CompletionStage<List<TeamMessage>> getMessages(String toMemberName) {
        return getMessages(toMemberName, false, null);
    }

    public CompletionStage<List<TeamMessage>> getMessages(String toMemberName, boolean unreadOnly) {
        return getMessages(toMemberName, unreadOnly, null);
    }

    public CompletionStage<List<TeamMessage>> getMessages(
            String toMemberName,
            boolean unreadOnly,
            String fromMemberName) {
        return messageStore.getMessages(teamName, toMemberName, unreadOnly, fromMemberName);
    }

    public CompletionStage<List<TeamMessage>> getBroadcastMessages(String memberName) {
        return getBroadcastMessages(memberName, false, null);
    }

    public CompletionStage<List<TeamMessage>> getBroadcastMessages(String memberName, boolean unreadOnly) {
        return getBroadcastMessages(memberName, unreadOnly, null);
    }

    public CompletionStage<List<TeamMessage>> getBroadcastMessages(
            String memberName,
            boolean unreadOnly,
            String fromMemberName) {
        return messageStore.getBroadcastMessages(teamName, memberName, unreadOnly, fromMemberName);
    }

    public CompletionStage<List<TeamMessage>> getTeamMessages(String targetTeamName) {
        return messageStore.getTeamMessages(targetTeamName, null);
    }

    public CompletionStage<Boolean> hasUnreadMessages() {
        return hasUnreadMessages(true);
    }

    public CompletionStage<Boolean> hasUnreadMessages(boolean includeBroadcast) {
        return messageStore.hasUnreadMessages(teamName, includeBroadcast);
    }

    public CompletionStage<Boolean> markMessageRead(String messageId, String targetMemberName) {
        return messageStore.markMessageRead(messageId, targetMemberName)
                .thenApply(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        TEAM_LOGGER.debug("Message %s marked as read by %s", messageId, targetMemberName);
                    } else {
                        TEAM_LOGGER.error("Failed to mark message %s as read by %s", messageId, targetMemberName);
                    }
                    return success;
                });
    }

    private String selectSender(String fromMemberName) {
        return fromMemberName == null ? memberName : fromMemberName;
    }

    private MessageEvent messageEvent(String messageId, String sender, String toMemberName) {
        MessageEvent event = new MessageEvent();
        event.setTeamName(teamName);
        event.setMessageId(messageId);
        event.setFromMemberName(sender);
        event.setToMemberName(toMemberName);
        return event;
    }

    private BroadcastEvent broadcastEvent(String messageId, String sender) {
        BroadcastEvent event = new BroadcastEvent();
        event.setTeamName(teamName);
        event.setMessageId(messageId);
        event.setFromMemberName(sender);
        return event;
    }

    private CompletionStage<Void> publishEvent(String messageId, EventMessage eventMessage) {
        String topicId = TeamTopic.MESSAGE.build(AgentTeamsContext.getSessionId(), teamName);
        CompletionStage<Void> publishStage;
        try {
            publishStage = messager.publish(topicId, eventMessage);
        } catch (Exception exception) {
            TEAM_LOGGER.error("Failed to publish message event for %s: %s", messageId, exception.getMessage());
            return CompletableFuture.completedFuture(null);
        }
        if (publishStage == null) {
            return CompletableFuture.completedFuture(null);
        }
        return publishStage.handle((ignored, exception) -> {
            if (exception == null) {
                TEAM_LOGGER.debug("Message event published: %s", messageId);
                return null;
            }
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            TEAM_LOGGER.error("Failed to publish message event for %s: %s", messageId, cause.getMessage());
            return null;
        });
    }

    private static MessageStore messageStoreFrom(TeamDatabase database) {
        Objects.requireNonNull(database, "database");
        if (database.getMessage() == null) {
            database.initialize().join();
        }
        MessageDao messageDao = database.getMessage();
        if (messageDao == null) {
            throw new IllegalStateException("TeamDatabase message DAO is not initialized");
        }
        return new MessageStore() {
            @Override
            public CompletionStage<Boolean> createMessage(
                    String messageId,
                    String teamName,
                    String fromMemberName,
                    String content,
                    String toMemberName,
                    boolean broadcast,
                    boolean isRead) {
                return messageDao.createMessage(
                        messageId,
                        teamName,
                        fromMemberName,
                        content,
                        toMemberName,
                        broadcast,
                        isRead
                );
            }

            @Override
            public CompletionStage<List<TeamMessage>> getMessages(
                    String teamName,
                    String toMemberName,
                    boolean unreadOnly,
                    String fromMemberName) {
                return messageDao.getMessages(teamName, toMemberName, unreadOnly, fromMemberName);
            }

            @Override
            public CompletionStage<List<TeamMessage>> getBroadcastMessages(
                    String teamName,
                    String memberName,
                    boolean unreadOnly,
                    String fromMemberName) {
                return messageDao.getBroadcastMessages(teamName, memberName, unreadOnly, fromMemberName);
            }

            @Override
            public CompletionStage<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
                return messageDao.getTeamMessages(teamName, broadcast);
            }

            @Override
            public CompletionStage<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast) {
                return messageDao.hasUnreadMessages(teamName, includeBroadcast);
            }

            @Override
            public CompletionStage<Boolean> markMessageRead(String messageId, String memberName) {
                return messageDao.markMessageRead(messageId, memberName);
            }
        };
    }

    private static MessageStore messageStoreFrom(InMemoryTeamDatabase database) {
        Objects.requireNonNull(database, "database");
        return new MessageStore() {
            @Override
            public CompletionStage<Boolean> createMessage(
                    String messageId,
                    String teamName,
                    String fromMemberName,
                    String content,
                    String toMemberName,
                    boolean broadcast,
                    boolean isRead) {
                return database.createMessage(
                        messageId,
                        teamName,
                        fromMemberName,
                        content,
                        toMemberName,
                        broadcast,
                        isRead
                );
            }

            @Override
            public CompletionStage<List<TeamMessage>> getMessages(
                    String teamName,
                    String toMemberName,
                    boolean unreadOnly,
                    String fromMemberName) {
                return database.getMessages(teamName, toMemberName, unreadOnly, fromMemberName);
            }

            @Override
            public CompletionStage<List<TeamMessage>> getBroadcastMessages(
                    String teamName,
                    String memberName,
                    boolean unreadOnly,
                    String fromMemberName) {
                return database.getBroadcastMessages(teamName, memberName, unreadOnly, fromMemberName);
            }

            @Override
            public CompletionStage<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
                return database.getTeamMessages(teamName, broadcast);
            }

            @Override
            public CompletionStage<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast) {
                return database.hasUnreadMessages(teamName, includeBroadcast);
            }

            @Override
            public CompletionStage<Boolean> markMessageRead(String messageId, String memberName) {
                return database.markMessageRead(messageId, memberName);
            }
        };
    }

    /**
     * Narrow DAO surface consumed by {@link TeamMessageManager}.
     *
     * <p>Mirrors Python's {@code TeamDatabase.message} usage in
     * {@code openjiuwen/agent_teams/tools/message_manager.py}.</p>
     */
    public interface MessageStore {
        CompletionStage<Boolean> createMessage(
                String messageId,
                String teamName,
                String fromMemberName,
                String content,
                String toMemberName,
                boolean broadcast,
                boolean isRead);

        CompletionStage<List<TeamMessage>> getMessages(
                String teamName,
                String toMemberName,
                boolean unreadOnly,
                String fromMemberName);

        CompletionStage<List<TeamMessage>> getBroadcastMessages(
                String teamName,
                String memberName,
                boolean unreadOnly,
                String fromMemberName);

        CompletionStage<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast);

        CompletionStage<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast);

        CompletionStage<Boolean> markMessageRead(String messageId, String memberName);
    }
}
