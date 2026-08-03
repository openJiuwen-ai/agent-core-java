/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Message envelope for routing between agents.
 * <p>
 * Mirrors Python's {@code MessageEnvelope} in
 * {@code openjiuwen/core/multi_agent/team_runtime/envelope.py}.
 */
public final class MessageEnvelope {

    private final String messageId;
    private final Object message;
    private final String sender;
    private final String recipient;
    private final String topicId;
    private final String sessionId;
    private final Map<String, Object> metadata;

    public MessageEnvelope(String messageId, Object message) {
        this(messageId, message, null, null, null, null, null);
    }

    public MessageEnvelope(
            String messageId,
            Object message,
            String sender,
            String recipient,
            String topicId,
            String sessionId,
            Map<String, Object> metadata
    ) {
        this.messageId = messageId;
        this.message = message;
        this.sender = sender;
        this.recipient = recipient;
        this.topicId = topicId;
        this.sessionId = sessionId;
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
    }

    public String getMessageId() {
        return messageId;
    }

    public Object getMessage() {
        return message;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getTopicId() {
        return topicId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isP2p() {
        return recipient != null;
    }

    public boolean isPubsub() {
        return topicId != null;
    }

    @Override
    public String toString() {
        return "MessageEnvelope(message_id=" + repr(messageId)
                + ", sender=" + repr(sender)
                + ", recipient=" + repr(recipient)
                + ", topic_id=" + repr(topicId)
                + ", session_id=" + repr(sessionId)
                + ", message=<" + messageTypeName() + ">)";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageEnvelope that)) {
            return false;
        }
        return Objects.equals(messageId, that.messageId)
                && Objects.equals(message, that.message)
                && Objects.equals(sender, that.sender)
                && Objects.equals(recipient, that.recipient)
                && Objects.equals(topicId, that.topicId)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, message, sender, recipient, topicId, sessionId, metadata);
    }

    private String messageTypeName() {
        return message == null ? "null" : message.getClass().getSimpleName();
    }

    private static String repr(String value) {
        return value == null ? "None" : "'" + value + "'";
    }
}
