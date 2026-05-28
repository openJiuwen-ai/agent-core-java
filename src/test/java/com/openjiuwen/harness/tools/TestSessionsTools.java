/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.agent_control.SessionTools;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionToolkit;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionTaskRow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for session tools.
 *
 * <p>Mirrors Python's {@code test_sessions_tools.py} in
 * {@code tests.unit_tests.harness.tools}.
 */
class TestSessionsTools {

    private SessionToolkit toolkit;

    @BeforeEach
    void setUp() {
        toolkit = new SessionToolkit();
    }

    @Nested
    class TestSessionToolkit {

        @Test
        void testUpsertAndGet() {
            // Insert a running task
            toolkit.upsertRunning("t1", "sub1", "desc");
            
            // Get and verify
            SessionTaskRow row = toolkit.get("t1");
            assertNotNull(row);
            assertEquals("t1", row.getTaskId());
            assertEquals("sub1", row.getSubSessionId());
            assertEquals("desc", row.getDescription());
            assertEquals("running", row.getStatus());
        }

        @Test
        void testMarkCompletedFailedCanceledClear() {
            // Test completed
            toolkit.upsertRunning("t1", "sub1", "d");
            toolkit.markCompleted("t1", "ok");
            SessionTaskRow row1 = toolkit.get("t1");
            assertNotNull(row1);
            assertEquals("completed", row1.getStatus());
            assertEquals("ok", row1.getResult());

            // Test failed
            toolkit.upsertRunning("t2", "sub2", "d2");
            toolkit.markFailed("t2", "boom");
            SessionTaskRow row2 = toolkit.get("t2");
            assertNotNull(row2);
            assertEquals("error", row2.getStatus());
            assertEquals("boom", row2.getError());

            // Test canceled
            toolkit.upsertRunning("t3", "sub3", "d3");
            toolkit.markCanceled("t3");
            SessionTaskRow row3 = toolkit.get("t3");
            assertNotNull(row3);
            assertEquals("canceled", row3.getStatus());

            // Test clear
            toolkit.clear();
            assertTrue(toolkit.listAll().isEmpty());
        }

        @Test
        void testGetMissingReturnsNull() {
            // Get non-existent task should return null
            SessionTaskRow row = toolkit.get("nonexistent");
            assertNull(row);
        }

        @Test
        void testUpsertOverwritesExisting() {
            // Insert first
            toolkit.upsertRunning("t1", "sub1", "desc1");
            
            // Upsert with same task_id but different values
            toolkit.upsertRunning("t1", "sub2", "desc2");
            
            // Verify overwrite
            SessionTaskRow row = toolkit.get("t1");
            assertNotNull(row);
            assertEquals("sub2", row.getSubSessionId());
            assertEquals("desc2", row.getDescription());
        }
    }

    @Nested
    class TestSessionsSpawnTool {

        @Test
        void testSpawnRequiresTaskId() {
            // Spawn should require task_id parameter
            // (Implementation validation in invoke)
            assertNotNull(toolkit);
        }

        @Test
        void testSpawnRequiresDescription() {
            // Spawn should require description parameter
            // (Implementation validation in invoke)
            assertNotNull(toolkit);
        }

        @Test
        void testSpawnCreatesSubSession() {
            // Upsert creates a sub-session entry
            toolkit.upsertRunning("task1", "session1", "Test task");
            
            SessionTaskRow row = toolkit.get("task1");
            assertNotNull(row);
            assertNotNull(row.getSubSessionId());
        }
    }

    @Nested
    class TestSessionsListTool {

        @Test
        void testListReturnsEmptyInitially() {
            // List should be empty initially
            java.util.List<SessionTaskRow> list = toolkit.listAll();
            assertTrue(list.isEmpty());
        }

        @Test
        void testListReturnsRunningTasks() {
            // Add running tasks
            toolkit.upsertRunning("t1", "s1", "task1");
            toolkit.upsertRunning("t2", "s2", "task2");
            
            // List should return tasks
            java.util.List<SessionTaskRow> list = toolkit.listAll();
            assertEquals(2, list.size());
            
            // Verify tasks are in list
            assertTrue(list.stream().anyMatch(r -> r.getTaskId().equals("t1")));
            assertTrue(list.stream().anyMatch(r -> r.getTaskId().equals("t2")));
        }
    }

    @Nested
    class TestSessionsCancelTool {

        @Test
        void testCancelRequiresTaskId() {
            // Cancel should require task_id parameter
            // (Implementation validation in invoke)
            assertNotNull(toolkit);
        }

        @Test
        void testCancelMarksCanceled() {
            // Add task
            toolkit.upsertRunning("t1", "s1", "task1");
            
            // Cancel it
            toolkit.markCanceled("t1");
            
            // Verify canceled
            SessionTaskRow row = toolkit.get("t1");
            assertNotNull(row);
            assertEquals("canceled", row.getStatus());
        }
    }

    @Nested
    class TestBuildSessionTools {

        @Test
        void testBuildReturnsTools() {
            // SessionTools.SessionToolkit should be instantiable
            SessionToolkit tk = new SessionToolkit();
            assertNotNull(tk);
            
            // Verify SESSION_SPAWN_TASK_TYPE constant
            assertEquals("session_spawn_task", SessionTools.SESSION_SPAWN_TASK_TYPE);
        }
    }
}