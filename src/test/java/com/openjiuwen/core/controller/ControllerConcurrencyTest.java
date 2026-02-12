// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller;

import com.openjiuwen.core.controller.modules.*;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.TaskSession;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controller integration tests — concurrency and exception handling.
 *
 * <p>Converted from: test_controller_concurrency_and_exception.py
 *
 * <p>Test areas:
 * <ul>
 *   <li>Concurrent session isolation</li>
 *   <li>Task execution exception handling</li>
 *   <li>Stream output exception isolation</li>
 *   <li>EventHandler exception isolation</li>
 *   <li>Controller config (timeout)</li>
 * </ul>
 */
@DisplayName("Controller Concurrency & Exception Tests")
class ControllerConcurrencyTest {

    private static final Logger logger = LoggerFactory.getLogger(ControllerConcurrencyTest.class);

    // ==================== Task Executors ====================

    /**
     * Normal task executor — runs 3 iterations with 50ms sleep each.
     */
    static class NormalTaskExecutor extends TaskExecutor {
        NormalTaskExecutor(TaskExecutorDependencies deps) {
            super(deps);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            List<ControllerOutputChunk> chunks = new ArrayList<>();
            chunks.add(chunk(0, "processing",
                    "Task " + taskId + " started in session " + session.getSessionId(), false));
            for (int i = 1; i <= 3; i++) {
                sleep(50);
                chunks.add(chunk(i, "processing",
                        "Task " + taskId + " progress " + i + "/3", false));
            }
            chunks.add(chunk(4, EventType.TASK_COMPLETION.getValue(),
                    "Task " + taskId + " completed in session " + session.getSessionId(), true));
            return chunks.iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return true;
        }
    }

    /**
     * Failing task executor — yields one chunk then throws on next iteration.
     */
    static class FailingTaskExecutor extends TaskExecutor {
        FailingTaskExecutor(TaskExecutorDependencies deps) {
            super(deps);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return new Iterator<>() {
                boolean yieldedStartChunk = false;

                @Override
                public boolean hasNext() {
                    // Always return true so next() gets a chance to throw
                    return true;
                }

                @Override
                public ControllerOutputChunk next() {
                    if (!yieldedStartChunk) {
                        yieldedStartChunk = true;
                        sleep(50);
                        return chunk(0, "processing", "Task " + taskId + " starting...", false);
                    }
                    // Second call — simulate task failure
                    throw new RuntimeException("Task " + taskId + " failed intentionally");
                }
            };
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return true;
        }
    }

    /**
     * Stream-exception task executor — yields 2 chunks then throws mid-stream.
     */
    static class ExceptionInStreamTaskExecutor extends TaskExecutor {
        ExceptionInStreamTaskExecutor(TaskExecutorDependencies deps) {
            super(deps);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return new Iterator<>() {
                int phase = -1;

                @Override
                public boolean hasNext() {
                    // Always return true so next() can throw on phase 2
                    return true;
                }

                @Override
                public ControllerOutputChunk next() {
                    phase++;
                    if (phase == 0) {
                        return chunk(0, "processing", "Task " + taskId + " started", false);
                    }
                    if (phase == 1) {
                        sleep(25);
                        return chunk(1, "processing", "Task " + taskId + " progress 1/3", false);
                    }
                    // phase >= 2 → throw to simulate stream failure
                    throw new RuntimeException("Task " + taskId + " stream failed");
                }
            };
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return true;
        }
    }

    /**
     * Slow task executor — sleeps for a configurable time.
     * Task "timeout_task_1" sleeps quickly (0.5s), others sleep for {@code sleepTimeMs}.
     */
    static class SlowTaskExecutor extends TaskExecutor {
        private final long sleepTimeMs;
        private volatile boolean cancelled = false;

