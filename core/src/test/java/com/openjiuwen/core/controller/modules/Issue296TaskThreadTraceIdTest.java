/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.Slf4jLogCapture;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Reproduction for GitCode issue #296: trace_id is lost on TaskScheduler's
 * {@code task-*} threads, so tool-execution logs fall back to the
 * {@code default_trace_id} placeholder instead of the session id.
 *
 * <p>Contract under test: a log emitted on the task execution thread carries
 * the same MDC {@code trace_id} as a log emitted on the session thread.</p>
 */
class Issue296TaskThreadTraceIdTest {

    private static final String SESSION_ID = "session-issue-296";
    private static final String TASK_ID = "task-issue-296";
    private static final String SESSION_THREAD_MARKER = "issue-296-probe-session-thread";
    private static final String TASK_THREAD_MARKER = "issue-296-probe-task-thread";

    /**
     * Control case: a thread created directly by the session thread inherits
     * the InheritableThreadLocal trace context. Proves the inheritance
     * mechanism itself works, isolating the defect to the scheduling path.
     */
    @Test
    void controlThreadCreatedBySessionThreadInheritsTraceId() throws Exception {
        LoggingUtils.setSessionId(SESSION_ID);
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> inherited = new AtomicReference<>();
            Thread child = OpenJiuwenExecutors.newThread(() -> {
                inherited.set(LoggingUtils.getSessionId());
                done.countDown();
            }, "control-child", true);
            child.start();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(inherited.get()).isEqualTo(SESSION_ID);
        } finally {
            LoggingUtils.setSessionId();
        }
    }

    @Test
    void schedulerTaskThreadKeepsSessionTraceIdInLogs() throws Exception {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        EventQueue eventQueue = new EventQueue(config);
        BaseCard card = new BaseCard("agent-1", "agent", "test agent");
        TaskScheduler scheduler = new TaskScheduler(
                config, taskManager, new ContextEngine(), new AbilityManager(), eventQueue, card);
        FakeSession session = new FakeSession(SESSION_ID);
        eventQueue.setEventHandler(new EventHandler() {
            @Override
            public Map<String, Object> handleInput(EventHandlerInput inputs) {
                return Map.of();
            }

            @Override
            public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
                return Map.of();
            }

            @Override
            public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
                return Map.of();
            }

            @Override
            public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
                return Map.of();
            }
        });
        eventQueue.subscribe(card.getId(), session.getSessionId());
        scheduler.getSessions().put(session.getSessionId(), session);

        AtomicReference<String> taskThreadName = new AtomicReference<>();
        AtomicReference<String> taskThreadTraceId = new AtomicReference<>();
        scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "test_task", dependencies -> new TaskExecutor(dependencies) {
                    @Override
                    public Iterator<ControllerOutputChunk> executeAbility(
                            String taskId, AgentSessionApi taskSession) {
                        taskThreadName.set(Thread.currentThread().getName());
                        taskThreadTraceId.set(LoggingUtils.getSessionId());
                        Loggers.CONTROLLER.info(TASK_THREAD_MARKER);
                        return List.of(completionChunk()).iterator();
                    }

                    @Override
                    public PauseCheckResult canPause(String taskId, AgentSessionApi taskSession) {
                        return new PauseCheckResult(true, "");
                    }

                    @Override
                    public boolean pause(String taskId, AgentSessionApi taskSession) {
                        return true;
                    }

                    @Override
                    public CancelCheckResult canCancel(String taskId, AgentSessionApi taskSession) {
                        return new CancelCheckResult(true, "");
                    }

                    @Override
                    public boolean cancel(String taskId, AgentSessionApi taskSession) {
                        return true;
                    }
                });

        try (Slf4jLogCapture capture = new Slf4jLogCapture("controller")) {
            scheduler.start();

            // Simulate the session thread binding trace_id after the scheduler
            // pool already exists, mirroring AgentSession.preRun on a request
            // thread in production.
            LoggingUtils.setSessionId(SESSION_ID);
            Loggers.CONTROLLER.info(SESSION_THREAD_MARKER);

            Task task = new Task(session.getSessionId(), TASK_ID, "test_task");
            task.setStatus(TaskStatus.SUBMITTED);
            taskManager.addTask(task);

            waitUntil(() -> taskManager.getTask(TaskFilter.byTaskId(TASK_ID)).get(0).getStatus()
                    == TaskStatus.COMPLETED);

            assertThat(taskThreadName.get()).startsWith("task-");

            String sessionThreadMdcTraceId = capturedMdcTraceId(capture, SESSION_THREAD_MARKER);
            assertThat(sessionThreadMdcTraceId)
                    .as("session thread log trace_id (pipeline control)")
                    .isEqualTo(SESSION_ID);

            assertThat(taskThreadTraceId.get())
                    .as("LoggingUtils trace context on the task execution thread")
                    .isEqualTo(SESSION_ID);

            String taskThreadMdcTraceId = capturedMdcTraceId(capture, TASK_THREAD_MARKER);
            assertThat(taskThreadMdcTraceId)
                    .as("MDC trace_id of the log emitted on the task execution thread")
                    .isEqualTo(SESSION_ID);
        } finally {
            LoggingUtils.setSessionId();
            scheduler.stop();
            eventQueue.stop();
        }
    }

    private static ControllerOutputChunk completionChunk() {
        return new ControllerOutputChunk(
                0,
                new ControllerOutputPayload(
                        EventType.TASK_COMPLETION.getValue(),
                        List.of(new DataFrame.TextDataFrame("done")),
                        null
                ),
                false
        );
    }

    private static String capturedMdcTraceId(Slf4jLogCapture capture, String marker) {
        List<String> messages = capture.formattedMessages();
        List<Map<String, String>> mdcMaps = capture.mdcMaps();
        for (int i = 0; i < messages.size(); i++) {
            if (marker.equals(messages.get(i))) {
                return mdcMaps.get(i).get("trace_id");
            }
        }
        return null;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        fail("condition was not met before timeout");
    }

    /**
     * Minimal session dependency, mirrors the fixture in {@code TaskSchedulerTest}.
     */
    private static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = Collections.synchronizedList(new ArrayList<>());

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            synchronized (stream) {
                return new ArrayList<>(stream).iterator();
            }
        }
    }
}
