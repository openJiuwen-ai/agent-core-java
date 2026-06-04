/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal in-memory team message manager.
 *
 * <p>Mirrors Python's {@code TeamMessageManager} in
 * {@code openjiuwen.agent_teams.tools.message_manager}.
 */
public class TeamMessageManager {

    private final String teamName;
    private final String memberName;
    private final Map<String, MessageRecord> messages;
    private final Messager messager;
    private final Set<String> humanAgentNames;
    private final Map<String, Map<String, Long>> broadcastReadAt = new LinkedHashMap<>();

    public TeamMessageManager(String teamName, String memberName) {
        this(teamName, memberName, new LinkedHashMap<>(), null, Set.of());
    }

    public TeamMessageManager(String teamName, String memberName, Map<String, MessageRecord> messages, Messager messager) {
        this(teamName, memberName, messages, messager, Set.of());
    }

    public TeamMessageManager(
            String teamName,
            String memberName,
            Map<String, MessageRecord> messages,
            Messager messager,
            Set<String> humanAgentNames
    ) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.messages = messages != null ? messages : new LinkedHashMap<>();
        this.messager = messager;
        this.humanAgentNames = humanAgentNames != null ? humanAgentNames : new LinkedHashSet<>();
    }

    public String sendMessage(String content, String toMemberName, String fromMemberName) {
        String sender = fromMemberName != null && !fromMemberName.isBlank() ? fromMemberName : memberName;
        boolean autoRead = toMemberName != null && humanAgentNames.contains(toMemberName);
        MessageRecord record = new MessageRecord(
                UUID.randomUUID().toString(),
                teamName,
                sender,
                toMemberName,
                content,
                false,
                autoRead
        );
        messages.put(record.getMessageId(), record);
        if (messager != null && toMemberName != null && !toMemberName.isBlank()) {
            EventMessage event = new EventMessage("direct_message", Map.of(
                    "message_id", record.getMessageId(),
                    "team_name", teamName,
                    "from_member_name", sender,
                    "to_member_name", toMemberName,
                    "content", content
            ));
            messager.send(toMemberName, event);
        }
        return record.getMessageId();
    }

    public String broadcastMessage(String content, String fromMemberName) {
        String sender = fromMemberName != null && !fromMemberName.isBlank() ? fromMemberName : memberName;
        MessageRecord record = new MessageRecord(
                UUID.randomUUID().toString(),
                teamName,
                sender,
                null,
                content,
                true,
                false
        );
        messages.put(record.getMessageId(), record);
        for (String humanName : humanAgentNames) {
            markMessageRead(record.getMessageId(), humanName);
        }
        if (messager != null) {
            EventMessage event = new EventMessage("broadcast_message", Map.of(
                    "message_id", record.getMessageId(),
                    "team_name", teamName,
                    "from_member_name", sender,
                    "content", content
            ));
            messager.publish(TeamTopic.MESSAGE.build("shared", teamName), event);
        }
        return record.getMessageId();
    }

    public List<MessageRecord> getMessages(String toMemberName, boolean unreadOnly, String fromMemberName) {
        return messages.values().stream()
                .filter(message -> teamName.equals(message.getTeamName()))
                .filter(message -> !message.isBroadcast())
                .filter(message -> toMemberName == null || toMemberName.equals(message.getToMemberName()))
                .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                .filter(message -> !unreadOnly || !message.isRead())
                .toList();
    }

    public List<MessageRecord> getBroadcastMessages(boolean unreadOnly, String fromMemberName) {
        return getBroadcastMessages(memberName, unreadOnly, fromMemberName);
    }

    public List<MessageRecord> getBroadcastMessages(String targetMemberName, boolean unreadOnly, String fromMemberName) {
        return messages.values().stream()
                .filter(message -> teamName.equals(message.getTeamName()))
                .filter(MessageRecord::isBroadcast)
                .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                .filter(message -> !unreadOnly || !isBroadcastRead(message, targetMemberName))
                .toList();
    }

    public boolean markMessageRead(String messageId) {
        return markMessageRead(messageId, memberName);
    }

    public boolean markMessageRead(String messageId, String targetMemberName) {
        MessageRecord message = messages.get(messageId);
        if (message == null) {
            return false;
        }
        if (message.isBroadcast()) {
            String reader = targetMemberName != null && !targetMemberName.isBlank() ? targetMemberName : memberName;
            broadcastReadAt
                    .computeIfAbsent(teamName, ignored -> new LinkedHashMap<>())
                    .merge(reader, message.getCreatedAt(), Math::max);
        } else {
            message.setRead(true);
        }
        return true;
    }

    private boolean isBroadcastRead(MessageRecord message, String targetMemberName) {
        if (message.isRead()) {
            return true;
        }
        String reader = targetMemberName != null && !targetMemberName.isBlank() ? targetMemberName : memberName;
        Map<String, Long> teamReadAt = broadcastReadAt.get(teamName);
        Long readAt = teamReadAt != null ? teamReadAt.get(reader) : null;
        return readAt != null && message.getCreatedAt() <= readAt;
    }
}
