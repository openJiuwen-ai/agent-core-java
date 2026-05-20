/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.tools.database.MessageRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Public class TeamMessageManager used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamMessageManager {
    private final String teamName;
    private final String memberName;
    private final TeamDatabase db;
    private final Messager messager;
    private final Supplier<Set<String>> humanAgentNamesSupplier;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamMessageManager(String teamName, String memberName, Messager messager) {
        this(teamName, memberName, null, messager);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamMessageManager(String teamName, String memberName, TeamDatabase db, Messager messager) {
        this(teamName, memberName, db, messager, Set::of);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamMessageManager(
            String teamName,
            String memberName,
            TeamDatabase db,
            Messager messager,
            Supplier<Set<String>> humanAgentNamesSupplier
    ) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.db = db;
        this.messager = messager;
        this.humanAgentNamesSupplier = humanAgentNamesSupplier != null ? humanAgentNamesSupplier : Set::of;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> sendMessage(String content, String toMemberName) {
        return sendMessage(content, toMemberName, memberName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> sendMessage(String content, String toMemberName, String fromMemberName) {
        String messageId = UUID.randomUUID().toString();
        TeamMessage message = TeamMessage.builder()
                .messageId(messageId)
                .teamName(teamName)
                .fromMemberName(fromMemberName)
                .toMemberName(toMemberName)
                .content(content)
                .timestamp(TeamDatabase.getCurrentTime())
                .broadcast(false)
                .build();
        if (db != null) {
            boolean isAutoRead = humanAgentNamesSupplier.get().contains(toMemberName);
            db.message.createMessage(
                    messageId,
                    teamName,
                    fromMemberName,
                    content,
                    toMemberName,
                    false,
                    isAutoRead,
                    message.getTimestamp()
            );
        }
        return messager.publish(
                        "team:message",
                        EventMessage.builder()
                                .eventType("message")
                                .payload(Map.of(
                                        "message_id", messageId,
                                        "from_member_name", fromMemberName,
                                        "to_member_name", toMemberName
                                ))
                                .build()
                )
                .thenApply(ignored -> messageId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> broadcastMessage(String content) {
        return broadcastMessage(content, memberName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<String> broadcastMessage(String content, String fromMemberName) {
        String messageId = UUID.randomUUID().toString();
        TeamMessage message = TeamMessage.builder()
                .messageId(messageId)
                .teamName(teamName)
                .fromMemberName(fromMemberName)
                .content(content)
                .timestamp(TeamDatabase.getCurrentTime())
                .broadcast(true)
                .build();
        if (db != null) {
            db.message.createMessage(
                    messageId,
                    teamName,
                    fromMemberName,
                    content,
                    null,
                    true,
                    false,
                    message.getTimestamp()
            );
            for (String humanAgentName : humanAgentNamesSupplier.get()) {
                db.message.markMessageRead(messageId, humanAgentName);
            }
        }
        return messager.publish(
                        "team:broadcast",
                        EventMessage.builder()
                                .eventType("broadcast")
                                .payload(Map.of("message_id", messageId, "from_member_name", fromMemberName))
                                .build()
                )
                .thenApply(ignored -> messageId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TeamMessage> getMessages(String toMemberName, boolean isUnreadOnly) {
        if (db == null) {
            return List.of();
        }
        return db.message.getMessages(teamName, toMemberName, isUnreadOnly, null).stream()
                .map(this::toTeamMessage)
                .toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TeamMessage> getBroadcastMessages(boolean isUnreadOnly) {
        if (db == null) {
            return List.of();
        }
        return db.message.getBroadcastMessages(teamName, memberName, isUnreadOnly, null).stream()
                .map(this::toTeamMessage)
                .toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean markMessageRead(String messageId) {
        return markMessageRead(messageId, memberName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean markMessageRead(String messageId, String readerMemberName) {
        return db != null && db.message.markMessageRead(messageId, readerMemberName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void restoreMessages(List<TeamMessage> restoredMessages) {
        if (db == null) {
            return;
        }
        db.message.clearTeamMessages(teamName);
        if (restoredMessages != null) {
            for (TeamMessage message : restoredMessages) {
                db.message.createMessage(
                        message.getMessageId(),
                        message.getTeamName(),
                        message.getFromMemberName(),
                        message.getContent(),
                        message.getToMemberName(),
                        message.isBroadcast(),
                        message.isRead(),
                        message.getTimestamp()
                );
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TeamMessage> listAllMessages() {
        if (db == null) {
            return List.of();
        }
        return db.getTeamMessages(teamName).stream().map(this::toTeamMessage).toList();
    }

    private TeamMessage toTeamMessage(MessageRecord record) {
        return TeamMessage.builder()
                .messageId(record.getMessageId())
                .teamName(record.getTeamName())
                .fromMemberName(record.getFromMemberName())
                .toMemberName(record.getToMemberName())
                .content(record.getContent())
                .timestamp(record.getTimestamp())
                .broadcast(record.isBroadcast())
                .isRead(record.isRead())
                .build();
    }
}
