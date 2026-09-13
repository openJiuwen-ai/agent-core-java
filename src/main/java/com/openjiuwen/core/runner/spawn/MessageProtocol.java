/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * MessageProtocol.
 * 
 * @since 0.1.7
 */
public final class MessageProtocol {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * MessageProtocol.
     * 
     * @since 0.1.7
     */
    private MessageProtocol() {
    }

    /**
     * serializeMessageToStream.
     * 
     * @param message message
     * @param writer writer
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static void serializeMessageToStream(Message message, BufferedWriter writer) throws IOException {
        writer.write(OBJECT_MAPPER.writeValueAsString(message));
        writer.newLine();
        writer.flush();
    }

    /**
     * deserializeMessageFromStream.
     * 
     * @param reader reader
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static Message deserializeMessageFromStream(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                return OBJECT_MAPPER.readValue(line, Message.class);
            } catch (IOException ignored) {
                // Match Python protocol: child stdout may contain non-protocol logs.
            }
        }
        return null;
    }
}
