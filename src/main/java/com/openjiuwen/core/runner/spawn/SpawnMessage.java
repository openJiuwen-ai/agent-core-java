/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Message data structure for async process communication between parent and spawned processes.
 * <p>
 * Mirrors Python's {@code Message} in {@code runner/spawn/protocol.py}.
 * <p>
 * Messages are serialized to JSON for stdin/stdout communication with child processes.
 */
public class SpawnMessage {

    private final SpawnMessageType type;
    private final Object payload;
    private final Instant timestamp;
    private final String messageId;

    /**
     * Create a new message with auto-generated timestamp and ID.
     *
     * @param type    the message type
     * @param payload the message payload (must be JSON-serializable)
     */
    public SpawnMessage(SpawnMessageType type, Object payload) {
        this(type, payload, Instant.now(), UUID.randomUUID().toString());
    }

    /**
     * Create a new message with explicit fields.
     *
     * @param type      the message type
     * @param payload   the message payload
     * @param timestamp the creation timestamp
     * @param messageId the unique message identifier
     */
    public SpawnMessage(SpawnMessageType type, Object payload, Instant timestamp, String messageId) {
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
        this.messageId = messageId;
    }

    /**
     * Convert this message to a JSON-serializable map.
     *
     * @return a map representation suitable for JSON serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type.getValue());
        map.put("payload", payload);
        map.put("timestamp", timestamp.toString());
        map.put("message_id", messageId);
        return map;
    }

    /**
     * Create a SpawnMessage from a deserialized map.
     *
     * @param data the map containing message fields
     * @return the reconstructed SpawnMessage
     */
    @SuppressWarnings("unchecked")
    public static SpawnMessage fromMap(Map<String, Object> data) {
        SpawnMessageType msgType = SpawnMessageType.fromValue((String) data.get("type"));
        Instant ts = Instant.parse((String) data.get("timestamp"));
        String msgId = (String) data.get("message_id");
        Object payload = data.get("payload");
        return new SpawnMessage(msgType, payload, ts, msgId);
    }

    public SpawnMessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return "SpawnMessage{type=" + type + ", messageId='" + messageId + "', timestamp=" + timestamp + "}";
    }
}
