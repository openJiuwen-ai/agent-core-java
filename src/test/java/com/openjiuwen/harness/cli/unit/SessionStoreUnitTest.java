/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.storage.CliSessionStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JSON session storage.
 * <p>
 * Mirrors Python's {@code test_session_store} in
 * {@code tests.cli.unit.test_session_store}.
 */
class SessionStoreUnitTest {

    @Test
    void newSession() {
        CliSessionStore store = new CliSessionStore("/tmp/test-sessions");
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("session_id", "test-001");
        store.save("test-001", session);
        assertTrue(store.listSessions().contains("test-001"));
    }

    @Test
    void addMessageAndSave() {
        CliSessionStore store = new CliSessionStore("/tmp/test-sessions-2");
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("session_id", "test-002");

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg1 = new LinkedHashMap<>();
        msg1.put("role", "user");
        msg1.put("content", "hello");
        messages.add(msg1);

        Map<String, Object> msg2 = new LinkedHashMap<>();
        msg2.put("role", "assistant");
        msg2.put("content", "hi");
        messages.add(msg2);

        session.put("messages", messages);
        store.save("test-002", session);

        Map<String, Object> loaded = store.load("test-002");
        assertNotNull(loaded);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> loadedMsgs = (List<Map<String, Object>>) loaded.get("messages");
        assertEquals(2, loadedMsgs.size());
        assertEquals("user", loadedMsgs.get(0).get("role"));
        assertEquals("hello", loadedMsgs.get(0).get("content"));
        assertEquals("assistant", loadedMsgs.get(1).get("role"));
        assertEquals("hi", loadedMsgs.get(1).get("content"));
    }

    @Test
    void listSessions() {
        CliSessionStore store = new CliSessionStore("/tmp/test-sessions-3");
        store.save("s1", Map.of("session_id", "s1"));
        store.save("s2", Map.of("session_id", "s2"));

        Set<String> sessions = store.listSessions();
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("s1"));
        assertTrue(sessions.contains("s2"));
    }

    @Test
    void addMessageWithoutSessionDoesNotCrash() {
        CliSessionStore store = new CliSessionStore("/tmp/test-sessions-4");
        Map<String, Object> loaded = store.load("nonexistent");
        assertTrue(loaded.isEmpty());
    }

    @Test
    void messageHasTimestamp() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertNotNull(timestamp);
        assertTrue(timestamp.length() > 10);
    }

    @Test
    void deleteSession() {
        CliSessionStore store = new CliSessionStore("/tmp/test-sessions-5");
        store.save("to-delete", Map.of("session_id", "to-delete"));
        assertTrue(store.listSessions().contains("to-delete"));
        store.delete("to-delete");
        assertFalse(store.listSessions().contains("to-delete"));
    }
}
