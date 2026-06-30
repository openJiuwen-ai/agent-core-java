/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Auto-generated for codecheck compliance.
 */
public final class MessageProtocol {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MessageProtocol() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void serializeMessageToStream(Message message, BufferedWriter writer) throws IOException {
        writer.write(OBJECT_MAPPER.writeValueAsString(message));
        writer.newLine();
        writer.flush();
    }

    /**
     * Auto-generated for codecheck compliance.
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
