/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.database.MessageRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public class TeamMessageManager used by the Java parity implementation.
 *
 * <p>Mirrors Python 0.1.15 {@code tools/message_manager.py:TeamMessageManager}.
 * The {@code humanAgentNamesSupplier} parameter present in the 0.1.12 port
 * has been dropped: human-agent inbound dispatch now lives in
 * {@code MessageHandler._notify_human_agent_inbound} which calls
 * {@code TeamBackend.getHumanAgentInbound} + {@code TeamBackend.humanAgentNames}
 * directly. This manager creates every message with {@code is_read=false} and
 * leaves human-agent read-tracking to the handler layer.</p>
 *
 * @since 1.0
 */
public class TeamMessageManager {
    private final String teamName;
    private final String memberName;
    private final TeamDatabase db;
    private final Messager messager;
    private String teamSessionId;

    /**
     * Construct without an explicit database (messages are not persisted).
     *
     * @param teamName team identifier
     * @param memberName local member name
     * @param messager messager for publishing events
     */
    public TeamMessageManager(String teamName, String memberName, Messager messager) {
        this(teamName, memberName, null, messager, SpawnContext.getSessionId());
    }

    /**
     * Construct with a shared team database but default session id.
     *
     * @param teamName team identifier
     * @param memberName local member name
     * @param db shared team database; may be {@code null}
     * @param messager messager for publishing events
     */
    public TeamMessageManager(String teamName, String memberName, TeamDatabase db, Messager messager) {
        this(teamName, memberName, db, messager, SpawnContext.getSessionId());
    }

