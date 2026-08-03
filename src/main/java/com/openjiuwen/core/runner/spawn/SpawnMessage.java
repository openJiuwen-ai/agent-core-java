/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Message data structure for async process communication.
 *
 * <p>Mirrors Python's {@code Message} in
 * {@code openjiuwen/core/runner/spawn/protocol.py}.</p>
 */
public class SpawnMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SpawnMessageType type;
    private final Object payload;
    private final Instant timestamp;
    private final String messageId;

    public SpawnMessage(SpawnMessageType type, Object payload) {
        this(type, payload, Instant.now(), null);
    }

    public SpawnMessage(SpawnMessageType type, Object payload, Instant timestamp, String messageId) {
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
        this.messageId = messageId;
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

    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type.name());
        data.put("payload", payload);
        data.put("timestamp", timestamp.toString());
        data.put("message_id", messageId);
        return data;
    }

    public static SpawnMessage fromMap(Map<String, Object> data) {
        return new SpawnMessage(
                SpawnMessageType.fromValue((String) data.get("type")),
                data.get("payload"),
                Instant.parse((String) data.get("timestamp")),
                (String) data.get("message_id")
        );
    }

    public static byte[] serializeMessage(SpawnMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message.toMap()).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize spawn message", exception);
        }
    }

    public static SpawnMessage deserializeMessage(byte[] data) {
        try {
            Map<String, Object> obj = OBJECT_MAPPER.readValue(data, new TypeReference<>() {
            });
            return fromMap(obj);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to deserialize spawn message", exception);
        }
    }

    public static void serializeMessageToStream(SpawnMessage message, Writer writer) throws IOException {
        writer.write(new String(serializeMessage(message), StandardCharsets.UTF_8));
        writer.write('\n');
        writer.flush();
    }

    public static SpawnMessage deserializeMessageFromStream(BufferedReader reader) throws IOException {
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            try {
                return deserializeMessage(line.getBytes(StandardCharsets.UTF_8));
            } catch (RuntimeException ignored) {
                // Child stdout can contain pre-protocol logs; skip them like the Python runtime.
            }
        }
    }
}
