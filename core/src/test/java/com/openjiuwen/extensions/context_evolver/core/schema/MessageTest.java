/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void toDictAndFromDictMirrorPythonModelDumpBehavior() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", "ace");
        Message message = new Message(Role.USER, "hello", metadata);

        Map<String, Object> serialized = message.toDict();

        assertEquals("user", serialized.get("role"));
        assertEquals("hello", serialized.get("content"));
        assertEquals(Map.of("kind", "ace"), serialized.get("metadata"));

        Message roundTrip = Message.fromDict(serialized);
        assertEquals(Role.USER, roundTrip.getRole());
        assertEquals("hello", roundTrip.getContent());
        assertEquals(Map.of("kind", "ace"), roundTrip.getMetadata());
    }

    @Test
    void modelRemainsMutableAndReprUsesValuePreview() {
        Message message = new Message(
            Role.SYSTEM,
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        );

        message.setRole(Role.ASSISTANT);
        message.setContent("updated");
        Map<String, Object> metadata = message.getMetadata();
        metadata.put("mutated", true);

        assertEquals(Role.ASSISTANT, message.getRole());
        assertEquals("updated", message.getContent());
        assertEquals(Map.of("mutated", true), message.getMetadata());
        assertTrue(message.toString().startsWith("Message(role=assistant, content='updated"));
    }
}