    /**
     * Construct with an explicit team-level session id used for topic routing.
     *
     * @param teamName team id
     * @param memberName local member name
     * @param db shared team database
     * @param messager messager
     * @param teamSessionId team-level session id (pinned, not affected by ReAct stream swaps)
     * @since 0.1.13
     */
    public TeamMessageManager(String teamName, String memberName, TeamDatabase db, Messager messager,
                              String teamSessionId) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.db = db;
        this.messager = messager;
        this.teamSessionId = teamSessionId != null ? teamSessionId : "";
    }

    /**
     * Latch the team-level session id after construction.
     *
     * @param sessionId team-level session id; {@code null} is ignored
     * @since 0.1.13
     */
    public void setTeamSessionId(String sessionId) {
        if (sessionId != null) {
            this.teamSessionId = sessionId;
        }
    }

    /**
     * Send a direct message from the local member to a named recipient.
     *
     * @param content message body
     * @param toMemberName target member name
     * @return a future that resolves to the assigned message id
     */
    public CompletableFuture<String> sendMessage(String content, String toMemberName) {
        return sendMessage(content, toMemberName, memberName);
    }

    /**
     * Send a direct message with an explicit sender name.
     *
     * @param content message body
     * @param toMemberName target member name
     * @param fromMemberName sender member name
     * @return a future that resolves to the assigned message id
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
            // Mirrors Python message_manager.send_message: every message is
            // created with is_read=False; human-agent inbound dispatch is the
            // handler layer's job (MessageHandler.notifyHumanAgentInbound).
            db.message.createMessage(
                    messageId,
                    teamName,
                    fromMemberName,
                    content,
                    toMemberName,
                    false,
                    false,
                    message.getTimestamp()
            );
        }
        return messager.publish(
                        messageTopic(),
                        EventMessage.builder()
                                .eventType(TeamEvent.MESSAGE)
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
     * Broadcast a message from the local member to all team members.
     *
     * @param content message body
     * @return a future that resolves to the assigned message id
     */
    public CompletableFuture<String> broadcastMessage(String content) {
        return broadcastMessage(content, memberName);
    }

    /**
     * Broadcast a message with an explicit sender name.
     *
     * @param content message body
     * @param fromMemberName sender member name
     * @return a future that resolves to the assigned message id
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
            // Mirrors Python broadcast_message: no per-human-agent pre-read
            // loop. Human agents receive inbound notification via the handler
            // path, same as any other member.
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
        }

        // Broadcast rides the same MESSAGE topic as direct messages (event_type
        // distinguishes them); mirrors Python message_manager.broadcast_message
        // publishing on TeamTopic.MESSAGE.build(session_id, team_name).
        return messager.publish(
                        messageTopic(),
                        EventMessage.builder()
                                .eventType(TeamEvent.BROADCAST)
                                .payload(Map.of("message_id", messageId, "from_member_name", fromMemberName))
                                .build()
                )
                .thenApply(ignored -> messageId);
    }

    /**
     * Retrieve direct messages addressed to a member.
     *
     * @param toMemberName recipient member name
     * @param isUnreadOnly whether to return only unread messages
     * @return list of matching messages, or empty list if no database
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
     * Retrieve broadcast messages visible to the local member.
     *
     * @param isUnreadOnly whether to return only unread messages
     * @return list of matching broadcast messages, or empty list if no database
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
     * Mark a single message as read for the local member.
     *
     * @param messageId the message id to mark read
     * @return {@code true} if the message was marked read, {@code false} if no database or not found
     */
    public boolean markMessageRead(String messageId) {
        return markMessageRead(messageId, memberName);
    }

    /**
     * Mark a single message as read for a specific reader member.
     *
     * @param messageId the message id to mark read
     * @param readerMemberName the member name reading the message
     * @return {@code true} if the message was marked read, {@code false} if no database or not found
     */
    public boolean markMessageRead(String messageId, String readerMemberName) {
        return db != null && db.message.markMessageRead(messageId, readerMemberName);
    }

    /**
     * Batch mark a list of messages read for one member.
     *
     * <p>Mirrors Python 0.1.15 {@code message_manager.mark_messages_read}.
     * Returns the number of messages successfully marked read. Used by
     * {@code MessageHandler._process_unread_messages} which collects
     * delivered ids and batch-marks them in its {@code finally} block.</p>
     *
     * @param messageIds the message ids to mark read
     * @param readerMemberName the member name reading the messages
     * @return the number of messages successfully marked read
     */
    public int markMessagesRead(List<String> messageIds, String readerMemberName) {
        if (db == null || messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        return db.message.markMessagesRead(messageIds, readerMemberName);
    }

    /**
     * Restore a list of messages by clearing existing team messages and re-inserting the provided ones.
     *
     * @param restoredMessages the messages to restore; may be {@code null}
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
     * List all messages in the team regardless of recipient or read state.
     *
     * @return list of all team messages, or empty list if no database
     */
    public List<TeamMessage> listAllMessages() {
        if (db == null) {
            return List.of();
        }
        return db.getTeamMessages(teamName).stream().map(this::toTeamMessage).toList();
    }

    /**
     * Return whether any message in the team is still unread.
     *
     * <p>Mirrors Python {@code message_manager.has_unread_messages(include_broadcast=True)}.
     * Used by {@code TeamBackend.isTeamCompleted()} as the third completion
     * condition: any undelivered message — direct or broadcast — blocks the
     * team from concluding.
     *
     * @param shouldIncludeBroadcast whether to consider broadcast messages
     * @return {@code true} if at least one unread message exists
     */
    public boolean hasUnreadMessages(boolean shouldIncludeBroadcast) {
        if (db == null) {
            return false;
        }
        for (TeamMessage message : listAllMessages()) {
            if (message.isRead()) {
                continue;
            }
            if (!shouldIncludeBroadcast && message.isBroadcast()) {
                continue;
            }
            return true;
        }
        return false;
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

    /**
     * Build the MESSAGE topic string, mirroring Python
     * {@code TeamTopic.MESSAGE.build(get_session_id(), team_name)}.
     *
     * @return the MESSAGE topic string
     */
    private String messageTopic() {
        return TeamTopic.MESSAGE.build(teamSessionId, teamName);
    }
}