        SlowTaskExecutor(TaskExecutorDependencies deps, long sleepTimeMs) {
            super(deps);
            this.sleepTimeMs = sleepTimeMs;
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return new Iterator<>() {
                boolean started = false;
                boolean done = false;

                @Override
                public boolean hasNext() {
                    return !done && !cancelled && !Thread.currentThread().isInterrupted();
                }

                @Override
                public ControllerOutputChunk next() {
                    if (!started) {
                        started = true;
                        return chunk(0, "processing",
                                "Slow task " + taskId + " started, will sleep for " + sleepTimeMs + "ms", false);
                    }
                    // Sleep (will be interrupted by timeout or cancellation)
                    long actualSleep = "timeout_task_1".equals(taskId) ? 500 : sleepTimeMs;
                    sleep(actualSleep);
                    if (cancelled || Thread.currentThread().isInterrupted()) {
                        done = true;
                        return chunk(1, "processing", "Slow task " + taskId + " interrupted", false);
                    }
                    done = true;
                    return chunk(1, EventType.TASK_COMPLETION.getValue(),
                            "Slow task " + taskId + " completed", true);
                }
            };
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            cancelled = true;
            return true;
        }
    }

    // ==================== Event Handlers ====================

    static class ConcurrentSessionEventHandler extends EventHandler {
        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            Task task = Task.builder(sid, "task_" + sid, "normal")
                    .priority(1).status(TaskStatus.SUBMITTED)
                    .contextId("context_" + sid).build();
            getTaskManager().addTask(List.of(task));
            logger.info("ConcurrentSessionEventHandler: Created task for session {}", sid);
            return CompletableFuture.completedFuture(Map.of("status", "success", "session_id", sid));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class ExceptionInEventHandlerEventHandler extends EventHandler {
        int handleCount = 0;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                tasks.add(Task.builder(sid, "task_" + i, "normal")
                        .priority(i).status(TaskStatus.SUBMITTED)
                        .contextId("context_" + i).build());
            }
            getTaskManager().addTask(tasks);
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 3));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            handleCount++;
            if (handleCount == 1) {
                logger.info("ExceptionInEventHandlerEventHandler: Throwing exception in handleTaskCompletion");
                throw new RuntimeException("Exception in handleTaskCompletion");
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class FailingTaskEventHandler extends EventHandler {
        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = List.of(
                    Task.builder(sid, "failing_task", "failing")
                            .priority(1).status(TaskStatus.SUBMITTED)
                            .contextId("failing_context").build(),
                    Task.builder(sid, "normal_task", "normal")
                            .priority(2).status(TaskStatus.SUBMITTED)
                            .contextId("normal_context").build()
            );
            getTaskManager().addTask(tasks);
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 2));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class StreamExceptionTaskEventHandler extends EventHandler {
        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = List.of(
                    Task.builder(sid, "stream_fail_task", "stream_exception")
                            .priority(1).status(TaskStatus.SUBMITTED)
                            .contextId("stream_fail_context").build(),
                    Task.builder(sid, "normal_task_2", "normal")
                            .priority(2).status(TaskStatus.SUBMITTED)
                            .contextId("normal_context_2").build()
            );
            getTaskManager().addTask(tasks);
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 2));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class ConcurrentTasksEventHandler extends EventHandler {
        boolean created = false;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            if (created) {
                return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 0));
            }
            created = true;
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tasks.add(Task.builder(sid, "concurrent_task_" + i, "normal")
                        .priority(1).status(TaskStatus.SUBMITTED)
                        .contextId("concurrent_context_" + i).build());
            }
            getTaskManager().addTask(tasks);
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", tasks.size()));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class TimeoutTestEventHandler extends EventHandler {
        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tasks.add(Task.builder(sid, "timeout_task_" + i, "slow")
                        .priority(1).status(TaskStatus.SUBMITTED)
                        .contextId("timeout_context_" + i).build());
            }
            getTaskManager().addTask(tasks);
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }
    }

    // ==================== Factory Functions ====================

    static NormalTaskExecutor buildNormalExecutor(TaskExecutorDependencies deps) {
        return new NormalTaskExecutor(deps);
    }

    static FailingTaskExecutor buildFailingExecutor(TaskExecutorDependencies deps) {
        return new FailingTaskExecutor(deps);
    }

    static ExceptionInStreamTaskExecutor buildStreamExceptionExecutor(TaskExecutorDependencies deps) {
        return new ExceptionInStreamTaskExecutor(deps);
    }

    static Function<TaskExecutorDependencies, TaskExecutor> buildSlowExecutorFactory(long sleepTimeMs) {
        return deps -> new SlowTaskExecutor(deps, sleepTimeMs);
    }

    // ==================== Helper Methods ====================

    static ControllerAgent buildTestAgent(String agentId, EventHandler eventHandler,
                                           Map<String, Function<TaskExecutorDependencies, TaskExecutor>> taskExecutors) {
        AgentCard agentCard = new AgentCard(agentId, "Test Agent " + agentId,
                "Test agent for concurrency testing", null);
        Controller controller = new Controller();
        ControllerAgent agent = new ControllerAgent(agentCard, controller);

        controller.setEventHandler(eventHandler);
        for (var entry : taskExecutors.entrySet()) {
            controller.addTaskExecutor(entry.getKey(), entry.getValue());
        }

        ControllerConfig config = ControllerConfig.builder()
                .enableTaskPersistence(true)
                .scheduleInterval(0.1)
                .build();
        agent.configure(config);
        return agent;
    }

    static List<String> collectStreamOutput(Iterator<ControllerOutputChunk> stream) {
        List<String> texts = new ArrayList<>();
        while (stream.hasNext()) {
            ControllerOutputChunk c = stream.next();
            if (c.getPayload() != null && c.getPayload().getData() != null) {
                for (BaseDataFrame frame : c.getPayload().getData()) {
                    if (frame instanceof TextDataFrame tdf) {
                        texts.add(tdf.getText());
                    }
                }
            }
        }
        return texts;
    }

    static ControllerOutputChunk chunk(int index, String type, String text, boolean last) {
        return new ControllerOutputChunk(index, "controller_output",
                new ControllerOutputPayload(type, List.of(new TextDataFrame(text)), null), last);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Tests: Concurrent Session Isolation ====================

    @Nested
    @DisplayName("Concurrent Session Isolation")
    class TestConcurrentSessionIsolation {

        @Test
        @DisplayName("Concurrent sessions isolation — 3 sessions run without interference")
        @Timeout(30)
        void testConcurrentSessionsIsolation() throws Exception {
            ControllerAgent agent = buildTestAgent("test_concurrent_sessions",
                    new ConcurrentSessionEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor));

            ExecutorService exec = Executors.newFixedThreadPool(3);
            try {
                List<Future<List<String>>> futures = new ArrayList<>();
                for (int i = 1; i <= 3; i++) {
                    final int idx = i;
                    futures.add(exec.submit(() -> {
                        TaskSession session = new TaskSession("session_" + idx);
                        InputEvent event = InputEvent.fromUserInput("request from session " + idx);
                        return collectStreamOutput(agent.getController().stream(event, session, null, null));
                    }));
                }

                for (int i = 0; i < 3; i++) {
                    List<String> output = futures.get(i).get(20, TimeUnit.SECONDS);
                    String sessionId = "session_" + (i + 1);

                    assertFalse(output.isEmpty(), "Session " + (i + 1) + " should have output");
                    assertTrue(output.stream().anyMatch(t -> t.contains(sessionId)),
                            "Output of session " + (i + 1) + " should contain " + sessionId);
                    assertTrue(output.stream().anyMatch(t -> t.contains("completed")),
                            "Task of session " + (i + 1) + " should be completed");
                    logger.info("Session {} passed verification: {} outputs", i + 1, output.size());
                }

                // Verify all tasks completed
                List<Task> allTasks = agent.getController().getTaskManager().getTask(null);
                long completedCount = allTasks.stream()
                        .filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
                assertTrue(completedCount >= 3,
                        "There should be at least 3 completed tasks, actually " + completedCount);
            } finally {
                exec.shutdownNow();
                agent.getController().stop();
            }

            logger.info("✅ testConcurrentSessionsIsolation passed");
        }

        @Test
        @DisplayName("Session task isolation — failing session doesn't affect normal session")
        @Timeout(30)
        void testSessionTaskIsolation() throws Exception {
            ControllerAgent agent1 = buildTestAgent("test_session_1",
                    new ConcurrentSessionEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor));

            ControllerAgent agent2 = buildTestAgent("test_session_2",
                    new FailingTaskEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor,
                            "failing", ControllerConcurrencyTest::buildFailingExecutor));

            ExecutorService exec = Executors.newFixedThreadPool(2);
            try {
                Future<List<String>> f1 = exec.submit(() -> {
                    TaskSession session = new TaskSession("normal_session");
                    return collectStreamOutput(
                            agent1.getController().stream(InputEvent.fromUserInput("normal request"), session, null, null));
                });
                Future<List<String>> f2 = exec.submit(() -> {
                    TaskSession session = new TaskSession("failing_session");
                    return collectStreamOutput(
                            agent2.getController().stream(InputEvent.fromUserInput("failing request"), session, null, null));
                });

                List<String> output1 = f1.get(20, TimeUnit.SECONDS);
                List<String> output2 = f2.get(20, TimeUnit.SECONDS);

                // Session 1 should complete normally
                assertTrue(output1.stream().anyMatch(t -> t.contains("completed")),
                        "Task in Session 1 should complete normally");

                // Session 2 should also finish (task failure != session failure)
                assertFalse(output2.isEmpty(), "Session 2 should have output");

                // Verify failed tasks of agent2
                List<Task> failedTasks2 = agent2.getController().getTaskManager().getTask(null).stream()
                        .filter(t -> t.getStatus() == TaskStatus.FAILED).toList();
                assertFalse(failedTasks2.isEmpty(), "Session 2 should have failed tasks");

                // Verify agent1 has no failed tasks
                List<Task> failedTasks1 = agent1.getController().getTaskManager().getTask(null).stream()
                        .filter(t -> t.getStatus() == TaskStatus.FAILED).toList();
                assertTrue(failedTasks1.isEmpty(), "Session 1 should have no failed tasks");
            } finally {
                exec.shutdownNow();
                agent1.getController().stop();
                agent2.getController().stop();
            }

            logger.info("✅ testSessionTaskIsolation passed");
        }

        @Test
        @DisplayName("Event routing to correct session — 5 sessions")
        @Timeout(30)
        void testEventRoutingToCorrectSession() throws Exception {
            ControllerAgent agent = buildTestAgent("test_event_routing",
                    new ConcurrentSessionEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor));

            ExecutorService exec = Executors.newFixedThreadPool(5);
            try {
                List<Future<List<String>>> futures = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    final int idx = i;
                    futures.add(exec.submit(() -> {
                        TaskSession session = new TaskSession("session_" + idx);
                        return collectStreamOutput(
                                agent.getController().stream(
                                        InputEvent.fromUserInput("request " + idx), session, null, null));
                    }));
                }

                for (int i = 0; i < 5; i++) {
                    List<String> output = futures.get(i).get(20, TimeUnit.SECONDS);
                    String sessionId = "session_" + i;

                    assertFalse(output.isEmpty(), "Session " + i + " should have output");
                    assertTrue(output.stream().anyMatch(t -> t.contains(sessionId)),
                            "Output of session " + i + " should contain " + sessionId);
                }
            } finally {
                exec.shutdownNow();
                agent.getController().stop();
            }

            logger.info("✅ testEventRoutingToCorrectSession passed");
        }
    }

    // ==================== Tests: Exception Handling ====================

    @Nested
    @DisplayName("Exception Handling")
    class TestExceptionHandling {

        @Test
        @DisplayName("Task execution exception — FAILED status and error message")
        @Timeout(20)
        void testTaskExecutionExceptionHandling() {
            ControllerAgent agent = buildTestAgent("test_task_exception",
                    new FailingTaskEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor,
                            "failing", ControllerConcurrencyTest::buildFailingExecutor));

            TaskSession session = new TaskSession("test_exception");
            collectStreamOutput(
                    agent.getController().stream(InputEvent.fromUserInput("test task exception"), session, null, null));

            // Verify failing_task is FAILED
            List<Task> failedTasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("failing_task").build());
            assertFalse(failedTasks.isEmpty(), "Failed task should exist");
            assertEquals(TaskStatus.FAILED, failedTasks.get(0).getStatus(),
                    "Status of failed task should be FAILED");
            assertNotNull(failedTasks.get(0).getErrorMessage(), "Error message should be recorded");
            assertTrue(failedTasks.get(0).getErrorMessage().contains("failed intentionally"),
                    "Error message should contain failure reason: " + failedTasks.get(0).getErrorMessage());

            // Verify normal_task is COMPLETED
            List<Task> normalTasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("normal_task").build());
            assertFalse(normalTasks.isEmpty(), "Normal task should exist");
            assertEquals(TaskStatus.COMPLETED, normalTasks.get(0).getStatus(),
                    "Normal task should be completed");

            agent.getController().stop();
            logger.info("✅ testTaskExecutionExceptionHandling passed");
        }

        @Test
        @DisplayName("Stream output exception isolation — failing task doesn't affect normal task")
        @Timeout(20)
        void testStreamOutputExceptionIsolation() {
            ControllerAgent agent = buildTestAgent("test_stream_exception",
                    new StreamExceptionTaskEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor,
                            "stream_exception", ControllerConcurrencyTest::buildStreamExceptionExecutor));

            TaskSession session = new TaskSession("test_stream_exception");
            collectStreamOutput(
                    agent.getController().stream(InputEvent.fromUserInput("test stream exception"), session, null, null));

            // Verify stream_fail_task is FAILED
            List<Task> streamFailTasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("stream_fail_task").build());
            assertFalse(streamFailTasks.isEmpty(), "Stream-fail task should exist");
            assertEquals(TaskStatus.FAILED, streamFailTasks.get(0).getStatus(),
                    "Status of stream-fail task should be FAILED");

            // Verify normal_task_2 is COMPLETED
            List<Task> normalTasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("normal_task_2").build());
            assertFalse(normalTasks.isEmpty(), "Normal task should exist");
            assertEquals(TaskStatus.COMPLETED, normalTasks.get(0).getStatus(),
                    "Normal task should be completed (not affected by stream exception)");

            agent.getController().stop();
            logger.info("✅ testStreamOutputExceptionIsolation passed");
        }

        @Test
        @DisplayName("EventHandler exception isolation — exception in handler doesn't affect other events")
        @Timeout(20)
        void testEventHandlerExceptionIsolation() {
            ControllerAgent agent = buildTestAgent("test_handler_exception",
                    new ExceptionInEventHandlerEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor));

            TaskSession session = new TaskSession("test_handler_exception");
            List<String> output = collectStreamOutput(
                    agent.getController().stream(
                            InputEvent.fromUserInput("test handler exception"), session, null, null));

            assertFalse(output.isEmpty(), "There should be output");

            // Verify at least 2 tasks are completed (first one's handler threw, others should be normal)
            List<Task> allTasks = agent.getController().getTaskManager().getTask(null);
            long completedCount = allTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
            assertTrue(completedCount >= 2,
                    "There should be at least 2 completed tasks, actually " + completedCount);

            agent.getController().stop();
            logger.info("✅ testEventHandlerExceptionIsolation passed");
        }

        @Test
        @DisplayName("Exception in concurrent sessions — failure doesn't propagate")
        @Timeout(30)
        void testExceptionInConcurrentSessions() throws Exception {
            ControllerAgent normalAgent = buildTestAgent("normal_agent",
                    new ConcurrentSessionEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor));

            ControllerAgent failingAgent = buildTestAgent("failing_agent",
                    new FailingTaskEventHandler(),
                    Map.of("normal", ControllerConcurrencyTest::buildNormalExecutor,
                            "failing", ControllerConcurrencyTest::buildFailingExecutor));

            ExecutorService exec = Executors.newFixedThreadPool(3);
            try {
                Future<List<String>> f1 = exec.submit(() -> {
                    TaskSession s = new TaskSession("normal_1");
                    return collectStreamOutput(
                            normalAgent.getController().stream(InputEvent.fromUserInput("test"), s, null, null));
                });
                Future<List<String>> f2 = exec.submit(() -> {
                    TaskSession s = new TaskSession("failing");
                    return collectStreamOutput(
                            failingAgent.getController().stream(InputEvent.fromUserInput("test"), s, null, null));
                });
                Future<List<String>> f3 = exec.submit(() -> {
                    TaskSession s = new TaskSession("normal_2");
                    return collectStreamOutput(
                            normalAgent.getController().stream(InputEvent.fromUserInput("test"), s, null, null));
                });

                // Verify all finish without exceptions propagating to the caller
                List<String> o1 = f1.get(20, TimeUnit.SECONDS);
                List<String> o2 = f2.get(20, TimeUnit.SECONDS);
                List<String> o3 = f3.get(20, TimeUnit.SECONDS);

                assertFalse(o1.isEmpty(), "Session normal_1 should have output");
                assertFalse(o2.isEmpty(), "Session failing should have output");
                assertFalse(o3.isEmpty(), "Session normal_2 should have output");
            } finally {
                exec.shutdownNow();
                normalAgent.getController().stop();
                failingAgent.getController().stop();
            }

            logger.info("✅ testExceptionInConcurrentSessions passed");
        }
    }

    // ==================== Tests: ControllerConfig ====================

    @Nested
    @DisplayName("Controller Config")
    class TestControllerConfig {

        @Test
        @DisplayName("Task timeout — slow tasks fail, normal task completes")
        @Timeout(30)
        void testTaskTimeout() throws Exception {
            ControllerAgent agent = buildTestAgent("test_timeout",
                    new TimeoutTestEventHandler(),
                    Map.of("slow", buildSlowExecutorFactory(10000)));

            // Configure timeout
            ControllerConfig config = ControllerConfig.builder()
                    .enableTaskPersistence(true)
                    .scheduleInterval(0.1)
                    .taskTimeout(2.0)    // 2 seconds timeout
                    .build();
            agent.configure(config);

            TaskSession session = new TaskSession("test_timeout_session");
            InputEvent inputEvent = InputEvent.fromUserInput("test timeout");

            // Run stream in background thread since it blocks
            ExecutorService exec = Executors.newSingleThreadExecutor();
            try {
                Future<List<String>> streamFuture = exec.submit(() ->
                        collectStreamOutput(agent.getController().stream(inputEvent, session, null, null)));

                // Wait for stream to complete (up to 15s)
                List<String> output = streamFuture.get(15, TimeUnit.SECONDS);

                // Check task statuses
                List<Task> tasks = agent.getController().getTaskManager().getTask(
                        TaskFilter.builder().sessionId("test_timeout_session").build());
                assertFalse(tasks.isEmpty(), "Should have tasks");

                long completedCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
                long failedCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count();

                assertTrue(completedCount >= 1, "timeout_task_1 (quick) should be completed");
                assertTrue(failedCount >= 1, "slow tasks should be failed due to timeout");

                logger.info("✅ testTaskTimeout passed: completed={}, failed={}", completedCount, failedCount);
            } finally {
                exec.shutdownNow();
                agent.getController().stop();
            }
        }

        @Test
        @DisplayName("Timeout vs manual cancel — manual cancel results in CANCELED")
        @Timeout(30)
        void testTimeoutVsManualCancel() throws Exception {
            ControllerAgent agent = buildTestAgent("test_timeout_vs_cancel",
                    new TimeoutTestEventHandler(),
                    Map.of("slow", buildSlowExecutorFactory(10000)));

            // Configure long timeout (10s)
            ControllerConfig config = ControllerConfig.builder()
                    .enableTaskPersistence(true)
                    .scheduleInterval(0.1)
                    .taskTimeout(10.0)
                    .build();
            agent.configure(config);

            TaskSession session = new TaskSession("test_cancel_session");

            ExecutorService exec = Executors.newSingleThreadExecutor();
            try {
                // Start stream in background
                Future<List<String>> streamFuture = exec.submit(() ->
                        collectStreamOutput(agent.getController().stream(
                                InputEvent.fromUserInput("test manual cancel"), session, null, null)));

                // Wait for tasks to start
                sleep(500);

                // Manually cancel the first task
                List<Task> tasks = agent.getController().getTaskManager().getTask(
                        TaskFilter.builder().sessionId("test_cancel_session").build());
                assertFalse(tasks.isEmpty(), "Should have at least one task");

                String taskId = tasks.get(0).getTaskId();
                logger.info("Manually cancelling task {}", taskId);
                boolean success = agent.getController().getTaskScheduler().cancelTask(taskId);
                logger.info("Cancel result: {}", success);

                // Wait for stream to finish
                streamFuture.get(15, TimeUnit.SECONDS);

                // Verify task is CANCELED
                List<Task> cancelledTasks = agent.getController().getTaskManager().getTask(
                        TaskFilter.builder().taskId(taskId).build());
                assertFalse(cancelledTasks.isEmpty(), "Task should still exist");
                assertEquals(TaskStatus.CANCELED, cancelledTasks.get(0).getStatus(),
                        "Task should be CANCELED, got " + cancelledTasks.get(0).getStatus());

                logger.info("✅ testTimeoutVsManualCancel passed");
            } finally {
                exec.shutdownNow();
                agent.getController().stop();
            }
        }
    }
}

