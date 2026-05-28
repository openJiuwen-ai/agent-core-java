/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Message envelope for routing between agents.
 * <p>
 * Mirrors Python's {@code MessageEnvelope} dataclass from
 * <code>multi_agent/team_runtime/envelope.py</code>.
 *
 * <p>Lightweight message container for routing between agents,
 * supporting both P2P and Pub-Sub patterns.
 */
public final class MessageEnvelope {

    private final String messageId;
    private final Object message;
    private final String sender;
    private final String recipient;
    private final String topicId;
    private final String sessionId;
    private final Map<String, Object> metadata;

    public MessageEnvelope(String messageId, Object message, String sender, 
            String recipient, String topicId, String sessionId) {
        this.messageId = messageId;
        this.message = message;
        this.sender = sender;
        this.recipient = recipient;
        this.topicId = topicId;
        this.sessionId = sessionId;
        this.metadata = new HashMap<>();
    }

    public MessageEnvelope(Object message, String sender, String recipient) {
        this.messageId = UUID.randomUUID().toString();
        this.message = message;
        this.sender = sender;
        this.recipient = recipient;
        this.topicId = null;
        this.sessionId = null;
        this.metadata = new HashMap<>();
    }

    /**
     * Check if this is a P2P message.
     */
    public boolean isP2P() {
        return recipient != null;
    }

    /**
     * Check if this is a Pub-Sub message.
     */
    public boolean isPubSub() {
        return topicId != null;
    }

    // Getters
    public String getMessageId() { return messageId; }
    public Object getMessage() { return message; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getTopicId() { return topicId; }
    public String getSessionId() { return sessionId; }
    public Map<String, Object> getMetadata() { return metadata; }

    public MessageEnvelope withMetadata(String key, Object value) {
        MessageEnvelope newEnvelope = new MessageEnvelope(messageId, message, 
            sender, recipient, topicId, sessionId);
        newEnvelope.metadata.putAll(this.metadata);
        newEnvelope.metadata.put(key, value);
        return newEnvelope;
    }

    @Override
    public String toString() {
        return "MessageEnvelope(messageId=" + messageId + 
            ", sender=" + sender + ", recipient=" + recipient + 
            ", topicId=" + topicId + ", sessionId=" + sessionId + ")";
    }
}