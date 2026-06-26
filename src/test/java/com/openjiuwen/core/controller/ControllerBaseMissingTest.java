/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

/**
 * Supplemental controller parity tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
 */
class ControllerBaseMissingTest {

    @Test
    void pauseTaskInEventHandler() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(new PauseTaskOnCompletionHandler("pause_test_task_2"));
        harness.registerExecutor("cancellable", dependencies -> new ControllableExecutor(dependencies, true, true));
        harness.start();
        try {
            harness.addTask("pause_test_task_2", "cancellable");
            waitUntil(() -> harness.status("pause_test_task_2") == TaskStatus.WORKING);
            harness.addTask("pause_test_task_1", "complete");

            waitUntil(() -> harness.status("pause_test_task_2") == TaskStatus.PAUSED);

            assertThat(harness.status("pause_test_task_1")).isEqualTo(TaskStatus.COMPLETED);
            assertThat(harness.status("pause_test_task_2")).isEqualTo(TaskStatus.PAUSED);
        } finally {
            harness.stop();
        }
    }

    @Test
    void cancelTaskInEventHandler() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(new CancelTaskOnCompletionHandler("cancel_test_task_2"));
        harness.registerExecutor("cancellable", dependencies -> new ControllableExecutor(dependencies, true, true));
        harness.start();
        try {
            harness.addTask("cancel_test_task_2", "cancellable");
            waitUntil(() -> harness.status("cancel_test_task_2") == TaskStatus.WORKING);
            harness.addTask("cancel_test_task_1", "complete");

            waitUntil(() -> harness.status("cancel_test_task_2") == TaskStatus.CANCELED);

            assertThat(harness.status("cancel_test_task_1")).isEqualTo(TaskStatus.COMPLETED);
            assertThat(harness.status("cancel_test_task_2")).isEqualTo(TaskStatus.CANCELED);
        } finally {
            harness.stop();
        }
    }

    @Test
    void pauseNonPausableTaskInEventHandler() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(new PauseTaskOnCompletionHandler("non_pausable_task_2"));
        harness.registerExecutor("non_pausable", dependencies -> new ControllableExecutor(dependencies, false, true));
        harness.start();
        try {
            harness.addTask("non_pausable_task_2", "non_pausable");
            waitUntil(() -> harness.status("non_pausable_task_2") == TaskStatus.WORKING);
            harness.addTask("non_pausable_task_1", "complete");

            waitUntil(() -> harness.status("non_pausable_task_2") == TaskStatus.COMPLETED);

            assertThat(harness.status("non_pausable_task_2")).isNotEqualTo(TaskStatus.PAUSED);
            assertThat(harness.handler().lastPauseResult()).isFalse();
        } finally {
            harness.stop();
        }
    }

    @Test
    void cancelNonCancellableTaskInEventHandler() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(new CancelTaskOnCompletionHandler("non_cancel_task_2"));
        harness.registerExecutor("non_cancel", dependencies -> new ControllableExecutor(dependencies, true, false));
        harness.start();
        try {
            harness.addTask("non_cancel_task_2", "non_cancel");
            waitUntil(() -> harness.status("non_cancel_task_2") == TaskStatus.WORKING);
            harness.addTask("non_cancel_task_1", "complete");

            waitUntil(() -> harness.status("non_cancel_task_2") == TaskStatus.COMPLETED);

            assertThat(harness.status("non_cancel_task_2")).isNotEqualTo(TaskStatus.CANCELED);
            assertThat(harness.handler().lastCancelResult()).isFalse();
        } finally {
            harness.stop();
        }
    }

    @Test
    void pauseThenCancelInEventHandler() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(
                new PauseAndCancelOnCompletionHandler("mixed_task_2", "mixed_task_3"));
        harness.registerExecutor("cancellable", dependencies -> new ControllableExecutor(dependencies, true, true));
        harness.start();
        try {
            harness.addTask("mixed_task_2", "cancellable");
            harness.addTask("mixed_task_3", "cancellable");
            waitUntil(() -> harness.status("mixed_task_2") == TaskStatus.WORKING
                    && harness.status("mixed_task_3") == TaskStatus.WORKING);
            harness.addTask("mixed_task_1", "complete");

            waitUntil(() -> harness.status("mixed_task_2") == TaskStatus.PAUSED
                    && harness.status("mixed_task_3") == TaskStatus.CANCELED);

            assertThat(harness.status("mixed_task_1")).isEqualTo(TaskStatus.COMPLETED);
            assertThat(harness.status("mixed_task_2")).isEqualTo(TaskStatus.PAUSED);
            assertThat(harness.status("mixed_task_3")).isEqualTo(TaskStatus.CANCELED);
        } finally {
            harness.stop();
        }
    }

    @Test
    void pauseNonExistentTask() {
        SchedulerHarness harness = SchedulerHarness.create(new RecordingHandler());
        harness.scheduler().getSessions().put(harness.session().getSessionId(), harness.session());

        assertThat(harness.scheduler().pauseTask("missing-task")).isFalse();
    }

    @Test
    void pauseCompletedTask() {
        SchedulerHarness harness = SchedulerHarness.create(new RecordingHandler());
        harness.scheduler().getSessions().put(harness.session().getSessionId(), harness.session());
        harness.taskManager().addTask(task(harness.session().getSessionId(), "completed-task", "complete",
                TaskStatus.COMPLETED));

        assertThat(harness.scheduler().pauseTask("completed-task")).isFalse();
        assertThat(harness.status("completed-task")).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void pauseWithExecutorException() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(new RecordingHandler());
        harness.registerExecutor("pause_throws", dependencies -> new ThrowingControlExecutor(dependencies, true, false));
        harness.start();
        try {
            harness.addTask("pause-throws-task", "pause_throws");
            waitUntil(() -> harness.status("pause-throws-task") == TaskStatus.WORKING);

            assertThat(harness.scheduler().pauseTask("pause-throws-task")).isFalse();
            assertThat(harness.status("pause-throws-task")).isEqualTo(TaskStatus.WORKING);
        } finally {
            harness.stop();
        }
    }

    @Test
    void cancelNonExistentTask() {
        SchedulerHarness harness = SchedulerHarness.create(new RecordingHandler());
        harness.scheduler().getSessions().put(harness.session().getSessionId(), harness.session());

        assertThat(harness.scheduler().cancelTask("missing-task")).isFalse();
    }

    @Test
    void cancelCompletedTask() {
        SchedulerHarness harness = SchedulerHarness.create(new RecordingHandler());
        harness.scheduler().getSessions().put(harness.session().getSessionId(), harness.session());
        harness.taskManager().addTask(task(harness.session().getSessionId(), "completed-task", "complete",
                TaskStatus.COMPLETED));

        assertThat(harness.scheduler().cancelTask("completed-task")).isFalse();
        assertThat(harness.status("completed-task")).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void cancelWithExecutorException() throws Exception {
        SchedulerHarness harness = SchedulerHarness.create(new RecordingHandler());
        harness.registerExecutor("cancel_throws", dependencies -> new ThrowingControlExecutor(dependencies, false, true));
        harness.start();
        try {
            harness.addTask("cancel-throws-task", "cancel_throws");
            waitUntil(() -> harness.status("cancel-throws-task") == TaskStatus.WORKING);

            assertThat(harness.scheduler().cancelTask("cancel-throws-task")).isFalse();
            assertThat(harness.status("cancel-throws-task")).isEqualTo(TaskStatus.WORKING);
        } finally {
            harness.stop();
        }
    }

    @Test
    void pausedTaskStatePersistence() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(task("session-1", "persist_task_2", "cancellable", TaskStatus.PAUSED));
        TaskManager restored = new TaskManager(new ControllerConfig());

        restored.loadState(taskManager.getState());

        assertThat(restored.getTask(TaskFilter.byTaskId("persist_task_2")).get(0).getStatus())
                .isEqualTo(TaskStatus.PAUSED);
    }

    @Test
    void multiTaskStatePersistence() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(List.of(
                task("session-1", "multi_task_1", "cancellable", TaskStatus.COMPLETED),
                task("session-1", "multi_task_2", "cancellable", TaskStatus.PAUSED),
                task("session-1", "multi_task_3", "cancellable", TaskStatus.CANCELED)
        ));
        TaskManager restored = new TaskManager(new ControllerConfig());

        restored.loadState(taskManager.getState());

        assertThat(statusMap(restored))
                .containsEntry("multi_task_1", TaskStatus.COMPLETED)
                .containsEntry("multi_task_2", TaskStatus.PAUSED)
                .containsEntry("multi_task_3", TaskStatus.CANCELED);
    }

    @Test
    void stateRestorationFailureFallback() {
        Controller controller = controller(new ControllerConfig(), new RecordingHandler());
        FakeSession session = new FakeSession("fallback-session");
        session.updateState(Map.of("controller", Map.of("task_manager_state", "invalid_data")));

        assertThatCode(() -> controller.invoke(new InputEvent(), session)).doesNotThrowAnyException();
        assertThat(controller.getTaskManager().getTask(null)).isEmpty();
    }

    @Test
    void controllerStopCleanupAll() {
        Controller controller = controller(new ControllerConfig(), new RecordingHandler());
        FakeSession session = new FakeSession("stop-session");
        controller.start();
        controller.getTaskScheduler().getSessions().put(session.getSessionId(), session);

        controller.stop();

        assertThat(controller.getTaskScheduler().getSessions()).isEmpty();
    }

    @Test
    void multipleStreamCallsNoDuplicateStart() {
        Controller controller = controller(new ControllerConfig(), new RecordingHandler());
        FakeSession first = new FakeSession("stream-session-1");
        FakeSession second = new FakeSession("stream-session-2");

        ControllerOutput firstOutput = controller.invoke(new InputEvent(), first);
        EventQueue eventQueue = controller.getEventQueue();
        TaskScheduler taskScheduler = controller.getTaskScheduler();
        ControllerOutput secondOutput = controller.invoke(new InputEvent(), second);

        assertThat(firstOutput.getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(secondOutput.getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(controller.getEventQueue()).isSameAs(eventQueue);
        assertThat(controller.getTaskScheduler()).isSameAs(taskScheduler);
    }

    @Test
    void multiTurnConversationNoInterference() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(task("conversation", "test_task_1", "cancellable", TaskStatus.COMPLETED));
        taskManager.clearState();
        taskManager.addTask(task("conversation", "test_task_2", "cancellable", TaskStatus.SUBMITTED));

        List<Task> tasks = taskManager.getTask(TaskFilter.bySessionId("conversation"));

        assertThat(tasks).extracting(Task::getTaskId).containsExactly("test_task_2");
    }

    @Test
    void sessionRegistrationAndCleanup() {
        Controller controller = controller(new ControllerConfig(), new RecordingHandler());
        FakeSession session = new FakeSession("registration-session");

        controller.invoke(new InputEvent(), session);

        assertThat(controller.getTaskScheduler().getSessions()).doesNotContainKey(session.getSessionId());
    }

    @Test
    void eventSubscribeAndPublish() {
        EventQueue eventQueue = new EventQueue(new ControllerConfig());
        RecordingHandler handler = new RecordingHandler();
        FakeSession session = new FakeSession("event-session");
        eventQueue.setEventHandler(handler);
        eventQueue.start();

        EventQueue.SubscriptionResult result = eventQueue.subscribe("agent-1", session.getSessionId());
        eventQueue.publishEvent("agent-1", session, new InputEvent());

        assertThat(result.subscriptions()).hasSize(5);
        assertThat(handler.inputCount()).isEqualTo(1);
        eventQueue.stop();
    }

    @Test
    void unsubscribeCleanup() {
        EventQueue eventQueue = new EventQueue(new ControllerConfig());
        RecordingHandler handler = new RecordingHandler();
        FakeSession session = new FakeSession("unsubscribe-session");
        eventQueue.setEventHandler(handler);
        eventQueue.start();

        eventQueue.subscribe("agent-1", session.getSessionId());
        eventQueue.unsubscribe("agent-1", session.getSessionId());
        eventQueue.publishEvent("agent-1", session, new InputEvent());
        eventQueue.subscribe("agent-1", session.getSessionId());
        eventQueue.publishEvent("agent-1", session, new InputEvent());

        assertThat(handler.inputCount()).isEqualTo(1);
        eventQueue.stop();
    }

    @Test
    void handleTaskInteraction() {
        EventQueue eventQueue = new EventQueue(new ControllerConfig());
        RecordingHandler handler = new RecordingHandler();
        FakeSession session = new FakeSession("interaction-session");
        Task task = task(session.getSessionId(), "interaction-task", "interaction", TaskStatus.WORKING);
        eventQueue.setEventHandler(handler);
        eventQueue.start();
        eventQueue.subscribe("agent-1", session.getSessionId());

        eventQueue.publishEvent("agent-1", session, new TaskInteractionEvent(
                List.of(new DataFrame.TextDataFrame("needs user input")), task));

        assertThat(handler.interactionHandled()).isTrue();
        assertThat(handler.lastEvent()).isInstanceOf(TaskInteractionEvent.class);
        eventQueue.stop();
    }

    private static Controller controller(ControllerConfig config, EventHandler eventHandler) {
        Controller controller = new Controller();
        controller.init(new BaseCard("agent-1", "agent", "test agent"), config,
                new AbilityManager(), new ContextEngine());
        controller.setEventHandler(eventHandler);
        return controller;
    }

    private static Task task(String sessionId, String taskId, String taskType, TaskStatus status) {
        Task task = new Task(sessionId, taskId, taskType);
        task.setStatus(status);
        return task;
    }

    private static Map<String, TaskStatus> statusMap(TaskManager taskManager) {
        Map<String, TaskStatus> result = new LinkedHashMap<>();
        for (Task task : taskManager.getTask(null)) {
            result.put(task.getTaskId(), task.getStatus());
        }
        return result;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        fail("condition was not met before timeout");
    }

    /**
     * Test harness for scheduler-level branches.
     *
     * <p>Mirrors Python's controller test fixtures in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static final class SchedulerHarness {
        private final ControllerConfig config;
        private final TaskManager taskManager;
        private final EventQueue eventQueue;
        private final TaskScheduler scheduler;
        private final BaseCard card;
        private final FakeSession session;
        private final RecordingHandler handler;

        private SchedulerHarness(RecordingHandler handler) {
            this.config = new ControllerConfig();
            this.config.setScheduleInterval(0.1D);
            this.config.setMaxConcurrentTasks(5);
            this.taskManager = new TaskManager(config);
            this.eventQueue = new EventQueue(config);
            this.card = new BaseCard("agent-1", "agent", "test agent");
            this.session = new FakeSession("session-1");
            this.handler = handler;
            this.scheduler = new TaskScheduler(
                    config,
                    taskManager,
                    new ContextEngine(),
                    new AbilityManager(),
                    eventQueue,
                    card
            );
            handler.setTaskScheduler(scheduler);
            handler.setTaskManager(taskManager);
            eventQueue.setEventHandler(handler);
            scheduler.getTaskExecutorRegistry().addTaskExecutor("complete", CompletingExecutor::new);
        }

        static SchedulerHarness create(RecordingHandler handler) {
            return new SchedulerHarness(handler);
        }

        void start() {
            eventQueue.start();
            eventQueue.subscribe(card.getId(), session.getSessionId());
            scheduler.getSessions().put(session.getSessionId(), session);
            scheduler.start();
        }

        void stop() {
            scheduler.stop();
            eventQueue.stop();
        }

        void registerExecutor(String taskType,
                              java.util.function.Function<TaskExecutorDependencies, TaskExecutor> builder) {
            scheduler.getTaskExecutorRegistry().addTaskExecutor(taskType, builder);
        }

        void addTask(String taskId, String taskType) {
            taskManager.addTask(task(session.getSessionId(), taskId, taskType, TaskStatus.SUBMITTED));
        }

        TaskStatus status(String taskId) {
            List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
            return tasks.isEmpty() ? null : tasks.get(0).getStatus();
        }

        TaskScheduler scheduler() {
            return scheduler;
        }

        TaskManager taskManager() {
            return taskManager;
        }

        FakeSession session() {
            return session;
        }

        RecordingHandler handler() {
            return handler;
        }
    }

    /**
     * Recording event handler used by controller parity tests.
     *
     * <p>Mirrors Python's test event handlers in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static class RecordingHandler extends EventHandler {
        private final AtomicInteger inputCount = new AtomicInteger();
        private final AtomicBoolean interactionHandled = new AtomicBoolean();
        private volatile Event lastEvent;
        protected volatile Boolean lastPauseResult;
        protected volatile Boolean lastCancelResult;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            inputCount.incrementAndGet();
            lastEvent = inputs.getEvent();
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            interactionHandled.set(true);
            lastEvent = inputs.getEvent();
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            lastEvent = inputs.getEvent();
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            lastEvent = inputs.getEvent();
            return Map.of("status", "failed");
        }

        int inputCount() {
            return inputCount.get();
        }

        boolean interactionHandled() {
            return interactionHandled.get();
        }

        Event lastEvent() {
            return lastEvent;
        }

        Boolean lastPauseResult() {
            return lastPauseResult;
        }

        Boolean lastCancelResult() {
            return lastCancelResult;
        }
    }

    /**
     * Completion handler that pauses another task.
     *
     * <p>Mirrors Python's {@code PauseInHandlerEventHandler} in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static final class PauseTaskOnCompletionHandler extends RecordingHandler {
        private final String targetTaskId;
        private final AtomicBoolean called = new AtomicBoolean();

        private PauseTaskOnCompletionHandler(String targetTaskId) {
            this.targetTaskId = targetTaskId;
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            super.handleTaskCompletion(inputs);
            if (called.compareAndSet(false, true)) {
                lastPauseResult = getTaskScheduler().pauseTask(targetTaskId);
            }
            return Map.of("status", "success");
        }
    }

    /**
     * Completion handler that cancels another task.
     *
     * <p>Mirrors Python's {@code CancelInHandlerEventHandler} in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static final class CancelTaskOnCompletionHandler extends RecordingHandler {
        private final String targetTaskId;
        private final AtomicBoolean called = new AtomicBoolean();

        private CancelTaskOnCompletionHandler(String targetTaskId) {
            this.targetTaskId = targetTaskId;
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            super.handleTaskCompletion(inputs);
            if (called.compareAndSet(false, true)) {
                lastCancelResult = getTaskScheduler().cancelTask(targetTaskId);
            }
            return Map.of("status", "success");
        }
    }

    /**
     * Completion handler that pauses and cancels sibling tasks.
     *
     * <p>Mirrors Python's mixed task-control event-handler tests in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static final class PauseAndCancelOnCompletionHandler extends RecordingHandler {
        private final String pauseTaskId;
        private final String cancelTaskId;
        private final AtomicBoolean called = new AtomicBoolean();

        private PauseAndCancelOnCompletionHandler(String pauseTaskId, String cancelTaskId) {
            this.pauseTaskId = pauseTaskId;
            this.cancelTaskId = cancelTaskId;
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            super.handleTaskCompletion(inputs);
            if (called.compareAndSet(false, true)) {
                lastPauseResult = getTaskScheduler().pauseTask(pauseTaskId);
                lastCancelResult = getTaskScheduler().cancelTask(cancelTaskId);
            }
            return Map.of("status", "success");
        }
    }

    /**
     * Executor that immediately completes a task.
     *
     * <p>Mirrors Python's fast completing test executor in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static final class CompletingExecutor extends TaskExecutor {
        private CompletingExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return List.of(completionChunk(taskId)).iterator();
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(true, "");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            return true;
        }

        @Override
        public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
            return new CancelCheckResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            return true;
        }
    }

    /**
     * Executor with configurable pause/cancel support.
     *
     * <p>Mirrors Python's cancellable and non-cancellable task executors in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static class ControllableExecutor extends TaskExecutor {
        private final boolean canPause;
        private final boolean canCancel;
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean canceled = new AtomicBoolean();

        private ControllableExecutor(TaskExecutorDependencies dependencies, boolean canPause, boolean canCancel) {
            super(dependencies);
            this.canPause = canPause;
            this.canCancel = canCancel;
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return !paused.get() && !canceled.get() && !Thread.currentThread().isInterrupted() && index < 40;
                }

                @Override
                public ControllerOutputChunk next() {
                    index++;
                    sleepQuietly(25L);
                    if (index >= 40) {
                        return completionChunk(taskId);
                    }
                    return processingChunk(taskId, index);
                }
            };
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(canPause, canPause ? "" : "This task cannot be paused");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            paused.set(true);
            return true;
        }

        @Override
        public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
            return new CancelCheckResult(canCancel, canCancel ? "" : "This task cannot be cancelled");
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            canceled.set(true);
            return true;
        }
    }

    /**
     * Executor that raises during pause or cancel.
     *
     * <p>Mirrors Python's exception-throwing task executors in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
     */
    private static final class ThrowingControlExecutor extends ControllableExecutor {
        private final boolean throwOnPause;
        private final boolean throwOnCancel;

        private ThrowingControlExecutor(TaskExecutorDependencies dependencies,
                                        boolean throwOnPause,
                                        boolean throwOnCancel) {
            super(dependencies, true, true);
            this.throwOnPause = throwOnPause;
            this.throwOnCancel = throwOnCancel;
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            if (throwOnPause) {
                throw new IllegalStateException("Pause failed for " + taskId);
            }
            return super.pause(taskId, session);
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            if (throwOnCancel) {
                throw new IllegalStateException("Cancel failed for " + taskId);
            }
            return super.cancel(taskId, session);
        }
    }

    private static ControllerOutputChunk processingChunk(String taskId, int index) {
        return new ControllerOutputChunk(
                index,
                new ControllerOutputPayload(
                        ControllerOutputPayload.TASK_PROCESSING,
                        List.of(new DataFrame.TextDataFrame("Task " + taskId + " progress " + index))
                ),
                false
        );
    }

    private static ControllerOutputChunk completionChunk(String taskId) {
        return new ControllerOutputChunk(
                99,
                new ControllerOutputPayload(
                        EventType.TASK_COMPLETION.getValue(),
                        List.of(new DataFrame.TextDataFrame("Task " + taskId + " completed"))
                ),
                true
        );
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Test session implementation.
     *
     * <p>Mirrors Python's {@code Session} dependency in
     * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
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
            return streamSnapshot().iterator();
        }

        private List<Object> streamSnapshot() {
            synchronized (stream) {
                return new ArrayList<>(stream);
            }
        }
    }
}
