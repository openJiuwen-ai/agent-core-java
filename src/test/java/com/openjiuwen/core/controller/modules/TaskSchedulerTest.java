// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskScheduler.
 *
 * <p>Covers initialization, start/stop lifecycle, schedule loop, execute_task,
 * execute_task_wrapper (timeout, cancellation, exception), pause/cancel task,
 * publish_task_event, are_all_tasks_completed, ensure_session_completion_signal.
 *
 * <p>Python reference: {@code tests/unit_tests/core/controller/modules/test_task_scheduler.py}
 */
class TaskSchedulerTest {

    // ==================== Stub Executors ====================

    /**
     * Concrete stub implementing all abstract methods.
     */
    static class StubTaskExecutor extends TaskExecutor {
        private final List<ControllerOutputChunk> chunks;

        StubTaskExecutor(TaskExecutorDependencies dependencies) {
            this(dependencies, List.of());
        }

        StubTaskExecutor(TaskExecutorDependencies dependencies, List<ControllerOutputChunk> chunks) {
            super(dependencies);
            this.chunks = chunks;
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
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
     * Executor that cannot be paused or cancelled.
     */
    static class NonPausableExecutor extends TaskExecutor {
        NonPausableExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            // Simulate a long-running task
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of(new ControllerOutputChunk(0)).iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(false, "Not pausable");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return false;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(false, "Not cancellable");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return false;
        }
    }

    // ==================== Helpers ====================

    private ControllerConfig config;
    private TaskManager taskManager;
    private EventQueue eventQueue;
    private TaskScheduler scheduler;
    private AgentCard card;

    private Session makeSession(String sessionId) {
        Session session = mock(Session.class);
        when(session.getSessionId()).thenReturn(sessionId);
        when(session.writeStream(any())).thenReturn(CompletableFuture.completedFuture(null));
        return session;
    }

    private AgentCard makeCard(String agentId) {
        AgentCard agentCard = new AgentCard();
        agentCard.setId(agentId);
        return agentCard;
    }

    private TaskExecutorDependencies makeDeps() {
        return makeDeps(config, taskManager, eventQueue);
    }

    private TaskExecutorDependencies makeDeps(ControllerConfig cfg, TaskManager tm, EventQueue eq) {
        return new TaskExecutorDependencies(
            cfg != null ? cfg : new ControllerConfig(),
            mock(AbilityManager.class),
            mock(ContextEngine.class),
            tm != null ? tm : new TaskManager(cfg != null ? cfg : new ControllerConfig()),
            eq != null ? eq : mock(EventQueue.class)
        );
    }

    @BeforeEach
    void setUp() {
        config = ControllerConfig.builder()
            .scheduleInterval(0.1)
            .maxConcurrentTasks(3)
            .build();
        taskManager = new TaskManager(config);
        eventQueue = mock(EventQueue.class);
        card = makeCard("test-agent");
        scheduler = new TaskScheduler(
            config, taskManager,
            mock(ContextEngine.class),
            mock(AbilityManager.class),
            eventQueue, card
        );
    }

    @AfterEach
    void tearDown() {
        try {
            scheduler.stop();
        } catch (Exception ignored) {
        }
    }

    // ==================== Init & Properties ====================

    @Nested
    @DisplayName("Init & Properties Tests")
    class InitTests {

        @Test
        @DisplayName("Scheduler should store all injected dependencies")
        void testInitStoresAllDependencies() {
            assertSame(config, scheduler.getConfig());
            assertSame(taskManager, scheduler.getTaskManager());
            assertNotNull(scheduler.getTaskExecutorRegistry());
            assertInstanceOf(TaskExecutorRegistry.class, scheduler.getTaskExecutorRegistry());
            assertTrue(scheduler.getSessions().isEmpty());
            assertFalse(scheduler.isRunning());
        }

        @Test
        @DisplayName("Config setter should update internal config")
        void testConfigSetter() {
            ControllerConfig newConfig = ControllerConfig.builder()
                .maxConcurrentTasks(10)
                .build();
            scheduler.setConfig(newConfig);
            assertSame(newConfig, scheduler.getConfig());
        }
    }

    // ==================== Start / Stop Lifecycle ====================

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("start() should set running to True")
        void testStartSetsRunningFlag() throws Exception {
            scheduler.start();
            assertTrue(scheduler.isRunning());
            assertNotNull(scheduler.getSchedulerTask());
            scheduler.stop();
        }

