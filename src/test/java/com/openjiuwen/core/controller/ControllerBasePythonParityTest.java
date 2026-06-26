/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.AbilityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Missing-test parity coverage for controller base integration behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.controller.test_controller_base} in
 * {@code tests/unit_tests/core/controller/test_controller_base.py}.</p>
 */
class ControllerBasePythonParityTest {

    private final List<Harness> harnesses = new ArrayList<>();

    @AfterEach
    void stopHarnesses() {
        for (int i = harnesses.size() - 1; i >= 0; i--) {
            harnesses.get(i).stop();
        }
        harnesses.clear();
    }

    @Test
    void pauseTaskInEventHandlerLeavesSiblingTaskCompleting() {
        Harness harness = harness(
                "test_pause_in_handler",
                new PauseInHandlerEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        List<String> outputTexts = harness.collectStreamOutput("test pause in handler");

        assertThat(outputTexts).anySatisfy(text ->
                assertThat(text).contains("pause_test_task_1").contains("completed"));
        assertThat(task(harness, "pause_test_task_2").getStatus()).isEqualTo(TaskStatus.PAUSED);
        assertThat(task(harness, "pause_test_task_3").getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(outputTexts).anySatisfy(text ->
                assertThat(text).contains("pause_test_task_3").contains("completed"));
    }

    @Test
    void cancelTaskInEventHandlerMarksTargetCanceled() {
        Harness harness = harness(
                "test_cancel_in_handler",
                new CancelInHandlerEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        List<String> outputTexts = harness.collectStreamOutput("test cancel in handler");

        assertThat(outputTexts).anySatisfy(text ->
                assertThat(text).contains("cancel_test_task_1").contains("completed"));
        assertThat(task(harness, "cancel_test_task_2").getStatus()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void pauseNonPausableTaskInEventHandlerKeepsTaskCompleting() {
        Harness harness = harness(
                "test_pause_non_pausable",
                new PauseNonPausableEventHandler(),
                Map.of(
                        "cancellable", CancellableTaskExecutor::new,
                        "non_cancellable", NonCancellableTaskExecutor::new
                ),
                false
        );

        harness.collectStreamOutput("test pause non-pausable");

        assertThat(task(harness, "non_pausable_task").getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void cancelNonCancellableTaskInEventHandlerKeepsTaskCompleting() {
        Harness harness = harness(
                "test_cancel_non_cancellable",
                new CancelNonCancellableEventHandler(),
                Map.of(
                        "cancellable", CancellableTaskExecutor::new,
                        "non_cancellable", NonCancellableTaskExecutor::new
                ),
                false
        );

        harness.collectStreamOutput("test cancel non-cancellable");

        assertThat(task(harness, "non_cancellable_task").getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void pauseThenCancelInEventHandlerLeavesPausedTaskPaused() {
        Harness harness = harness(
                "test_pause_then_cancel",
                new PauseThenCancelEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        harness.collectStreamOutput("test pause then cancel");

        assertThat(task(harness, "multi_op_task_2").getStatus()).isEqualTo(TaskStatus.PAUSED);
    }

    @Test
    void pauseNonExistentTaskFailsGracefully() {
        Harness harness = harness(
                "test_pause_non_existent",
                new SimpleEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        assertThat(harness.controller.getTaskScheduler().pauseTask("non_existent_task")).isFalse();
    }

    @Test
    void pauseCompletedTaskFailsGracefully() {
        Harness harness = harness(
                "test_pause_completed",
                new SimpleEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        harness.collectStreamOutput("test pause completed");

        assertThat(harness.controller.getTaskScheduler().pauseTask("test_task_1")).isFalse();
    }

    @Test
    void pauseWithExecutorExceptionDoesNotMarkTaskPaused() {
        Harness harness = harness(
                "test_pause_exception",
                new PauseExceptionEventHandler(),
                Map.of(
                        "cancellable", CancellableTaskExecutor::new,
                        "pause_exception", PauseExceptionTaskExecutor::new
                ),
                false
        );

        List<String> outputTexts = harness.collectStreamOutput("test pause exception");

        assertThat(outputTexts).anySatisfy(text ->
                assertThat(text).contains("pause_exception_task_1").contains("completed"));
        assertThat(task(harness, "pause_exception_task_2").getStatus()).isNotEqualTo(TaskStatus.PAUSED);
    }

    @Test
    void cancelNonExistentTaskFailsGracefully() {
        Harness harness = harness(
                "test_cancel_non_existent",
                new SimpleEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        assertThat(harness.controller.getTaskScheduler().cancelTask("non_existent_task")).isFalse();
    }

    @Test
    void cancelCompletedTaskFailsAfterSessionCleanup() {
        Harness harness = harness(
                "test_cancel_completed",
                new SimpleEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        harness.collectStreamOutput("test cancel completed");

        assertThat(harness.controller.getTaskScheduler().cancelTask("test_task_1")).isFalse();
    }

    @Test
    void cancelWithExecutorExceptionDoesNotMarkTaskCanceled() {
        Harness harness = harness(
                "test_cancel_exception",
                new CancelExceptionEventHandler(),
                Map.of(
                        "cancellable", CancellableTaskExecutor::new,
                        "cancel_exception", CancelExceptionTaskExecutor::new
                ),
                false
        );

        List<String> outputTexts = harness.collectStreamOutput("test cancel exception");

        assertThat(outputTexts).anySatisfy(text ->
                assertThat(text).contains("cancel_exception_task_1").contains("completed"));
        assertThat(task(harness, "cancel_exception_task_2").getStatus()).isNotEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void pausedTaskStatePersistsAcrossStreamRounds() {
        Harness harness = harness(
                "test_state_persistence",
                new StatePersistenceEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                true
        );

        List<String> firstRound = harness.collectStreamOutput("round 1");
        Task firstRoundTask = task(harness, "persist_task_2");
        List<String> secondRound = harness.collectStreamOutput("round 2");
        Task secondRoundTask = task(harness, "persist_task_2");

        assertThat(firstRound).anySatisfy(text ->
                assertThat(text).contains("persist_task_1").contains("completed"));
        assertThat(secondRound).isEmpty();
        assertThat(firstRoundTask.getStatus()).isEqualTo(TaskStatus.PAUSED);
        assertThat(secondRoundTask.getStatus()).isEqualTo(TaskStatus.PAUSED);
        assertThat(secondRoundTask.getTaskId()).isEqualTo("persist_task_2");
        assertThat(secondRoundTask.getContextId()).isEqualTo("persist_context_2");
        assertThat(secondRoundTask.getPriority()).isEqualTo(1);
    }

    @Test
    void multiTaskStatePersistsAcrossStreamRounds() {
        Harness harness = harness(
                "test_multi_state_persistence",
                new MultiTaskStatePersistenceEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                true
        );

        harness.collectStreamOutput("round 1");
        Map<String, TaskStatus> firstStatusMap = statusMap(harness.controller.getTaskManager().getTask(null));
        harness.collectStreamOutput("round 2");
        List<Task> restoredTasks = harness.controller.getTaskManager().getTask(null);
        Map<String, TaskStatus> secondStatusMap = statusMap(restoredTasks);

        assertThat(firstStatusMap)
                .containsEntry("multi_task_1", TaskStatus.COMPLETED)
                .containsEntry("multi_task_2", TaskStatus.PAUSED)
                .containsEntry("multi_task_3", TaskStatus.CANCELED);
        assertThat(restoredTasks).hasSize(3);
        assertThat(secondStatusMap).isEqualTo(firstStatusMap);
        assertThat(taskById(restoredTasks, "multi_task_1").getPriority()).isEqualTo(1);
        assertThat(taskById(restoredTasks, "multi_task_1").getContextId()).isEqualTo("multi_context_1");
        assertThat(taskById(restoredTasks, "multi_task_2").getPriority()).isEqualTo(2);
        assertThat(taskById(restoredTasks, "multi_task_2").getContextId()).isEqualTo("multi_context_2");
        assertThat(taskById(restoredTasks, "multi_task_3").getPriority()).isEqualTo(3);
        assertThat(taskById(restoredTasks, "multi_task_3").getContextId()).isEqualTo("multi_context_3");
    }

    @Test
    void stateRestorationFailureFallsBackToCleanTaskManager() {
        Harness harness = harness(
                "test_fallback",
                new StatePersistenceEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                true
        );
        harness.collectStreamOutput("round 1");
        assertThat(task(harness, "persist_task_2").getStatus()).isEqualTo(TaskStatus.PAUSED);
        harness.session.updateState(Map.of("controller", Map.of("task_manager_state", "invalid_data")));

        assertThatNoException().isThrownBy(() -> harness.collectStreamOutput("round 2"));

        assertThat(harness.controller.getTaskManager().getTask(TaskFilter.byTaskId("persist_task_2"))).isEmpty();
    }

    @Test
    void controllerStopCleansSchedulerSessions() throws InterruptedException {
        Harness harness = harness(
                "test_stop_cleanup",
                new DynamicTaskEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );
        Iterator<Object> stream = harness.controller.stream(input("test stop"), harness.session, List.of());

        Thread.sleep(80L);
        drainIterator(stream, Duration.ofMillis(400));
        harness.stop();

        assertThat(harness.controller.getTaskScheduler().getSessions()).isEmpty();
    }

    @Test
    void multipleStreamCallsReuseControllerComponents() {
        Harness harness = harness(
                "test_multiple_start",
                new DynamicTaskEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        List<String> output1 = harness.collectStreamOutput("test");
        int eventQueueId = System.identityHashCode(harness.controller.getEventQueue());
        int taskSchedulerId = System.identityHashCode(harness.controller.getTaskScheduler());
        List<String> output2 = harness.collectStreamOutput("test");
        List<String> output3 = harness.collectStreamOutput("test");

        assertThat(output1).isNotEmpty();
        assertThat(output2).isNotEmpty();
        assertThat(output3).isNotEmpty();
        assertThat(System.identityHashCode(harness.controller.getEventQueue())).isEqualTo(eventQueueId);
        assertThat(System.identityHashCode(harness.controller.getTaskScheduler())).isEqualTo(taskSchedulerId);
    }

    @Test
    void multiTurnConversationCreatesIndependentTasks() {
        Harness harness = harness(
                "multi_turn",
                new DynamicTaskEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        List<String> firstTurn = harness.collectStreamOutput("turn 1");
        List<Task> tasksAfterFirstTurn = harness.controller.getTaskManager().getTask(null);
        List<String> secondTurn = harness.collectStreamOutput("turn 2");
        List<Task> tasksAfterSecondTurn = harness.controller.getTaskManager().getTask(null);

        assertThat(firstTurn).anySatisfy(text -> assertThat(text).contains("test_task_1"));
        assertThat(secondTurn).anySatisfy(text -> assertThat(text).contains("test_task_2"));
        assertThat(tasksAfterFirstTurn).isNotEmpty();
        assertThat(tasksAfterSecondTurn).isNotEmpty();
    }

    @Test
    void sessionRegistrationAndCleanupMatchesStreamLifecycle() {
        Harness harness = harness(
                "test_reg",
                new SimpleEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        assertThat(harness.controller.getTaskScheduler().getSessions()).isEmpty();
        Iterator<Object> stream = harness.controller.stream(input("test"), harness.session, List.of());
        Object firstChunk = nextWithin(stream, Duration.ofSeconds(3));

        assertThat(firstChunk).isNotNull();
        assertThat(harness.controller.getTaskScheduler().getSessions()).containsKey("test_reg");
        drainIterator(stream, Duration.ofSeconds(3));

        assertThat(harness.controller.getTaskScheduler().getSessions()).doesNotContainKey("test_reg");
    }

    @Test
    void eventSubscribeAndPublishRoutesInputAndTaskEvents() {
        Harness harness = harness(
                "test_pub_sub",
                new SimpleEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        List<String> output = harness.collectStreamOutput("test event system");

        assertThat(output).isNotEmpty();
        assertThat(output).anySatisfy(text -> assertThat(text).contains("started"));
    }

    @Test
    void unsubscribeCleanupPreventsSessionLeaksAcrossStreams() {
        Harness harness = harness(
                "test_unsubscribe",
                new DynamicTaskEventHandler(),
                Map.of("cancellable", CancellableTaskExecutor::new),
                false
        );

        List<String> output1 = harness.collectStreamOutput("test unsubscribe");
        boolean removedAfterFirst = !harness.controller.getTaskScheduler().getSessions().containsKey("test_unsubscribe");
        List<String> output2 = harness.collectStreamOutput("test unsubscribe");

        assertThat(output1).isNotEmpty();
        assertThat(output2).isNotEmpty();
        assertThat(removedAfterFirst).isTrue();
        assertThat(harness.controller.getTaskScheduler().getSessions()).doesNotContainKey("test_unsubscribe");
    }

    @Test
    void handleTaskInteractionIsCalledWhenExecutorRequestsInput() {
        InteractionEventHandler handler = new InteractionEventHandler();
        Harness harness = harness(
                "test_interaction",
                handler,
                Map.of("interaction", InteractionTaskExecutor::new),
                false
        );

        List<String> outputTexts = harness.collectStreamOutput("test interaction");

        assertThat(outputTexts).anySatisfy(text -> assertThat(text).contains("needs user input"));
        assertThat(handler.interactionHandled).isTrue();
    }

    private Harness harness(String sessionId, EventHandler eventHandler,
                            Map<String, Function<TaskExecutorDependencies, TaskExecutor>> executors,
                            boolean enablePersistence) {
        ControllerConfig config = new ControllerConfig();
        config.setScheduleInterval(0.1D);
        config.setMaxConcurrentTasks(5);
        config.setEnableTaskPersistence(enablePersistence);
        Controller controller = new Controller();
        controller.init(new BaseCard(sessionId, "agent", "test agent"),
                config,
                new AbilityManager(),
                new ContextEngine());
        controller.setEventHandler(eventHandler);
        executors.forEach(controller::addTaskExecutor);
        Harness harness = new Harness(controller, new LiveSession(sessionId));
        harnesses.add(harness);
        return harness;
    }

    private static InputEvent input(String query) {
        return InputEvent.fromUserInput(Map.of("query", query));
    }

    private static Task task(Harness harness, String taskId) {
        return taskById(harness.controller.getTaskManager().getTask(TaskFilter.byTaskId(taskId)), taskId);
    }

    private static Task taskById(List<Task> tasks, String taskId) {
        return tasks.stream()
                .filter(task -> taskId.equals(task.getTaskId()))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, TaskStatus> statusMap(List<Task> tasks) {
        Map<String, TaskStatus> result = new LinkedHashMap<>();
        for (Task task : tasks) {
            result.put(task.getTaskId(), task.getStatus());
        }
        return result;
    }

    private static Task submittedTask(String sessionId, String taskId, String taskType,
                                      int priority, String contextId) {
        Task task = new Task(sessionId, taskId, taskType);
        task.setPriority(priority);
        task.setStatus(TaskStatus.SUBMITTED);
        task.setContextId(contextId);
        return task;
    }

    private static List<String> collectText(Iterator<Object> iterator) {
        List<String> output = new ArrayList<>();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof ControllerOutputChunk chunk && chunk.getControllerPayload() != null) {
                for (DataFrame frame : chunk.getControllerPayload().getData()) {
                    if (frame instanceof DataFrame.TextDataFrame textDataFrame) {
                        output.add(textDataFrame.text());
                    }
                }
            }
        }
        return output;
    }

    private static Object nextWithin(Iterator<Object> iterator, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (iterator.hasNext()) {
                return iterator.next();
            }
        }
        throw new AssertionError("stream did not produce an item within " + timeout);
    }

    private static List<Object> drainIterator(Iterator<Object> iterator, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<Object> items = new ArrayList<>();
        while (System.nanoTime() < deadline && iterator.hasNext()) {
            items.add(iterator.next());
        }
        return items;
    }

    private static final class Harness {
        private final Controller controller;
        private final LiveSession session;
        private boolean stopped;

        private Harness(Controller controller, LiveSession session) {
            this.controller = controller;
            this.session = session;
        }

        private List<String> collectStreamOutput(String query) {
            return collectText(controller.stream(input(query), session, List.of()));
        }

        private void stop() {
            if (stopped) {
                return;
            }
            stopped = true;
            controller.stop();
        }
    }

    private static final class LiveSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = Collections.synchronizedMap(new LinkedHashMap<>());
        private final List<Object> stream = new ArrayList<>();
        private int nextReadIndex;

        private LiveSession(String sessionId) {
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
            synchronized (state) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() == null) {
                        state.remove(entry.getKey());
                    } else {
                        state.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        @Override
        public void writeStream(Object data) {
            synchronized (stream) {
                stream.add(data);
                stream.notifyAll();
            }
        }

        @Override
        public Iterator<Object> streamIterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    long deadline = System.currentTimeMillis() + 5000L;
                    synchronized (stream) {
                        while (nextReadIndex >= stream.size()) {
                            long remaining = deadline - System.currentTimeMillis();
                            if (remaining <= 0L) {
                                return false;
                            }
                            try {
                                stream.wait(remaining);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return false;
                            }
                        }
                        return true;
                    }
                }

                @Override
                public Object next() {
                    synchronized (stream) {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return stream.get(nextReadIndex++);
                    }
                }
            };
        }
    }

    private abstract static class BaseTestExecutor extends TaskExecutor {
        BaseTestExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        ControllerOutputChunk processing(int index, String text) {
            return chunk(index, ControllerOutputPayload.TASK_PROCESSING, text, false);
        }

        ControllerOutputChunk completion(int index, String text) {
            return chunk(index, EventType.TASK_COMPLETION.getValue(), text, true);
        }

        ControllerOutputChunk interaction(int index, String text) {
            return chunk(index, EventType.TASK_INTERACTION.getValue(), text, false);
        }

        private ControllerOutputChunk chunk(int index, String type, String text, boolean lastChunk) {
            return new ControllerOutputChunk(
                    index,
                    new ControllerOutputPayload(type, List.of(new DataFrame.TextDataFrame(text))),
                    lastChunk
            );
        }
    }

    private static class CancellableTaskExecutor extends BaseTestExecutor {
        private static final int QUICK_COMPLETION_ITERATIONS = 2;
        private static final int CONTROL_TARGET_ITERATIONS = 30;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean pauseRequested = new AtomicBoolean(false);

        CancellableTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            int iterations = taskId.contains("task_1") || taskId.endsWith("_1")
                    ? QUICK_COMPLETION_ITERATIONS
                    : CONTROL_TARGET_ITERATIONS;
            return new TimedTaskIterator(taskId, iterations, cancelled, pauseRequested, this);
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(true, "");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            pauseRequested.set(true);
            return true;
        }

        @Override
        public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
            return new CancelCheckResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            cancelled.set(true);
            return true;
        }
    }

    private static final class NonCancellableTaskExecutor extends BaseTestExecutor {
        NonCancellableTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return new TimedTaskIterator(taskId, 6, new AtomicBoolean(false), new AtomicBoolean(false), this);
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(false, "This task cannot be paused");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            throw new IllegalStateException("pause() should not be called when canPause() returns false");
        }

        @Override
        public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
            return new CancelCheckResult(false, "This task cannot be cancelled");
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            throw new IllegalStateException("cancel() should not be called when canCancel() returns false");
        }
    }

    private static final class PauseExceptionTaskExecutor extends CancellableTaskExecutor {
        PauseExceptionTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            throw new IllegalStateException("Pause failed for task " + taskId);
        }
    }

    private static final class CancelExceptionTaskExecutor extends CancellableTaskExecutor {
        CancelExceptionTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            throw new IllegalStateException("Cancel failed for task " + taskId);
        }
    }

    private static final class InteractionTaskExecutor extends BaseTestExecutor {
        InteractionTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            Task task = taskManager.getTask(TaskFilter.byTaskId(taskId)).get(0);
            task.setInputRequiredFields(Map.of("id", "questioner"));
            taskManager.updateTask(task);
            return List.of(
                    processing(0, "Task " + taskId + " started"),
                    interaction(1, "Task " + taskId + " needs user input")
            ).iterator();
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

    private static final class TimedTaskIterator implements Iterator<ControllerOutputChunk> {
        private static final long STEP_SLEEP_MS = 20L;

        private final String taskId;
        private final int iterations;
        private final AtomicBoolean cancelled;
        private final AtomicBoolean pauseRequested;
        private final BaseTestExecutor executor;
        private int step;
        private ControllerOutputChunk nextChunk;
        private boolean finished;

        private TimedTaskIterator(String taskId, int iterations, AtomicBoolean cancelled,
                                  AtomicBoolean pauseRequested, BaseTestExecutor executor) {
            this.taskId = taskId;
            this.iterations = iterations;
            this.cancelled = cancelled;
            this.pauseRequested = pauseRequested;
            this.executor = executor;
        }

        @Override
        public boolean hasNext() {
            if (nextChunk != null) {
                return true;
            }
            if (finished || cancelled.get() || pauseRequested.get() || Thread.currentThread().isInterrupted()) {
                finished = true;
                return false;
            }
            if (step == 0) {
                nextChunk = executor.processing(0, "Task " + taskId + " started");
                step++;
                return true;
            }
            if (step <= iterations) {
                if (!sleepStep()) {
                    finished = true;
                    return false;
                }
                nextChunk = executor.processing(step, "Task " + taskId + " progress " + step + "/" + iterations);
                step++;
                return true;
            }
            nextChunk = executor.completion(iterations + 1, "Task " + taskId + " completed");
            finished = true;
            return true;
        }

        @Override
        public ControllerOutputChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ControllerOutputChunk chunk = nextChunk;
            nextChunk = null;
            return chunk;
        }

        private boolean sleepStep() {
            try {
                Thread.sleep(STEP_SLEEP_MS);
                return !(cancelled.get() || pauseRequested.get() || Thread.currentThread().isInterrupted());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static class SimpleEventHandler extends EventHandler {
        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            taskManager.addTask(submittedTask(inputs.getSession().getSessionId(),
                    "test_task_1", "cancellable", 1, "test_context_1"));
            return Map.of("status", "success", "tasks_created", 1);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            return Map.of("status", "success", "tasks_created", 1);
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class DynamicTaskEventHandler extends EventHandler {
        private int taskCounter;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            taskCounter++;
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String taskId = "test_task_" + taskCounter + "_" + uniqueId;
            taskManager.addTask(submittedTask(inputs.getSession().getSessionId(),
                    taskId, "cancellable", 1, "test_context_" + uniqueId));
            return Map.of("status", "success", "tasks_created", 1);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class PauseInHandlerEventHandler extends EventHandler {
        private boolean firstTaskCompleted;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "pause_test_task_1", "cancellable", 1, "pause_test_context_1"),
                    submittedTask(sessionId, "pause_test_task_2", "cancellable", 1, "pause_test_context_2"),
                    submittedTask(sessionId, "pause_test_task_3", "cancellable", 1, "pause_test_context_3")
            ));
            return Map.of("status", "success", "tasks_created", 3);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            String completedTaskId = ((TaskCompletionEvent) inputs.getEvent()).getTask().getTaskId();
            if ("pause_test_task_1".equals(completedTaskId) && !firstTaskCompleted) {
                firstTaskCompleted = true;
                boolean success = taskScheduler.pauseTask("pause_test_task_2");
                return Map.of("status", "success", "paused", success);
            }
            if ("pause_test_task_3".equals(completedTaskId)) {
                taskScheduler.cancelTask("pause_test_task_2");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class PauseNonPausableEventHandler extends EventHandler {
        private boolean firstTaskCompleted;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "pausable_task", "cancellable", 1, "pausable_context"),
                    submittedTask(sessionId, "non_pausable_task", "non_cancellable", 1, "non_pausable_context")
            ));
            return Map.of("status", "success", "tasks_created", 2);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                return Map.of("status", "success", "paused", taskScheduler.pauseTask("non_pausable_task"));
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class PauseThenCancelEventHandler extends EventHandler {
        private int completedCount;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            List<Task> tasks = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                tasks.add(submittedTask(sessionId, "multi_op_task_" + i,
                        "cancellable", 1, "multi_op_context_" + i));
            }
            taskManager.addTask(tasks);
            return Map.of("status", "success", "tasks_created", 3);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            completedCount++;
            if (completedCount == 1) {
                taskScheduler.pauseTask("multi_op_task_2");
            } else if (completedCount == 2) {
                taskScheduler.cancelTask("multi_op_task_2");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class CancelInHandlerEventHandler extends EventHandler {
        private boolean firstTaskCompleted;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "cancel_test_task_1", "cancellable", 1, "cancel_test_context_1"),
                    submittedTask(sessionId, "cancel_test_task_2", "cancellable", 1, "cancel_test_context_2")
            ));
            return Map.of("status", "success", "tasks_created", 2);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                return Map.of("status", "success", "cancelled", taskScheduler.cancelTask("cancel_test_task_2"));
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class CancelNonCancellableEventHandler extends EventHandler {
        private boolean firstTaskCompleted;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "cancellable_task", "cancellable", 1, "cancellable_context"),
                    submittedTask(sessionId, "non_cancellable_task", "non_cancellable", 1, "non_cancellable_context")
            ));
            return Map.of("status", "success", "tasks_created", 2);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                return Map.of("status", "success", "cancelled", taskScheduler.cancelTask("non_cancellable_task"));
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class InteractionEventHandler extends EventHandler {
        private boolean interactionHandled;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            taskManager.addTask(submittedTask(inputs.getSession().getSessionId(),
                    "interaction_task_1", "interaction", 1, "interaction_context_1"));
            return Map.of("status", "success", "tasks_created", 1);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            interactionHandled = true;
            assertThat(((TaskInteractionEvent) inputs.getEvent()).getTask().getTaskId()).isEqualTo("interaction_task_1");
            return Map.of("status", "success", "interaction_handled", true);
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class PauseExceptionEventHandler extends EventHandler {
        private boolean firstTaskCompleted;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "pause_exception_task_1", "cancellable", 1,
                            "pause_exception_context_1"),
                    submittedTask(sessionId, "pause_exception_task_2", "pause_exception", 1,
                            "pause_exception_context_2")
            ));
            return Map.of("status", "success", "tasks_created", 2);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                taskScheduler.pauseTask("pause_exception_task_2");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class CancelExceptionEventHandler extends EventHandler {
        private boolean firstTaskCompleted;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "cancel_exception_task_1", "cancellable", 1,
                            "cancel_exception_context_1"),
                    submittedTask(sessionId, "cancel_exception_task_2", "cancel_exception", 1,
                            "cancel_exception_context_2")
            ));
            return Map.of("status", "success", "tasks_created", 2);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            if (!firstTaskCompleted) {
                firstTaskCompleted = true;
                taskScheduler.cancelTask("cancel_exception_task_2");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class StatePersistenceEventHandler extends EventHandler {
        private int roundNumber;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            roundNumber++;
            if (roundNumber == 1) {
                String sessionId = inputs.getSession().getSessionId();
                taskManager.addTask(List.of(
                        submittedTask(sessionId, "persist_task_1", "cancellable", 1, "persist_context_1"),
                        submittedTask(sessionId, "persist_task_2", "cancellable", 1, "persist_context_2")
                ));
                return Map.of("status", "success", "round", 1, "tasks_created", 2);
            }
            return Map.of(
                    "status", "success",
                    "round", 2,
                    "found_persisted_task", !taskManager.getTask(TaskFilter.byTaskId("persist_task_2")).isEmpty()
            );
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            String completedTaskId = ((TaskCompletionEvent) inputs.getEvent()).getTask().getTaskId();
            if ("persist_task_1".equals(completedTaskId)) {
                taskScheduler.pauseTask("persist_task_2");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class MultiTaskStatePersistenceEventHandler extends EventHandler {
        private int roundNumber;

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            roundNumber++;
            if (roundNumber == 1) {
                String sessionId = inputs.getSession().getSessionId();
                taskManager.addTask(List.of(
                        submittedTask(sessionId, "multi_task_1", "cancellable", 1, "multi_context_1"),
                        submittedTask(sessionId, "multi_task_2", "cancellable", 2, "multi_context_2"),
                        submittedTask(sessionId, "multi_task_3", "cancellable", 3, "multi_context_3")
                ));
                return Map.of("status", "success", "round", 1, "tasks_created", 3);
            }
            return Map.of("status", "success", "round", 2,
                    "restored_count", taskManager.getTask(null).size());
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            String completedTaskId = ((TaskCompletionEvent) inputs.getEvent()).getTask().getTaskId();
            if ("multi_task_1".equals(completedTaskId)) {
                taskScheduler.pauseTask("multi_task_2");
                taskScheduler.cancelTask("multi_task_3");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            TaskFailedEvent failedEvent = (TaskFailedEvent) inputs.getEvent();
            return Map.of("status", "failed", "task_id", failedEvent.getTask().getTaskId());
        }
    }
}
