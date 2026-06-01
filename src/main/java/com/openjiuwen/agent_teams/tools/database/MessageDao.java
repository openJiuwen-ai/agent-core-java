/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.tools.MessageReadStatus;
import com.openjiuwen.agent_teams.tools.TeamMessage;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Data access object for message and message-read-status tables.
 *
 * <p>Mirrors Python's {@code MessageDao} in
 * {@code openjiuwen.agent_teams.tools.database.message_dao}.</p>
 */
public class MessageDao {

    private static final Logger teamLogger = Logger.getLogger(MessageDao.class.getName());

    private final TeamDatabaseState state;

    public MessageDao() {
        this(new TeamDatabaseState(DatabaseConfig.inMemory()));
        this.state.createCurrentSessionTables();
    }

    public MessageDao(TeamDatabaseState state) {
        this.state = state;
    }

    public CompletableFuture<Optional<TeamMessage>> getMessage(String messageId) {
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(session().messages().get(messageId)));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> createMessage(
            String messageId,
            String teamName,
            String fromMemberName,
            String content,
            String toMemberName,
            boolean broadcast,
            boolean isRead) {
        try {
            TeamDatabaseState.SessionData session = session();
            if (session.messages().containsKey(messageId)) {
                teamLogger.severe(String.format("Failed to create %s, duplicate message id", messageId));
                return CompletableFuture.completedFuture(false);
            }
            TeamMessage message = new TeamMessage(
                    messageId,
                    teamName,
                    fromMemberName,
                    toMemberName,
                    content,
                    DatabaseEngine.getCurrentTime(),
                    broadcast,
                    broadcast ? null : isRead);
            boolean created = session.messages().putIfAbsent(messageId, message) == null;
            if (created) {
                teamLogger.info(String.format("Message %s created", messageId));
            }
            return CompletableFuture.completedFuture(created);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamMessage>> getMessages(
            String teamName,
            String toMemberName,
            boolean unreadOnly,
            String fromMemberName) {
        try {
            List<TeamMessage> rows = session().messages().values().stream()
                    .filter(message -> teamName.equals(message.getTeamName()))
                    .filter(message -> toMemberName.equals(message.getToMemberName()))
                    .filter(message -> !Boolean.TRUE.equals(message.getBroadcast()))
                    .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                    .filter(message -> !unreadOnly || Boolean.FALSE.equals(message.getIsRead()))
                    .sorted(Comparator.comparing(TeamMessage::getTimestamp))
                    .toList();
            return CompletableFuture.completedFuture(rows);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamMessage>> getBroadcastMessages(
            String teamName,
            String memberName,
            boolean unreadOnly,
            String fromMemberName) {
        try {
            TeamDatabaseState.SessionData session = session();
            TeamDatabaseState.ReadStatusKey key = new TeamDatabaseState.ReadStatusKey(memberName, teamName);
            MessageReadStatus readStatus = session.readStatuses().get(key);
            Long readAt = readStatus != null ? readStatus.getReadAt() : null;
            List<TeamMessage> rows = session.messages().values().stream()
                    .filter(message -> teamName.equals(message.getTeamName()))
                    .filter(message -> Boolean.TRUE.equals(message.getBroadcast()))
                    .filter(message -> !memberName.equals(message.getFromMemberName()))
                    .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                    .filter(message -> !unreadOnly || readAt == null || message.getTimestamp() > readAt)
                    .sorted(Comparator.comparing(TeamMessage::getTimestamp))
                    .toList();
            return CompletableFuture.completedFuture(rows);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
        try {
            List<TeamMessage> rows = session().messages().values().stream()
                    .filter(message -> teamName.equals(message.getTeamName()))
                    .filter(message -> broadcast == null || broadcast.equals(message.getBroadcast()))
                    .sorted(Comparator.comparing(TeamMessage::getTimestamp))
                    .toList();
            return CompletableFuture.completedFuture(rows);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> markMessageRead(String messageId, String memberName) {
        try {
            TeamDatabaseState.SessionData session = session();
            TeamMessage message = session.messages().get(messageId);
            if (message == null) {
                teamLogger.severe(String.format("Message %s not found", messageId));
                return CompletableFuture.completedFuture(false);
            }
            if ("user".equals(memberName)) {
                if (Boolean.TRUE.equals(message.getBroadcast())) {
                    teamLogger.severe(String.format("'user' pseudo-member cannot read broadcast message %s", messageId));
                    return CompletableFuture.completedFuture(false);
                }
            } else {
                TeamDatabaseState.MemberKey memberKey =
                        new TeamDatabaseState.MemberKey(memberName, message.getTeamName());
                if (!state.members().containsKey(memberKey)) {
                    teamLogger.severe(String.format("Member %s not found", memberName));
                    return CompletableFuture.completedFuture(false);
                }
            }

            if (Boolean.TRUE.equals(message.getBroadcast())) {
                TeamDatabaseState.ReadStatusKey key =
                        new TeamDatabaseState.ReadStatusKey(memberName, message.getTeamName());
                session.readStatuses().compute(key, (ignored, existing) -> {
                    if (existing == null) {
                        return new MessageReadStatus(memberName, message.getTeamName(), message.getTimestamp());
                    }
                    if (existing.getReadAt() == null || message.getTimestamp() > existing.getReadAt()) {
                        existing.setReadAt(message.getTimestamp());
                    }
                    return existing;
                });
            } else {
                message.setIsRead(true);
            }
            teamLogger.info(String.format("Message %s marked as read by %s", messageId, memberName));
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private TeamDatabaseState.SessionData session() {
        return state.currentSession();
    }
}
