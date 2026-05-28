/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.cli.storage.CliSessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-15: Session persistence to JSON files.
 * <p>
 * Mirrors Python's {@code test_session_persist} in
 * {@code tests.cli.e2e.test_session_persist}.
 */
class SessionPersistE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmpPath;

    @Test
    void sessionPersistence() {
        CliSessionStore store = new CliSessionStore(tmpPath.toString());
        java.util.Map<String, Object> session = new java.util.LinkedHashMap<>();
        session.put("session_id", "e2e-test-001");

        java.util.Map<String, Object> msg1 = new java.util.LinkedHashMap<>();
        msg1.put("role", "user");
        msg1.put("content", "hello");

        java.util.Map<String, Object> msg2 = new java.util.LinkedHashMap<>();
        msg2.put("role", "assistant");
        msg2.put("content", "hi there");

        session.put("messages", java.util.List.of(msg1, msg2));
        store.save("e2e-test-001", session);

        java.util.Map<String, Object> loaded = store.load("e2e-test-001");
        assertEquals("e2e-test-001", loaded.get("session_id"));

        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> messages =
                (java.util.List<java.util.Map<String, Object>>) loaded.get("messages");
        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("assistant", messages.get(1).get("role"));
    }
}
