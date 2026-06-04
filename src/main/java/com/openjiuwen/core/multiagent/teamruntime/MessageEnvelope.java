/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Message envelope for routing between agents.
 * <p>
 * Mirrors Python's {@code MessageEnvelope} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.envelope}.
 * <p>
 * Immutable message container for routing between agents.
 * Supports both P2P (point-to-point) and Pub-Sub patterns.
 * <p>
 * Attributes:
 * <ul>
 *     <li>messageId: Unique message identifier</li>
 *     <li>message: Message payload</li>
 *     <li>sender: Sender agent ID (optional)</li>
 *     <li>recipient: Recipient agent ID (optional, for P2P)</li>
 *     <li>topicId: Topic ID (optional, for Pub-Sub)</li>
 *     <li>sessionId: Session ID (optional)</li>
 *     <li>metadata: Additional metadata</li>
 * </ul>
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
        this(messageId, message, null, null, null, null, new HashMap<>());
    }
    
    public MessageEnvelope(String messageId, Object message, String sender, 
                           String recipient, String topicId, String sessionId,
                           Map<String, Object> metadata) {
        this.messageId = messageId;
        this.message = message;
        this.sender = sender;
        this.recipient = recipient;
        this.topicId = topicId;
        this.sessionId = sessionId;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    // Getters
    public String getMessageId() { return messageId; }
    public Object getMessage() { return message; }
    public Optional<String> getSender() { return Optional.ofNullable(sender); }
    public Optional<String> getRecipient() { return Optional.ofNullable(recipient); }
    public Optional<String> getTopicId() { return Optional.ofNullable(topicId); }
    public Optional<String> getSessionId() { return Optional.ofNullable(sessionId); }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    
    /**
     * Check if this is a P2P message.
     * 
     * @return true if recipient is specified
     */
    public boolean isP2p() {
        return recipient != null;
    }

    /**
     * Python-style alias for {@link #isP2p()}.
     *
     * @return true if recipient is specified
     */
    public boolean isP2P() {
        return isP2p();
    }
    
    /**
     * Check if this is a Pub-Sub message.
     * 
     * @return true if topicId is specified
     */
    public boolean isPubsub() {
        return topicId != null;
    }

    /**
     * Python-style alias for {@link #isPubsub()}.
     *
     * @return true if topicId is specified
     */
    public boolean isPubSub() {
        return isPubsub();
    }
    
    @Override
    public String toString() {
        String messageType = message != null ? message.getClass().getSimpleName() : "null";
        return String.format("MessageEnvelope(messageId=%s, sender=%s, recipient=%s, " +
                             "topicId=%s, sessionId=%s, message=<%s>)",
                             messageId, sender, recipient, topicId, sessionId, messageType);
    }
}
