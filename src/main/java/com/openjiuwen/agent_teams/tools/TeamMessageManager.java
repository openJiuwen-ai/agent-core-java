/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public TeamMessageManager(String teamName, String memberName) {
        this(teamName, memberName, new LinkedHashMap<>(), null);
    }

    public TeamMessageManager(String teamName, String memberName, Map<String, MessageRecord> messages, Messager messager) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.messages = messages != null ? messages : new LinkedHashMap<>();
        this.messager = messager;
    }

    public String sendMessage(String content, String toMemberName, String fromMemberName) {
        String sender = fromMemberName != null && !fromMemberName.isBlank() ? fromMemberName : memberName;
        MessageRecord record = new MessageRecord(
                UUID.randomUUID().toString(),
                teamName,
                sender,
                toMemberName,
                content,
                false,
                false
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
                .filter(message -> !message.isBroadcast())
                .filter(message -> toMemberName == null || toMemberName.equals(message.getToMemberName()))
                .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                .filter(message -> !unreadOnly || !message.isRead())
                .toList();
    }

    public List<MessageRecord> getBroadcastMessages(boolean unreadOnly, String fromMemberName) {
        return messages.values().stream()
                .filter(MessageRecord::isBroadcast)
                .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                .filter(message -> !unreadOnly || !message.isRead())
                .toList();
    }

    public boolean markMessageRead(String messageId) {
        MessageRecord message = messages.get(messageId);
        if (message != null) {
            message.setRead(true);
            return true;
        }
        return false;
    }
}