        @Test
        @DisplayName("Calling start() when already running should not create a new scheduler task")
        void testIdempotentStart() throws Exception {
            scheduler.start();
            Future<?> firstTask = scheduler.getSchedulerTask();
            scheduler.start(); // Should log warning but not change task
            assertSame(firstTask, scheduler.getSchedulerTask());
            scheduler.stop();
        }

        @Test
        @DisplayName("stop() should set running to False")
        void testStopClearsRunningFlag() throws Exception {
            scheduler.start();
            scheduler.stop();
            assertFalse(scheduler.isRunning());
        }

        @Test
        @DisplayName("stop() should cancel all running asyncio tasks")
        void testStopCancelsRunningTasks() throws Exception {
            // Add a task and simulate it running
            Task task = new Task("s1", "running_t", "stub", TaskStatus.SUBMITTED);
            taskManager.addTask(task);
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            // Register a stub executor
            ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_COMPLETION.getValue(),
                    List.of(new TextDataFrame("done")),
                    null
                ),
                false
            );
            scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "stub", deps -> new StubTaskExecutor(deps, List.of(completionChunk))
            );

            scheduler.start();
            Thread.sleep(500);
            scheduler.stop();

            // After stop, running tasks should be cleaned up
            assertFalse(scheduler.isRunning());
        }
    }

    // ==================== Schedule Loop ====================

    @Nested
    @DisplayName("Schedule Loop Tests")
    class ScheduleLoopTests {

        @Test
        @DisplayName("Schedule loop should pick up SUBMITTED tasks and create asyncio tasks")
        void testSchedulePicksSubmittedTasks() throws Exception {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_COMPLETION.getValue(),
                    List.of(new TextDataFrame("done")),
                    null
                ),
                false
            );
            scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "stub", deps -> new StubTaskExecutor(deps, List.of(completionChunk))
            );

            Task task = new Task("s1", "sched_t", "stub", TaskStatus.SUBMITTED);
            taskManager.addTask(task);

            scheduler.start();
            Thread.sleep(500);
            scheduler.stop();

            // Task should have been picked up and executed
            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("sched_t").build());
            assertEquals(1, tasks.size());
            // Status should be COMPLETED after execution
            assertEquals(TaskStatus.COMPLETED, tasks.get(0).getStatus());
        }

        @Test
        @DisplayName("Schedule should skip tasks whose session_id is not in sessions dict")
        void testScheduleSkipsMissingSession() throws Exception {
            Task task = new Task("no-such-session", "orphan_t", "stub", TaskStatus.SUBMITTED);
            taskManager.addTask(task);

            scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "stub", StubTaskExecutor::new
            );

            scheduler.start();
            Thread.sleep(300);
            scheduler.stop();

            // Task should still be SUBMITTED (not picked up)
            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("orphan_t").build());
            assertEquals(TaskStatus.SUBMITTED, tasks.get(0).getStatus());
        }

        @Test
        @DisplayName("Schedule loop should not exceed max_concurrent_tasks")
        void testScheduleRespectsMaxConcurrentTasks() throws Exception {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            // Create a slow executor
            scheduler.getTaskExecutorRegistry().addTaskExecutor("slow", deps -> new TaskExecutor(deps) {
                @Override
                public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of(new ControllerOutputChunk(
                        0, "controller_output",
                        new ControllerOutputPayload(
                            EventType.TASK_COMPLETION.getValue(),
                            List.of(new TextDataFrame("done")),
                            null
                        ),
                        false
                    )).iterator();
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
            });

            // Add more tasks than max_concurrent_tasks (3)
            for (int i = 0; i < 5; i++) {
                taskManager.addTask(new Task("s1", "max_t" + i, "slow", TaskStatus.SUBMITTED));
            }

            scheduler.start();
            Thread.sleep(500);

            // Should not exceed max_concurrent_tasks
            assertTrue(scheduler.getRunningTasks().size() <= config.getMaxConcurrentTasks());

            scheduler.stop();
        }
    }

    // ==================== execute_task() ====================

    @Nested
    @DisplayName("Execute Task Tests")
    class ExecuteTaskTests {

        @Test
        @DisplayName("executeTask with non-existent task_id should raise")
        void testExecuteTaskMissingTaskRaises() {
            Session session = makeSession("s1");
            assertThrows(BaseError.class,
                () -> scheduler.executeTask("nonexistent", session));
        }

        @Test
        @DisplayName("executeTask should transition to WORKING then COMPLETED")
        void testExecuteTaskCompletionFlow() {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_COMPLETION.getValue(),
                    List.of(new TextDataFrame("result")),
                    null
                ),
                false
            );
            scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "stub", deps -> new StubTaskExecutor(deps, List.of(completionChunk))
            );

            Task task = new Task("s1", "exec_t", "stub", TaskStatus.SUBMITTED);
            taskManager.addTask(task);

            // Manually record in running_tasks
            scheduler.getRunningTasks().put("exec_t",
                new TaskScheduler.TaskEntry(null, null));

            scheduler.executeTask("exec_t", session);

            // Task status should be COMPLETED
            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("exec_t").build());
            assertEquals(TaskStatus.COMPLETED, tasks.get(0).getStatus());
            // writeStream should have been called
            verify(session, atLeastOnce()).writeStream(any());
        }

        @Test
        @DisplayName("Task yielding TASK_INTERACTION chunk should set INPUT_REQUIRED status")
        void testExecuteTaskInteractionFlow() {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            ControllerOutputChunk interactionChunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_INTERACTION.getValue(),
                    List.of(new TextDataFrame("need info")),
                    null
                ),
                false
            );
            scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "stub", deps -> new StubTaskExecutor(deps, List.of(interactionChunk))
            );

            Task task = Task.builder("s1", "int_t", "stub")
                .status(TaskStatus.SUBMITTED)
                .inputRequiredFields(Map.of("extra_info", "string"))
                .build();
            taskManager.addTask(task);
            scheduler.getRunningTasks().put("int_t",
                new TaskScheduler.TaskEntry(null, null));

            scheduler.executeTask("int_t", session);

            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("int_t").build());
            assertEquals(TaskStatus.INPUT_REQUIRED, tasks.get(0).getStatus());
        }

        @Test
        @DisplayName("Task yielding TASK_FAILED chunk should set FAILED status")
        void testExecuteTaskFailedFlow() {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            ControllerOutputChunk failedChunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_FAILED.getValue(),
                    List.of(new TextDataFrame("error occurred")),
                    null
                ),
                false
            );
            scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "stub", deps -> new StubTaskExecutor(deps, List.of(failedChunk))
            );

            Task task = new Task("s1", "fail_t", "stub", TaskStatus.SUBMITTED);
            taskManager.addTask(task);
            scheduler.getRunningTasks().put("fail_t",
                new TaskScheduler.TaskEntry(null, null));

            scheduler.executeTask("fail_t", session);

            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("fail_t").build());
            assertEquals(TaskStatus.FAILED, tasks.get(0).getStatus());
        }
    }

    // ==================== execute_task_wrapper ====================

    @Nested
    @DisplayName("Execute Task Wrapper Tests")
    class ExecuteTaskWrapperTests {

        @Test
        @DisplayName("Exception during execute_task should be caught and task marked FAILED")
        void testExceptionHandlingInWrapper() {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            // Create an executor that throws
            scheduler.getTaskExecutorRegistry().addTaskExecutor("fail", deps -> new TaskExecutor(deps) {
                @Override
                public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
                    throw new RuntimeException("boom");
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
            });

            Task task = new Task("s1", "crash_t", "fail", TaskStatus.SUBMITTED);
            taskManager.addTask(task);
            scheduler.getRunningTasks().put("crash_t",
                new TaskScheduler.TaskEntry(null, null));

            scheduler.executeTaskWrapper("crash_t", session);

            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("crash_t").build());
            assertEquals(TaskStatus.FAILED, tasks.get(0).getStatus());
        }
    }

    // ==================== pause_task / cancel_task ====================

    @Nested
    @DisplayName("Pause / Cancel Tests")
    class PauseCancelTests {

        @Test
        @DisplayName("Pausing a non-existent task should return false")
        void testPauseTaskNotFound() {
            assertFalse(scheduler.pauseTask("nonexistent"));
        }

        @Test
        @DisplayName("Pausing a task whose session is not registered should return false")
        void testPauseTaskNoSession() {
            Task task = new Task("s1", "pt", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            assertFalse(scheduler.pauseTask("pt"));
        }

        @Test
        @DisplayName("Pausing a task not in running_tasks should return false")
        void testPauseTaskNotRunning() {
            Task task = new Task("s1", "pt", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            scheduler.getSessions().put("s1", makeSession("s1"));
            assertFalse(scheduler.pauseTask("pt"));
        }

        @Test
        @DisplayName("Successful pause should update status to PAUSED and clean up running_tasks")
        void testPauseTaskSuccess() {
            Task task = new Task("s1", "pt", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            TaskExecutorDependencies deps = makeDeps();
            StubTaskExecutor executor = new StubTaskExecutor(deps);
            Future<?> execTask = mock(Future.class);
            when(execTask.isDone()).thenReturn(false);
            scheduler.getRunningTasks().put("pt",
                new TaskScheduler.TaskEntry(executor, execTask));

            assertTrue(scheduler.pauseTask("pt"));

            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("pt").build());
            assertEquals(TaskStatus.PAUSED, tasks.get(0).getStatus());
            assertFalse(scheduler.getRunningTasks().containsKey("pt"));
        }

        @Test
        @DisplayName("Pausing a task whose executor.canPause returns false should fail")
        void testPauseNonPausableReturnsFalse() {
            Task task = new Task("s1", "np", "non_p", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            TaskExecutorDependencies deps = makeDeps();
            NonPausableExecutor executor = new NonPausableExecutor(deps);
            Future<?> execTask = mock(Future.class);
            when(execTask.isDone()).thenReturn(false);
            scheduler.getRunningTasks().put("np",
                new TaskScheduler.TaskEntry(executor, execTask));

            assertFalse(scheduler.pauseTask("np"));
        }

        @Test
        @DisplayName("Successful cancel should update status to CANCELED and clean up")
        void testCancelTaskSuccess() {
            Task task = new Task("s1", "ct", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            TaskExecutorDependencies deps = makeDeps();
            StubTaskExecutor executor = new StubTaskExecutor(deps);
            Future<?> execTask = mock(Future.class);
            when(execTask.isDone()).thenReturn(false);
            scheduler.getRunningTasks().put("ct",
                new TaskScheduler.TaskEntry(executor, execTask));

            assertTrue(scheduler.cancelTask("ct"));

            List<Task> tasks = taskManager.getTask(TaskFilter.builder().taskId("ct").build());
            assertEquals(TaskStatus.CANCELED, tasks.get(0).getStatus());
        }

        @Test
        @DisplayName("Cancelling a task whose executor.canCancel returns false should fail")
        void testCancelNonCancellableReturnsFalse() {
            Task task = new Task("s1", "nc", "non_c", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            TaskExecutorDependencies deps = makeDeps();
            NonPausableExecutor executor = new NonPausableExecutor(deps);
            Future<?> execTask = mock(Future.class);
            when(execTask.isDone()).thenReturn(false);
            scheduler.getRunningTasks().put("nc",
                new TaskScheduler.TaskEntry(executor, execTask));

            assertFalse(scheduler.cancelTask("nc"));
        }
    }

    // ==================== _publish_task_event ====================

    @Nested
    @DisplayName("Publish Task Event Tests")
    class PublishTaskEventTests {

        @Test
        @DisplayName("TASK_COMPLETION chunk should publish TaskCompletionEvent")
        void testPublishCompletionEvent() {
            Task task = new Task("s1", "pub_t", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");

            ControllerOutputChunk chunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_COMPLETION.getValue(),
                    List.of(new TextDataFrame("done")),
                    null
                ),
                false
            );

            scheduler.publishTaskEvent("pub_t", session, chunk);

            verify(eventQueue).publishEvent(eq("test-agent"), eq(session), argThat(event ->
                event instanceof TaskCompletionEvent
            ));
        }

        @Test
        @DisplayName("TASK_INTERACTION chunk should publish TaskInteractionEvent")
        void testPublishInteractionEvent() {
            Task task = new Task("s1", "pub_int", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");

            ControllerOutputChunk chunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_INTERACTION.getValue(),
                    List.of(new TextDataFrame("need info")),
                    null
                ),
                false
            );

            scheduler.publishTaskEvent("pub_int", session, chunk);

            verify(eventQueue).publishEvent(eq("test-agent"), eq(session), argThat(event ->
                event instanceof TaskInteractionEvent
            ));
        }

        @Test
        @DisplayName("TASK_FAILED chunk should publish TaskFailedEvent")
        void testPublishFailedEvent() {
            Task task = new Task("s1", "pub_fail", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");

            ControllerOutputChunk chunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload(
                    EventType.TASK_FAILED.getValue(),
                    List.of(new TextDataFrame("error")),
                    null
                ),
                false
            );

            scheduler.publishTaskEvent("pub_fail", session, chunk);

            verify(eventQueue).publishEvent(eq("test-agent"), eq(session), argThat(event ->
                event instanceof TaskFailedEvent
            ));
        }

        @Test
        @DisplayName("Chunk without payload should not publish any event")
        void testPublishMissingPayloadReturnsEarly() {
            Task task = new Task("s1", "pub_none", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");

            ControllerOutputChunk chunk = new ControllerOutputChunk(0);

            scheduler.publishTaskEvent("pub_none", session, chunk);

            verify(eventQueue, never()).publishEvent(any(), any(), any());
        }

        @Test
        @DisplayName("Chunk with unsupported payload type should not publish event")
        void testPublishUnsupportedTypeReturnsEarly() {
            Task task = new Task("s1", "pub_unk", "stub", TaskStatus.WORKING);
            taskManager.addTask(task);
            Session session = makeSession("s1");

            ControllerOutputChunk chunk = new ControllerOutputChunk(
                0, "controller_output",
                new ControllerOutputPayload("processing", List.of(), null),
                false
            );

            scheduler.publishTaskEvent("pub_unk", session, chunk);

            verify(eventQueue, never()).publishEvent(any(), any(), any());
        }
    }

    // ==================== _are_all_tasks_completed ====================

    @Nested
    @DisplayName("All Tasks Completed Tests")
    class AllTasksCompletedTests {

        @Test
        @DisplayName("No tasks for session should return false")
        void testNoTasksReturnsFalse() {
            assertFalse(scheduler.areAllTasksCompleted("empty-session"));
        }

        @Test
        @DisplayName("All tasks in terminal states should return true")
        void testAllTerminalReturnsTrue() {
            taskManager.addTask(List.of(
                new Task("s1", "ct1", "stub", TaskStatus.COMPLETED),
                Task.builder("s1", "ct2", "stub")
                    .status(TaskStatus.FAILED)
                    .errorMessage("err")
                    .build()
            ));
            assertTrue(scheduler.areAllTasksCompleted("s1"));
        }

        @Test
        @DisplayName("Active (SUBMITTED/WORKING) tasks should return false")
        void testActiveTaskReturnsFalse() {
            taskManager.addTask(List.of(
                new Task("s1", "at1", "stub", TaskStatus.COMPLETED),
                new Task("s1", "at2", "stub", TaskStatus.WORKING)
            ));
            assertFalse(scheduler.areAllTasksCompleted("s1"));
        }
    }

    // ==================== _ensure_session_completion_signal ====================

    @Nested
    @DisplayName("Ensure Completion Signal Tests")
    class EnsureCompletionSignalTests {

        @Test
        @DisplayName("Should write completion chunk to session when all tasks complete")
        void testSendsSignalWhenAllDone() {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            taskManager.addTask(
                new Task("s1", "sig_t", "stub", TaskStatus.COMPLETED)
            );

            scheduler.ensureSessionCompletionSignal("s1");

            verify(session).writeStream(argThat(data -> {
                if (data instanceof ControllerOutputChunk chunk) {
                    return chunk.getPayload() != null
                        && "all_tasks_processed".equals(chunk.getPayload().getType())
                        && chunk.isLastChunk();
                }
                return false;
            }));
        }

        @Test
        @DisplayName("Should not send signal when tasks are still active")
        void testNoSignalWhenTasksActive() {
            Session session = makeSession("s1");
            scheduler.getSessions().put("s1", session);

            taskManager.addTask(
                new Task("s1", "active_t", "stub", TaskStatus.WORKING)
            );

            scheduler.ensureSessionCompletionSignal("s1");

            verify(session, never()).writeStream(any());
        }

        @Test
        @DisplayName("Missing session should log warning but not raise")
        void testMissingSessionDoesNotRaise() {
            taskManager.addTask(
                new Task("s1", "ms_t", "stub", TaskStatus.COMPLETED)
            );
            // No session registered — should not raise
            assertDoesNotThrow(() -> scheduler.ensureSessionCompletionSignal("s1"));
        }

        @Test
        @DisplayName("Exceptions in completion signal should be caught and swallowed")
        void testExceptionIsSwallowed() {
            Session session = makeSession("s1");
            when(session.writeStream(any())).thenThrow(new RuntimeException("write failed"));
            scheduler.getSessions().put("s1", session);

            taskManager.addTask(
                new Task("s1", "exc_t", "stub", TaskStatus.COMPLETED)
            );

            // Should not raise
            assertDoesNotThrow(() -> scheduler.ensureSessionCompletionSignal("s1"));
        }
    }
}

