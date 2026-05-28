/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Controller Persistence.
 * <p>
 * Mirrors Python's test_controller_persistence.py from
 * <code>tests/unit_tests/core/controller/test_controller_persistence.py</code>.
 */
@DisplayName("Controller Persistence Tests")
class TestControllerPersistence {

    // Stub classes
    static class TaskStateStub {
        String taskId;
        String status;
        Map<String, Object> data;

        TaskStateStub(String taskId, String status) {
            this.taskId = taskId;
            this.status = status;
            this.data = new HashMap<>();
        }
    }

    static class SessionStateStub {
        String sessionId;
        Map<String, TaskStateStub> tasks;

        SessionStateStub(String sessionId) {
            this.sessionId = sessionId;
            this.tasks = new HashMap<>();
        }

        void addTask(TaskStateStub task) {
            tasks.put(task.taskId, task);
        }
    }

    static class PersistenceStore {
        Map<String, SessionStateStub> sessions = new HashMap<>();

        void saveSession(SessionStateStub session) {
            sessions.put(session.sessionId, session);
        }

        SessionStateStub loadSession(String sessionId) {
            return sessions.get(sessionId);
        }

        void deleteSession(String sessionId) {
            sessions.remove(sessionId);
        }

        void updateTaskStatus(String sessionId, String taskId, String status) {
            SessionStateStub session = sessions.get(sessionId);
            if (session != null && session.tasks.containsKey(taskId)) {
                session.tasks.get(taskId).status = status;
            }
        }
    }

    @Nested
    @DisplayName("Session Persistence Tests")
    class TestSessionPersistence {

        @Test
        @DisplayName("save session to store")
        void testSaveSessionToStore() {
            PersistenceStore store = new PersistenceStore();
            SessionStateStub session = new SessionStateStub("session-1");
            session.addTask(new TaskStateStub("task-1", "pending"));

            store.saveSession(session);

            assertNotNull(store.loadSession("session-1"));
        }

        @Test
        @DisplayName("load session from store")
        void testLoadSessionFromStore() {
            PersistenceStore store = new PersistenceStore();
            SessionStateStub session = new SessionStateStub("session-2");
            session.addTask(new TaskStateStub("task-2", "running"));
            store.saveSession(session);

            SessionStateStub loaded = store.loadSession("session-2");

            assertEquals("session-2", loaded.sessionId);
            assertTrue(loaded.tasks.containsKey("task-2"));
        }

        @Test
        @DisplayName("delete session from store")
        void testDeleteSessionFromStore() {
            PersistenceStore store = new PersistenceStore();
            SessionStateStub session = new SessionStateStub("session-3");
            store.saveSession(session);

            store.deleteSession("session-3");

            assertNull(store.loadSession("session-3"));
        }
    }

    @Nested
    @DisplayName("Task Status Update Tests")
    class TestTaskStatusUpdate {

        @Test
        @DisplayName("update task status")
        void testUpdateTaskStatus() {
            PersistenceStore store = new PersistenceStore();
            SessionStateStub session = new SessionStateStub("session-4");
            session.addTask(new TaskStateStub("task-4", "pending"));
            store.saveSession(session);

            store.updateTaskStatus("session-4", "task-4", "completed");

            SessionStateStub loaded = store.loadSession("session-4");
            assertEquals("completed", loaded.tasks.get("task-4").status);
        }

        @Test
        @DisplayName("update non-existent task does nothing")
        void testUpdateNonExistentTaskDoesNothing() {
            PersistenceStore store = new PersistenceStore();
            SessionStateStub session = new SessionStateStub("session-5");
            store.saveSession(session);

            store.updateTaskStatus("session-5", "non-existent", "completed");

            // No exception should be thrown
            assertNotNull(store.loadSession("session-5"));
        }
    }
}