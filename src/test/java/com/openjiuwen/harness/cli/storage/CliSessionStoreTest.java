/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliSessionStoreTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void newSessionCreatesCurrentRecord() {
        CliSessionStore store = new CliSessionStore(tempDir);
        store.newSession("test-001", "gpt-4o");

        CliSessionStore.StoredSession current = store.getCurrentSession();
        assertNotNull(current);
        assertEquals("test-001", current.sessionId());
        assertEquals("gpt-4o", current.model());
    }

    @Test
    void addMessagePersistsJsonSessionFile() throws IOException {
        CliSessionStore store = new CliSessionStore(tempDir);
        store.newSession("test-002", "gpt-4o");
        store.addMessage("user", "hello");
        store.addMessage("assistant", "hi");

        Path sessionFile = tempDir.resolve("test-002.json");
        assertTrue(Files.exists(sessionFile));

        Map<String, Object> data = OBJECT_MAPPER.readValue(
                Files.readString(sessionFile),
                new TypeReference<Map<String, Object>>() {
                }
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("hello", messages.get(0).get("content"));
        assertEquals("assistant", messages.get(1).get("role"));
        assertEquals("hi", messages.get(1).get("content"));
    }

    @Test
    void listSessionsReturnsPersistedSummaries() {
        CliSessionStore store = new CliSessionStore(tempDir);
        store.newSession("s1", "gpt-4o");
        store.addMessage("user", "q1");
        store.newSession("s2", "qwen-max");
        store.addMessage("user", "q2");

        List<Map<String, Object>> sessions = store.listSessions();
        assertEquals(2, sessions.size());
        Set<Object> ids = Set.of(sessions.get(0).get("id"), sessions.get(1).get("id"));
        assertTrue(ids.contains("s1"));
        assertTrue(ids.contains("s2"));
    }

    @Test
    void addMessageWithoutSessionDoesNotCreateFiles() throws IOException {
        CliSessionStore store = new CliSessionStore(tempDir);
        store.addMessage("user", "hello");

        try (var files = Files.list(tempDir)) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    void savedSessionContainsIsoTimestampsAndMetadata() throws IOException {
        CliSessionStore store = new CliSessionStore(tempDir);
        store.newSession("meta-test", "gpt-4o");
        store.addMessage("user", "x");

        Map<String, Object> data = OBJECT_MAPPER.readValue(
                Files.readString(tempDir.resolve("meta-test.json")),
                new TypeReference<Map<String, Object>>() {
                }
        );
        assertEquals("meta-test", data.get("session_id"));
        assertEquals("gpt-4o", data.get("model"));
        OffsetDateTime.parse((String) data.get("created_at"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
        OffsetDateTime.parse((String) messages.get(0).get("timestamp"));
    }
}
