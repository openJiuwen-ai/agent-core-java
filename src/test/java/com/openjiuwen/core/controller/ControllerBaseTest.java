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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controller integration tests — base scenarios.
 *
 * <p>Converted from: test_controller_base.py
 *
 * <p>Test areas:
 * <ul>
 *   <li>EventHandler task control (pause/cancel)</li>
 *   <li>State persistence</li>
 *   <li>Lifecycle management</li>
 *   <li>Session management</li>
 *   <li>Event system</li>
 * </ul>
 */
@DisplayName("Controller Base Integration Tests")
class ControllerBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ControllerBaseTest.class);

    // ==================== Task Executors ====================

    /**
     * Cancellable task executor.
     * task_1 finishes quickly (2 iterations), others run 100 iterations.
     */
    static class CancellableTaskExecutor extends TaskExecutor {
        volatile boolean cancelled = false;
        volatile boolean pauseRequested = false;

        CancellableTaskExecutor(TaskExecutorDependencies deps) {
            super(deps);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            final int iterations = taskId.contains("task_1") ? 2 : 10;
            return new Iterator<>() {
                int phase = -1;
                boolean done = false;

                @Override
                public boolean hasNext() {
                    return !done && !cancelled && !pauseRequested
                            && !Thread.currentThread().isInterrupted();
                }

                @Override
                public ControllerOutputChunk next() {
                    phase++;
                    if (phase == 0) {
                        return chunk(0, "processing",
                                "Task " + taskId + " started", false);
                    }
                    if (phase <= iterations) {
                        sleep(50);
                        if (cancelled || pauseRequested) {
                            done = true;
                            return chunk(phase, "processing",
                                    "Task " + taskId + " stopped", false);
                        }
                        return chunk(phase, "processing",
                                "Task " + taskId + " progress " + phase + "/" + iterations, false);
                    }
                    done = true;
                    return chunk(iterations + 1, EventType.TASK_COMPLETION.getValue(),
                            "Task " + taskId + " completed", true);
                }
            };
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            pauseRequested = true;
            logger.info("Task {} pause requested", taskId);
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            cancelled = true;
            logger.info("Task {} cancellation requested", taskId);
            return true;
        }
    }

    /**
     * Non-cancellable task executor.
     */
    static class NonCancellableTaskExecutor extends TaskExecutor {
        NonCancellableTaskExecutor(TaskExecutorDependencies deps) {
            super(deps);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return List.of(
                    chunk(0, "processing",
                            "Non-cancellable task " + taskId + " running", false),
                    chunk(1, EventType.TASK_COMPLETION.getValue(),
                            "Task " + taskId + " completed", true)
            ).iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(false, "This task cannot be paused");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            throw new RuntimeException("pause() should not be called when canPause() returns false");
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(false, "This task cannot be cancelled");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            throw new RuntimeException("cancel() should not be called when canCancel() returns false");
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
                    // Always return true so next() can throw
                    return true;
                }

                @Override
                public ControllerOutputChunk next() {
                    if (!yieldedStartChunk) {
                        yieldedStartChunk = true;
                        return chunk(0, "processing",
                                "Task " + taskId + " starting...", false);
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

    // ==================== Event Handlers ====================

    static class SimpleEventHandler extends EventHandler {
        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            Task task = Task.builder(inputs.getSession().getSessionId(), "test_task_1", "cancellable")
                    .priority(1).status(TaskStatus.SUBMITTED).contextId("test_context_1").build();
            getTaskManager().addTask(List.of(task));
            logger.info("SimpleEventHandler: Created task test_task_1");
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 1));
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

    static class DynamicTaskEventHandler extends EventHandler {
        private int taskCounter = 0;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            taskCounter++;
            String taskId = "test_task_" + taskCounter + "_" + uniqueId;
            Task task = Task.builder(inputs.getSession().getSessionId(), taskId, "cancellable")
                    .priority(1).status(TaskStatus.SUBMITTED).contextId("test_context_" + uniqueId).build();
            getTaskManager().addTask(List.of(task));
            logger.info("DynamicTaskEventHandler: Created task {}", taskId);
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 1));
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

    static class PauseInHandlerEventHandler extends EventHandler {
        boolean firstTaskCompleted = false;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = List.of(
                    Task.builder(sid, "pause_test_task_1", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("pause_test_context_1").build(),
                    Task.builder(sid, "pause_test_task_2", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("pause_test_context_2").build(),
                    Task.builder(sid, "pause_test_task_3", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("pause_test_context_3").build()
            );
            getTaskManager().addTask(tasks);
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 3));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            TaskCompletionEvent event = (TaskCompletionEvent) inputs.getEvent();
            String completedTaskId = event.getTask().getTaskId();
            logger.info("PauseInHandlerEventHandler: Task {} completed", completedTaskId);

            if ("pause_test_task_1".equals(completedTaskId) && !firstTaskCompleted) {
                firstTaskCompleted = true;
                boolean success = getTaskScheduler().pauseTask("pause_test_task_2");
                logger.info("PauseInHandlerEventHandler: Paused task 2: {}", success);
                return CompletableFuture.completedFuture(Map.of("status", "success", "paused", success));
            }
            if ("pause_test_task_3".equals(completedTaskId)) {
                getTaskScheduler().cancelTask("pause_test_task_2");
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class PauseNonPausableEventHandler extends EventHandler {
        boolean firstTaskCompleted = false;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = List.of(
                    Task.builder(sid, "pausable_task", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("pausable_context").build(),
                    Task.builder(sid, "non_pausable_task", "non_cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("non_pausable_context").build()
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
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                boolean success = getTaskScheduler().pauseTask("non_pausable_task");
                logger.info("Attempt to pause non-pausable task: {}", success);
                return CompletableFuture.completedFuture(Map.of("status", "success", "paused", success));
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class PauseThenCancelEventHandler extends EventHandler {
        int completedCount = 0;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                tasks.add(Task.builder(sid, "multi_op_task_" + i, "cancellable")
                        .priority(1).status(TaskStatus.SUBMITTED).contextId("multi_op_context_" + i).build());
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
            completedCount++;
            if (completedCount == 1) {
                getTaskScheduler().pauseTask("multi_op_task_2");
                logger.info("Paused task 2");
            } else if (completedCount == 2) {
                boolean success = getTaskScheduler().cancelTask("multi_op_task_2");
                logger.info("Attempted to cancel paused task 2: {}", success);
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class CancelInHandlerEventHandler extends EventHandler {
        boolean firstTaskCompleted = false;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = List.of(
                    Task.builder(sid, "cancel_test_task_1", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("cancel_test_context_1").build(),
                    Task.builder(sid, "cancel_test_task_2", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("cancel_test_context_2").build()
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
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                boolean success = getTaskScheduler().cancelTask("cancel_test_task_2");
                logger.info("CancelInHandlerEventHandler: Cancelled task 2: {}", success);
                return CompletableFuture.completedFuture(Map.of("status", "success", "cancelled", success));
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class CancelNonCancellableEventHandler extends EventHandler {
        boolean firstTaskCompleted = false;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            String sid = inputs.getSession().getSessionId();
            List<Task> tasks = List.of(
                    Task.builder(sid, "cancellable_task", "cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("cancellable_context").build(),
                    Task.builder(sid, "non_cancellable_task", "non_cancellable")
                            .priority(1).status(TaskStatus.SUBMITTED).contextId("non_cancellable_context").build()
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
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                boolean success = getTaskScheduler().cancelTask("non_cancellable_task");
                logger.info("Attempt to cancel non-cancellable task: {}", success);
                return CompletableFuture.completedFuture(Map.of("status", "success", "cancelled", success));
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    // ==================== State Persistence EventHandlers ====================

    static class StatePersistenceEventHandler extends EventHandler {
        int roundNumber = 0;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            roundNumber++;
            String sid = inputs.getSession().getSessionId();
            if (roundNumber == 1) {
                List<Task> tasks = List.of(
                        Task.builder(sid, "persist_task_1", "cancellable")
                                .priority(1).status(TaskStatus.SUBMITTED).contextId("persist_context_1").build(),
                        Task.builder(sid, "persist_task_2", "cancellable")
                                .priority(1).status(TaskStatus.SUBMITTED).contextId("persist_context_2").build()
                );
                getTaskManager().addTask(tasks);
                return CompletableFuture.completedFuture(Map.of("status", "success", "round", 1, "tasks_created", 2));
            } else {
                List<Task> existing = getTaskManager().getTask(
                        TaskFilter.builder().taskId("persist_task_2").build());
                return CompletableFuture.completedFuture(Map.of(
                        "status", "success", "round", 2, "found_persisted_task", !existing.isEmpty()));
            }
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            TaskCompletionEvent event = (TaskCompletionEvent) inputs.getEvent();
            String completedTaskId = event.getTask().getTaskId();
            if ("persist_task_1".equals(completedTaskId)) {
                boolean success = getTaskScheduler().pauseTask("persist_task_2");
                logger.info("StatePersistenceEventHandler: Paused persist_task_2, result: {}", success);
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    static class MultiTaskStatePersistenceEventHandler extends EventHandler {
        int roundNumber = 0;

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            roundNumber++;
            String sid = inputs.getSession().getSessionId();
            if (roundNumber == 1) {
                List<Task> tasks = List.of(
                        Task.builder(sid, "multi_task_1", "cancellable")
                                .priority(1).status(TaskStatus.SUBMITTED).contextId("multi_context_1").build(),
                        Task.builder(sid, "multi_task_2", "cancellable")
                                .priority(2).status(TaskStatus.SUBMITTED).contextId("multi_context_2").build(),
                        Task.builder(sid, "multi_task_3", "cancellable")
                                .priority(3).status(TaskStatus.SUBMITTED).contextId("multi_context_3").build()
                );
                getTaskManager().addTask(tasks);
                return CompletableFuture.completedFuture(Map.of("status", "success", "round", 1, "tasks_created", 3));
            } else {
                List<Task> allTasks = getTaskManager().getTask(null);
                return CompletableFuture.completedFuture(Map.of(
                        "status", "success", "round", 2, "restored_count", allTasks.size()));
            }
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            TaskCompletionEvent event = (TaskCompletionEvent) inputs.getEvent();
            String completedTaskId = event.getTask().getTaskId();
            if ("multi_task_1".equals(completedTaskId)) {
                getTaskScheduler().pauseTask("multi_task_2");
                getTaskScheduler().cancelTask("multi_task_3");
                logger.info("MultiTaskStatePersistenceEventHandler: Paused task_2, cancelled task_3");
            }
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(Map.of("status", "failed"));
        }
    }

    // ==================== Factory Functions ====================

    static CancellableTaskExecutor buildCancellableExecutor(TaskExecutorDependencies deps) {
        return new CancellableTaskExecutor(deps);
    }

    static NonCancellableTaskExecutor buildNonCancellableExecutor(TaskExecutorDependencies deps) {
        return new NonCancellableTaskExecutor(deps);
    }

    static FailingTaskExecutor buildFailingExecutor(TaskExecutorDependencies deps) {
        return new FailingTaskExecutor(deps);
    }

    // ==================== Helper Methods ====================

    static ControllerAgent buildTestAgent(String agentId, EventHandler eventHandler,
                                           Map<String, Function<TaskExecutorDependencies, TaskExecutor>> taskExecutors) {
        AgentCard agentCard = new AgentCard(agentId, "Test Agent " + agentId,
                "Test agent for controller testing", null);
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
            ControllerOutputChunk chunk = stream.next();
            if (chunk.getPayload() != null && chunk.getPayload().getData() != null) {
                for (BaseDataFrame frame : chunk.getPayload().getData()) {
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

    // ==================== Tests: EventHandler Task Control ====================

    @Nested
    @DisplayName("EventHandler Task Control")
    class TestEventHandlerTaskControl {

        @Test
        @DisplayName("Pause task in event handler without affecting other tasks")
        void testPauseTaskInEventHandler() {
            ControllerAgent agent = buildTestAgent("test_pause_in_handler",
                    new PauseInHandlerEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_pause_in_handler");
            InputEvent inputEvent = InputEvent.fromUserInput("test pause in handler");

            List<String> outputTexts = collectStreamOutput(
                    agent.getController().stream(inputEvent, session, null, null));

            // task_1 should complete
            assertTrue(outputTexts.stream().anyMatch(t -> t.contains("pause_test_task_1") && t.contains("completed")),
                    "The first task should complete");

            // task_2 should be PAUSED
            List<Task> tasks2 = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("pause_test_task_2").build());
            assertFalse(tasks2.isEmpty(), "The second task should exist");
            assertEquals(TaskStatus.PAUSED, tasks2.get(0).getStatus(),
                    "The second task should be PAUSED");

            // task_3 should be COMPLETED
            List<Task> tasks3 = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("pause_test_task_3").build());
            assertFalse(tasks3.isEmpty(), "The third task should exist");
            assertEquals(TaskStatus.COMPLETED, tasks3.get(0).getStatus(),
                    "The third task should be COMPLETED");

            logger.info("✅ testPauseTaskInEventHandler passed");
        }

        @Test
        @DisplayName("Cancel task in event handler")
        void testCancelTaskInEventHandler() {
            ControllerAgent agent = buildTestAgent("test_cancel_in_handler",
                    new CancelInHandlerEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_cancel_in_handler");
            InputEvent inputEvent = InputEvent.fromUserInput("test cancel in handler");

            List<String> outputTexts = collectStreamOutput(
                    agent.getController().stream(inputEvent, session, null, null));

            // task_1 should complete
            assertTrue(outputTexts.stream().anyMatch(t -> t.contains("cancel_test_task_1") && t.contains("completed")),
                    "The first task should complete");

            // task_2 should be CANCELED
            List<Task> tasks2 = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("cancel_test_task_2").build());
            assertFalse(tasks2.isEmpty(), "The second task should exist");
            assertEquals(TaskStatus.CANCELED, tasks2.get(0).getStatus(),
                    "The second task should be CANCELED");

            logger.info("✅ testCancelTaskInEventHandler passed");
        }

        @Test
        @DisplayName("Pause non-pausable task in event handler")
        void testPauseNonPausableTaskInEventHandler() {
            ControllerAgent agent = buildTestAgent("test_pause_non_pausable",
                    new PauseNonPausableEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor,
                            "non_cancellable", ControllerBaseTest::buildNonCancellableExecutor));

            TaskSession session = new TaskSession("test_pause_non_pausable");
            InputEvent inputEvent = InputEvent.fromUserInput("test pause non-pausable");

            collectStreamOutput(agent.getController().stream(inputEvent, session, null, null));

            // Non-pausable task should complete normally
            List<Task> tasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("non_pausable_task").build());
            assertFalse(tasks.isEmpty(), "Task should exist");
            assertEquals(TaskStatus.COMPLETED, tasks.get(0).getStatus(),
                    "The non-pausable task should complete normally");

            logger.info("✅ testPauseNonPausableTaskInEventHandler passed");
        }

        @Test
        @DisplayName("Cancel non-cancellable task in event handler")
        void testCancelNonCancellableTaskInEventHandler() {
            ControllerAgent agent = buildTestAgent("test_cancel_non_cancellable",
                    new CancelNonCancellableEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor,
                            "non_cancellable", ControllerBaseTest::buildNonCancellableExecutor));

            TaskSession session = new TaskSession("test_cancel_non_cancellable");
            InputEvent inputEvent = InputEvent.fromUserInput("test cancel non-cancellable");

            collectStreamOutput(agent.getController().stream(inputEvent, session, null, null));

            // Non-cancellable task should complete normally
            List<Task> tasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("non_cancellable_task").build());
            assertFalse(tasks.isEmpty(), "Task should exist");
            assertEquals(TaskStatus.COMPLETED, tasks.get(0).getStatus(),
                    "The non-cancellable task should complete normally");

            logger.info("✅ testCancelNonCancellableTaskInEventHandler passed");
        }

        @Test
        @DisplayName("Pause then cancel task in event handler")
        void testPauseThenCancelInEventHandler() {
            ControllerAgent agent = buildTestAgent("test_pause_then_cancel",
                    new PauseThenCancelEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_pause_then_cancel");
            InputEvent inputEvent = InputEvent.fromUserInput("test pause then cancel");

            collectStreamOutput(agent.getController().stream(inputEvent, session, null, null));

            // task_2 should be PAUSED
            List<Task> tasks2 = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("multi_op_task_2").build());
            assertFalse(tasks2.isEmpty(), "The second task should exist");
            assertEquals(TaskStatus.PAUSED, tasks2.get(0).getStatus(),
                    "The second task should be PAUSED");

            logger.info("✅ testPauseThenCancelInEventHandler passed");
        }
    }

    // ==================== Tests: State Persistence ====================

    @Nested
    @DisplayName("State Persistence")
    class TestStatePersistence {

        @Test
        @DisplayName("Paused task state persistence across stream rounds")
        void testPausedTaskStatePersistence() {
            ControllerAgent agent = buildTestAgent("test_state_persistence",
                    new StatePersistenceEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_state_persistence");

            // Round 1
            InputEvent inputEvent1 = InputEvent.fromUserInput("round 1");
            List<String> output1 = collectStreamOutput(
                    agent.getController().stream(inputEvent1, session, null, null));

            // Verify persist_task_1 completed
            assertTrue(output1.stream().anyMatch(t -> t.contains("persist_task_1") && t.contains("completed")),
                    "The first task should complete");

            // Verify persist_task_2 is PAUSED
            List<Task> tasks = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("persist_task_2").build());
            assertFalse(tasks.isEmpty());
            assertEquals(TaskStatus.PAUSED, tasks.get(0).getStatus());

            // Round 2
            InputEvent inputEvent2 = InputEvent.fromUserInput("round 2");
            collectStreamOutput(agent.getController().stream(inputEvent2, session, null, null));

            // Verify persist_task_2 is still PAUSED (state persisted)
            List<Task> tasks2 = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("persist_task_2").build());
            assertFalse(tasks2.isEmpty(), "persist_task_2 should be readable in round 2");
            assertEquals(TaskStatus.PAUSED, tasks2.get(0).getStatus());
            assertEquals("persist_task_2", tasks2.get(0).getTaskId());
            assertEquals("persist_context_2", tasks2.get(0).getContextId());
            assertEquals(1, tasks2.get(0).getPriority());

            logger.info("✅ testPausedTaskStatePersistence passed");
        }

        @Test
        @DisplayName("Multi-task state persistence with mixed statuses")
        void testMultiTaskStatePersistence() {
            ControllerAgent agent = buildTestAgent("test_multi_state_persistence",
                    new MultiTaskStatePersistenceEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_multi_state");

            // Round 1
            InputEvent inputEvent1 = InputEvent.fromUserInput("round 1");
            collectStreamOutput(agent.getController().stream(inputEvent1, session, null, null));

            // Verify round 1 states
            List<Task> allTasks1 = agent.getController().getTaskManager().getTask(null);
            Map<String, TaskStatus> statusMap1 = new HashMap<>();
            allTasks1.forEach(t -> statusMap1.put(t.getTaskId(), t.getStatus()));

            assertEquals(TaskStatus.COMPLETED, statusMap1.get("multi_task_1"));
            assertEquals(TaskStatus.PAUSED, statusMap1.get("multi_task_2"));
            assertEquals(TaskStatus.CANCELED, statusMap1.get("multi_task_3"));

            // Round 2
            InputEvent inputEvent2 = InputEvent.fromUserInput("round 2");
            collectStreamOutput(agent.getController().stream(inputEvent2, session, null, null));

            // Verify states persisted
            List<Task> allTasks2 = agent.getController().getTaskManager().getTask(null);
            assertEquals(3, allTasks2.size(), "Round 2 should restore 3 tasks");
            Map<String, TaskStatus> statusMap2 = new HashMap<>();
            allTasks2.forEach(t -> statusMap2.put(t.getTaskId(), t.getStatus()));
            assertEquals(statusMap1, statusMap2, "States in round 2 should match round 1");

            // Verify metadata
            for (Task task : allTasks2) {
                switch (task.getTaskId()) {
                    case "multi_task_1" -> {
                        assertEquals(1, task.getPriority());
                        assertEquals("multi_context_1", task.getContextId());
                    }
                    case "multi_task_2" -> {
                        assertEquals(2, task.getPriority());
                        assertEquals("multi_context_2", task.getContextId());
                    }
                    case "multi_task_3" -> {
                        assertEquals(3, task.getPriority());
                        assertEquals("multi_context_3", task.getContextId());
                    }
                }
            }

            logger.info("✅ testMultiTaskStatePersistence passed");
        }

        @Test
        @DisplayName("State restoration failure fallback - graceful degradation")
        void testStateRestorationFailureFallback() {
            ControllerAgent agent = buildTestAgent("test_fallback",
                    new StatePersistenceEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_fallback");

            // Round 1
            InputEvent inputEvent1 = InputEvent.fromUserInput("round 1");
            collectStreamOutput(agent.getController().stream(inputEvent1, session, null, null));

            // Verify task is paused
            List<Task> paused = agent.getController().getTaskManager().getTask(
                    TaskFilter.builder().taskId("persist_task_2").build());
            assertEquals(TaskStatus.PAUSED, paused.get(0).getStatus());

            // Corrupt session state
            session.updateState(Map.of("controller", Map.of("task_manager_state", "invalid_data")));

            // Round 2 — should not throw
            InputEvent inputEvent2 = InputEvent.fromUserInput("round 2");
            assertDoesNotThrow(() ->
                            collectStreamOutput(agent.getController().stream(inputEvent2, session, null, null)),
                    "No exception should be raised when state restoration fails");

            logger.info("✅ testStateRestorationFailureFallback passed");
        }
    }

    // ==================== Tests: Lifecycle Management ====================

    @Nested
    @DisplayName("Lifecycle Management")
    class TestLifecycleManagement {

        @Test
        @DisplayName("Controller stop cleans up all resources")
        void testControllerStopCleanupAll() {
            ControllerAgent agent = buildTestAgent("test_stop_cleanup",
                    new DynamicTaskEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_stop");
            InputEvent inputEvent = InputEvent.fromUserInput("test stop");

            // Execute stream
            collectStreamOutput(agent.getController().stream(inputEvent, session, null, null));

            // Stop controller
            agent.getController().stop();

            // Verify sessions are cleared
            assertEquals(0, agent.getController().getTaskScheduler().getSessions().size(),
                    "Sessions should be cleared after stop");

            logger.info("✅ testControllerStopCleanupAll passed");
        }
    }

    // ==================== Tests: Session Management ====================

    @Nested
    @DisplayName("Session Management")
    class TestSessionManagement {

        @Test
        @DisplayName("Multi-turn conversation no interference")
        void testMultiTurnConversationNoInterference() {
            ControllerAgent agent = buildTestAgent("test_multi_turn",
                    new DynamicTaskEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("multi_turn");

            // Turn 1
            InputEvent inputEvent1 = InputEvent.fromUserInput("turn 1");
            List<String> output1 = collectStreamOutput(
                    agent.getController().stream(inputEvent1, session, null, null));
            assertTrue(output1.stream().anyMatch(t -> t.contains("test_task_1")),
                    "The first turn should create a task");

            List<Task> tasksAfterTurn1 = agent.getController().getTaskManager().getTask(null);
            logger.info("Tasks after turn 1: {}", tasksAfterTurn1.size());

            // Turn 2
            InputEvent inputEvent2 = InputEvent.fromUserInput("turn 2");
            List<String> output2 = collectStreamOutput(
                    agent.getController().stream(inputEvent2, session, null, null));
            assertTrue(output2.stream().anyMatch(t -> t.contains("test_task_2")),
                    "The second turn should create a new task");

            logger.info("✅ testMultiTurnConversationNoInterference passed");
        }

        @Test
        @DisplayName("Session registration and cleanup")
        void testSessionRegistrationAndCleanup() {
            ControllerAgent agent = buildTestAgent("test_session_reg",
                    new SimpleEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_reg");

            // Verify initial state: no sessions
            assertEquals(0, agent.getController().getTaskScheduler().getSessions().size(),
                    "Sessions should be empty initially");

            InputEvent inputEvent = InputEvent.fromUserInput("test");

            // Execute stream (blocking)
            collectStreamOutput(agent.getController().stream(inputEvent, session, null, null));

            // After stream, sessions should be cleaned up
            assertFalse(agent.getController().getTaskScheduler().getSessions().containsKey("test_reg"),
                    "Session should be removed after stream ends");

            logger.info("✅ testSessionRegistrationAndCleanup passed");
        }
    }

    // ==================== Tests: Event System ====================

    @Nested
    @DisplayName("Event System")
    class TestEventSystem {

        @Test
        @DisplayName("Event subscribe and publish")
        void testEventSubscribeAndPublish() {
            ControllerAgent agent = buildTestAgent("test_event_pub_sub",
                    new SimpleEventHandler(),
                    Map.of("cancellable", ControllerBaseTest::buildCancellableExecutor));

            TaskSession session = new TaskSession("test_pub_sub");
            InputEvent inputEvent = InputEvent.fromUserInput("test event system");

            List<String> output = collectStreamOutput(
                    agent.getController().stream(inputEvent, session, null, null));

            // Verify events were handled correctly (via output)
            assertFalse(output.isEmpty(), "There should be output to prove events were handled correctly");
            assertTrue(output.stream().anyMatch(t -> t.contains("started")),
                    "There should be task start information");

            logger.info("✅ testEventSubscribeAndPublish passed");
        }
    }
}

