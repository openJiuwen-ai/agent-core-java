/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.FrameworkError;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.tools.agent_control.SessionTools;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionTaskRow;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionToolkit;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionTool;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionsCancelTool;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionsListTool;
import com.openjiuwen.harness.tools.agent_control.SessionTools.SessionsSpawnTool;
import com.openjiuwen.harness.tools.agent_control.SessionTools.ToolOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            toolkit.upsertRunning("t1", "sub1", "desc");

            SessionTaskRow row = toolkit.get("t1");
            assertNotNull(row);
            assertEquals("t1", row.getTaskId());
            assertEquals("sub1", row.getSubSessionId());
            assertEquals("desc", row.getDescription());
            assertEquals("running", row.getStatus());
        }

        @Test
        void testMarkCompletedFailedCanceledClear() {
            toolkit.upsertRunning("t1", "sub1", "d");
            toolkit.markCompleted("t1", "ok");
            SessionTaskRow row1 = toolkit.get("t1");
            assertNotNull(row1);
            assertEquals("completed", row1.getStatus());
            assertEquals("ok", row1.getResult());

            toolkit.upsertRunning("t2", "sub2", "d2");
            toolkit.markFailed("t2", "boom");
            SessionTaskRow row2 = toolkit.get("t2");
            assertNotNull(row2);
            assertEquals("error", row2.getStatus());
            assertEquals("boom", row2.getError());

            toolkit.upsertRunning("t3", "sub3", "d3");
            toolkit.markCanceled("t3");
            SessionTaskRow row3 = toolkit.get("t3");
            assertNotNull(row3);
            assertEquals("canceled", row3.getStatus());

            toolkit.clear();
            assertEquals(List.of(), toolkit.listAll());
            assertNull(toolkit.get("t1"));
        }
    }

    @Nested
    class TestSessionsListTool {

        @Test
        void testEmptyCn() {
            SessionsListTool tool = new SessionsListTool(toolkit, "cn");

            ToolOutput out = tool.invoke(Map.of());

            assertTrue(out.isSuccess());
            assertTrue(String.valueOf(out.getData()).contains("\u6ca1\u6709\u540e\u53f0"));
        }

        @Test
        void testOneRow() {
            toolkit.upsertRunning("tid", "sid", "hello");
            SessionsListTool tool = new SessionsListTool(toolkit, "en");

            ToolOutput out = tool.invoke(Map.of());

            assertTrue(out.isSuccess());
            String data = String.valueOf(out.getData());
            assertTrue(data.contains("tid"));
            assertTrue(data.contains("hello"));
            assertTrue(data.contains("running"));
        }
    }

    @Nested
    class TestSessionsCancelTool {

        @Test
        void testInvalidInputs() {
            SessionsCancelTool tool = new SessionsCancelTool(new FakeParent(null, null, null), toolkit, "en");

            FrameworkError error = assertThrows(FrameworkError.class, () -> tool.invoke("not a dict"));

            assertTrue(error.getMessage().contains("Invalid inputs"));
        }

        @Test
        void testMissingTaskId() {
            SessionsCancelTool tool = new SessionsCancelTool(new FakeParent(null, null, null), toolkit, "en");

            FrameworkError error = assertThrows(FrameworkError.class, () -> tool.invoke(Map.of()));

            assertTrue(error.getMessage().contains("task_id"));
        }

        @Test
        void testTaskNotFound() {
            FakeScheduler scheduler = new FakeScheduler(true);
            SessionsCancelTool tool = new SessionsCancelTool(
                    new FakeParent(null, null, new FakeController(scheduler)),
                    toolkit,
                    "en"
            );

            FrameworkError error = assertThrows(FrameworkError.class, () -> tool.invoke(Map.of("task_id", "nope")));

            assertTrue(error.getMessage().contains("not found"));
        }

        @Test
        void testCancelSuccess() {
            toolkit.upsertRunning("tid", "sid", "d");
            FakeScheduler scheduler = new FakeScheduler(true);
            SessionsCancelTool tool = new SessionsCancelTool(
                    new FakeParent(null, null, new FakeController(scheduler)),
                    toolkit,
                    "cn"
            );

            ToolOutput out = tool.invoke(Map.of("task_id", "tid"));

            assertTrue(out.isSuccess());
            assertEquals("tid", scheduler.cancelledTaskId);
            assertEquals(1, scheduler.cancelCalls);
            assertEquals("canceled", toolkit.get("tid").getStatus());
            Map<?, ?> data = assertInstanceOf(Map.class, out.getData());
            assertEquals("tid", data.get("task_id"));
            assertEquals("canceled", data.get("status"));
        }

        @Test
        void testCancelSchedulerReturnsFalse() {
            toolkit.upsertRunning("tid", "sid", "d");
            FakeScheduler scheduler = new FakeScheduler(false);
            SessionsCancelTool tool = new SessionsCancelTool(
                    new FakeParent(null, null, new FakeController(scheduler)),
                    toolkit,
                    "en"
            );

            ToolOutput out = tool.invoke(Map.of("task_id", "tid"));

            assertFalse(out.isSuccess());
            assertEquals("tid", scheduler.cancelledTaskId);
            assertEquals("running", toolkit.get("tid").getStatus());
            Map<?, ?> data = assertInstanceOf(Map.class, out.getData());
            assertEquals("running", data.get("status"));
        }
    }

    @Nested
    class TestSessionsSpawnTool {

        @Test
        void testEnableTaskLoopRequired() {
            SessionsSpawnTool tool = new SessionsSpawnTool(
                    new FakeParent(null, null, null),
                    toolkit,
                    "en"
            );

            FrameworkError error = assertThrows(
                    FrameworkError.class,
                    () -> tool.invoke(Map.of(), new FakeSession("sess-1"))
            );

            assertTrue(error.getMessage().contains("enable_task_loop"));
        }

        @Test
        void testSpawnSubmitsTask() {
            FakeTaskManager taskManager = new FakeTaskManager();
            SessionsSpawnTool tool = new SessionsSpawnTool(
                    new FakeParent(new FakeDeepConfig(true), new FakeEventHandler(taskManager), null),
                    toolkit,
                    "cn"
            );
            FakeSession session = new FakeSession("sess-1");

            ToolOutput out = tool.invoke(
                    Map.of("subagent_type", "foo", "task_description", "do work"),
                    session
            );

            assertTrue(out.isSuccess());
            Map<?, ?> data = assertInstanceOf(Map.class, out.getData());
            assertEquals("pending", data.get("status"));
            assertTrue(String.valueOf(data.get("message")).contains("\u5df2\u63d0\u4ea4")
                    || String.valueOf(data.get("message")).toLowerCase().contains("pending"));

            assertEquals(1, taskManager.addCalls);
            Task taskArg = taskManager.addedTask;
            assertNotNull(taskArg);
            assertEquals(SessionTools.SESSION_SPAWN_TASK_TYPE, taskArg.getTaskType());
            assertEquals("sess-1", taskArg.getSessionId());
            assertEquals(TaskStatus.SUBMITTED, taskArg.getStatus());
            assertEquals("do work", taskArg.getDescription());
            assertEquals("foo", taskArg.getMetadata().get("subagent_type"));
            assertEquals("do work", taskArg.getMetadata().get("task_description"));
            assertTrue(String.valueOf(taskArg.getMetadata().get("sub_session_id")).startsWith("sess-1_sub_"));

            List<SessionTaskRow> rows = toolkit.listAll();
            assertEquals(1, rows.size());
            assertEquals("running", rows.get(0).getStatus());
            assertEquals("do work", rows.get(0).getDescription());
        }
    }

    @Test
    void testBuildSessionToolsReturnsThree() {
        List<Object> tools = SessionTools.createSessionTools(
                new FakeParent(new FakeDeepConfig(true), new FakeEventHandler(new FakeTaskManager()), null),
                toolkit,
                "en"
        );

        assertEquals(3, tools.size());
        Set<String> names = tools.stream()
                .map(SessionTool.class::cast)
                .map(SessionTool::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("sessions_list", "sessions_spawn", "sessions_cancel"), names);
    }

    private static final class FakeParent {
        private final Object deep_config;
        private final Object event_handler;
        private final Object loop_controller;

        private FakeParent(Object deepConfig, Object eventHandler, Object loopController) {
            this.deep_config = deepConfig;
            this.event_handler = eventHandler;
            this.loop_controller = loopController;
        }
    }

    private static final class FakeDeepConfig {
        private final boolean enable_task_loop;

        private FakeDeepConfig(boolean enableTaskLoop) {
            this.enable_task_loop = enableTaskLoop;
        }
    }

    private static final class FakeEventHandler {
        private final FakeTaskManager task_manager;

        private FakeEventHandler(FakeTaskManager taskManager) {
            this.task_manager = taskManager;
        }
    }

    private static final class FakeTaskManager {
        private int addCalls;
        private Task addedTask;

        void addTask(Task task) {
            this.addCalls++;
            this.addedTask = task;
        }
    }

    private static final class FakeController {
        private final FakeScheduler task_scheduler;

        private FakeController(FakeScheduler scheduler) {
            this.task_scheduler = scheduler;
        }
    }

    private static final class FakeScheduler {
        private final boolean cancelResult;
        private int cancelCalls;
        private String cancelledTaskId;

        private FakeScheduler(boolean cancelResult) {
            this.cancelResult = cancelResult;
        }

        boolean cancelTask(String taskId) {
            this.cancelCalls++;
            this.cancelledTaskId = taskId;
            return cancelResult;
        }
    }

    private static final class FakeSession implements Session {
        private final String sessionId;

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }
    }
}
