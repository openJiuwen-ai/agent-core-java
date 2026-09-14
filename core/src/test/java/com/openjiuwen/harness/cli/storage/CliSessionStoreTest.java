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

/**
 * <p>Mirrors Python's {@code tests/cli/unit/test_session_store.py}.</p>
 *
 * <p>Mirrors Python's {@code test_session_persistence} in
 * {@code tests/cli/e2e/test_session_persist.py}.</p>
 */
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
    void e2eSessionPersistenceCreatesJsonFile() throws IOException {
        Path storeDir = tempDir.resolve("sessions");
        CliSessionStore store = new CliSessionStore(storeDir);
        store.newSession("e2e-test-001", "Pro/zai-org/GLM-5");
        store.addMessage("user", "hello");
        store.addMessage("assistant", "hi there");

        List<Path> jsonFiles;
        try (var files = Files.list(storeDir)) {
            jsonFiles = files.filter(path -> path.getFileName().toString().endsWith(".json")).toList();
        }
        assertTrue(jsonFiles.size() >= 1);

        Map<String, Object> data = OBJECT_MAPPER.readValue(
                Files.readString(jsonFiles.get(0)),
                new TypeReference<Map<String, Object>>() {
                }
        );
        assertEquals("e2e-test-001", data.get("session_id"));
        assertTrue(data.containsKey("messages"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
        assertTrue(messages.size() >= 2);
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("assistant", messages.get(1).get("role"));
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
    void messageHasIsoTimestamp() throws IOException {
        CliSessionStore store = new CliSessionStore(tempDir);
        store.newSession("test-ts", "gpt-4o");
        store.addMessage("user", "test");

        Map<String, Object> data = OBJECT_MAPPER.readValue(
                Files.readString(tempDir.resolve("test-ts.json")),
                new TypeReference<Map<String, Object>>() {
                }
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");

        OffsetDateTime.parse((String) messages.get(0).get("timestamp"));
    }

    @Test
    void sessionJsonContainsMetadata() throws IOException {
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
    }
}
