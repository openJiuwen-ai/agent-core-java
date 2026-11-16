/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

/**
 * Focused parity tests for task scheduling and executor registration.
 *
 * <p>Mirrors Python's {@code TaskScheduler}, {@code TaskExecutor},
 * {@code TaskExecutorDependencies}, and {@code TaskExecutorRegistry} in
 * {@code openjiuwen/core/controller/modules/task_scheduler.py}.</p>
 */
class TaskSchedulerTest {

    @Test
    void dependenciesAndRegistryMirrorPythonExecutorLookup() {
        ControllerConfig config = new ControllerConfig();
        ContextEngine contextEngine = new ContextEngine();
        AbilityManager abilityManager = new AbilityManager();
        TaskManager taskManager = new TaskManager(config);
        EventQueue eventQueue = new EventQueue(config);
        TaskExecutorDependencies dependencies = new TaskExecutorDependencies(
                config,
                abilityManager,
                contextEngine,
                taskManager,
                eventQueue
        );
        TaskExecutorRegistry registry = new TaskExecutorRegistry();

        registry.addTaskExecutor("test_task", TestTaskExecutor::new);
        TaskExecutor executor = registry.getTaskExecutor("test_task", dependencies);
        registry.removeTaskExecutor("test_task");

        assertThat(dependencies.getConfig()).isSameAs(config);
        assertThat(dependencies.getAbilityManager()).isSameAs(abilityManager);
        assertThat(dependencies.getContextEngine()).isSameAs(contextEngine);
        assertThat(dependencies.getTaskManager()).isSameAs(taskManager);
        assertThat(dependencies.getEventQueue()).isSameAs(eventQueue);
        assertThat(executor).isInstanceOf(TestTaskExecutor.class);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> registry.getTaskExecutor("test_task", dependencies));
    }

    @Test
    void submittedCallbackWakesSchedulerAndPublishesMergedMetadata() throws Exception {
        ControllerConfig config = new ControllerConfig();
        config.setScheduleInterval(5.0D);
        TaskManager taskManager = new TaskManager(config);
        EventQueue eventQueue = new EventQueue(config);
        BaseCard card = new BaseCard("agent-1", "agent", "test agent");
        TaskScheduler scheduler = new TaskScheduler(
                config,
                taskManager,
                new ContextEngine(),
                new AbilityManager(),
                eventQueue,
                card
        );
        FakeSession session = new FakeSession("session-1");
        RecordingEventHandler eventHandler = new RecordingEventHandler();

        Map<String, Object> payloadMetadata = new LinkedHashMap<>();
        payloadMetadata.put("payload_key", "payload-value");
        payloadMetadata.put("shared", "payload-value");
        ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                0,
                new ControllerOutputPayload(
                        EventType.TASK_COMPLETION.getValue(),
                        List.of(new DataFrame.TextDataFrame("done")),
                        payloadMetadata
                )
        );
        scheduler.getTaskExecutorRegistry().addTaskExecutor(
                "test_task",
                dependencies -> new TestTaskExecutor(dependencies, List.of(completionChunk))
        );
        scheduler.getSessions().put(session.getSessionId(), session);
        eventQueue.setEventHandler(eventHandler);
        eventQueue.subscribe(card.getId(), session.getSessionId());

        try {
            scheduler.start();
            Thread.sleep(250L);

            Task task = task(session.getSessionId(), "task-1", TaskStatus.SUBMITTED);
            Map<String, Object> taskMetadata = new LinkedHashMap<>();
            taskMetadata.put("task_key", "task-value");
            taskMetadata.put("shared", "task-value");
            task.setMetadata(taskMetadata);
            taskManager.addTask(task);

            waitUntil(() -> taskManager.getTask(TaskFilter.byTaskId("task-1")).get(0).getStatus()
                    == TaskStatus.COMPLETED);
            waitUntil(() -> session.streamSnapshot().stream().anyMatch(TaskSchedulerTest::isAllTasksProcessed));
        } finally {
            scheduler.stop();
            eventQueue.stop();
        }

        assertThat(session.streamSnapshot()).anySatisfy(item -> {
            assertThat(item).isInstanceOf(ControllerOutputChunk.class);
            ControllerOutputChunk chunk = (ControllerOutputChunk) item;
            assertThat(chunk.getControllerPayload()).isNotNull();
        });
        assertThat(eventHandler.completionEvent.get()).isInstanceOf(TaskCompletionEvent.class);
        assertThat(eventHandler.completionEvent.get().getMetadata())
                .containsEntry("payload_key", "payload-value")
                .containsEntry("task_key", "task-value")
                .containsEntry("shared", "task-value");
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"TASK_COMPLETION", "TASK_INTERACTION", "TASK_FAILED"})
    void completionSignalWaitsForTaskResultCallback(EventType eventType) throws Exception {
        BlockingResultHandler handler = new BlockingResultHandler();
        try (SchedulerFixture fixture = new SchedulerFixture(handler)) {
            fixture.registerResultExecutor(eventType);
            fixture.scheduler.start();
            fixture.taskManager.addTask(task(fixture.session.getSessionId(), "task-1", TaskStatus.SUBMITTED));
            assertThat(handler.started.await(3, TimeUnit.SECONDS)).isTrue();
            fixture.scheduler.ensureSessionCompletionSignal(fixture.session.getSessionId());
            assertThat(fixture.session.streamSnapshot()).noneMatch(TaskSchedulerTest::isAllTasksProcessed);
            handler.release.complete(null);
            waitUntil(() -> fixture.session.streamSnapshot().stream().anyMatch(TaskSchedulerTest::isAllTasksProcessed));
            List<Object> output = fixture.session.streamSnapshot();
            assertThat(output).contains("callback-finished");
            assertThat(output.indexOf("callback-finished")).isLessThan(output.size() - 1);
            assertThat(isAllTasksProcessed(output.get(output.size() - 1))).isTrue();
        }
    }

    @Test
    void cancelTaskHandlesSubmittedAndTerminalTasksLikePython() {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        TaskScheduler scheduler = scheduler(config, taskManager);
        FakeSession session = new FakeSession("session-1");
        scheduler.getSessions().put(session.getSessionId(), session);
        taskManager.addTask(task(session.getSessionId(), "task-1", TaskStatus.SUBMITTED));

        boolean firstCancel = scheduler.cancelTask("task-1");
        boolean secondCancel = scheduler.cancelTask("task-1");

        assertThat(firstCancel).isTrue();
        assertThat(secondCancel).isFalse();
        assertThat(taskManager.getTask(TaskFilter.byTaskId("task-1")).get(0).getStatus())
                .isEqualTo(TaskStatus.CANCELED);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CANCELED", "PAUSED"})
    void completionSignalDoesNotWaitForStoppedWorker(TaskStatus stoppedStatus) throws Exception {
        try (SchedulerFixture fixture = new SchedulerFixture(new RecordingEventHandler())) {
            CountDownLatch stopRequested = new CountDownLatch(1);
            AtomicReference<Boolean> stopped = new AtomicReference<>();
            fixture.eventQueue.setEventHandler(new RecordingEventHandler() {
                @Override
                public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
                    stopped.set(stoppedStatus == TaskStatus.CANCELED
                            ? fixture.scheduler.cancelTask("worker") : fixture.scheduler.pauseTask("worker"));
                    stopRequested.countDown();
                    return Map.of();
                }
            });
            fixture.registerResultExecutor(EventType.TASK_COMPLETION);
            BlockedTaskExecutor worker = fixture.registerBlockedExecutor();
            try {
                fixture.scheduler.start();
                Task task = new Task(fixture.session.getSessionId(), "worker", "blocked");
                task.setStatus(TaskStatus.SUBMITTED);
                fixture.taskManager.addTask(task);
                assertThat(worker.started.await(3, TimeUnit.SECONDS)).isTrue();
                fixture.taskManager.addTask(task(fixture.session.getSessionId(), "finishing", TaskStatus.SUBMITTED));
                assertThat(stopRequested.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(stopped.get()).isTrue();
                waitUntil(() -> fixture.session.streamSnapshot().stream()
                        .anyMatch(TaskSchedulerTest::isAllTasksProcessed));
                assertThat(worker.result).isNotDone();
                assertThat(fixture.taskManager.getTask(TaskFilter.byTaskId("worker")).get(0).getStatus())
                        .isEqualTo(stoppedStatus);
            } finally {
                worker.result.complete(null);
            }
        }
    }

    @Test
    void completionSignalHonorsSuppressCompletionSignalConfig() {
        ControllerConfig config = new ControllerConfig();
        config.setSuppressCompletionSignal(true);
        TaskManager taskManager = new TaskManager(config);
        TaskScheduler scheduler = scheduler(config, taskManager);
        FakeSession session = new FakeSession("session-1");
        scheduler.getSessions().put(session.getSessionId(), session);
        taskManager.addTask(task(session.getSessionId(), "task-1", TaskStatus.COMPLETED));

        scheduler.ensureSessionCompletionSignal(session.getSessionId());
        config.setSuppressCompletionSignal(false);
        scheduler.ensureSessionCompletionSignal(session.getSessionId());

        assertThat(session.streamSnapshot()).hasSize(1);
        assertThat(isAllTasksProcessed(session.streamSnapshot().get(0))).isTrue();
    }

    private static final class SchedulerFixture implements AutoCloseable {
        private final ControllerConfig config = new ControllerConfig();
        private final TaskManager taskManager = new TaskManager(config);
        private final EventQueue eventQueue = new EventQueue(config);
        private final BaseCard card = new BaseCard("agent-1", "agent", "test agent");
        private final TaskScheduler scheduler = new TaskScheduler(config, taskManager, new ContextEngine(),
                new AbilityManager(), eventQueue, card);
        private final FakeSession session = new FakeSession("session-1");
        private final RecordingEventHandler handler;

        private SchedulerFixture(RecordingEventHandler handler) {
            this.handler = handler;
            eventQueue.setEventHandler(handler);
            eventQueue.subscribe(card.getId(), session.getSessionId());
            scheduler.getSessions().put(session.getSessionId(), session);
        }

        private void registerResultExecutor(EventType eventType) {
            scheduler.getTaskExecutorRegistry().addTaskExecutor("test_task", dependencies ->
                    new TestTaskExecutor(dependencies, List.of(new ControllerOutputChunk(0,
                            new ControllerOutputPayload(eventType.getValue(), List.of())))));
        }

        private BlockedTaskExecutor registerBlockedExecutor() {
            BlockedTaskExecutor executor = new BlockedTaskExecutor(new TaskExecutorDependencies(
                    config, new AbilityManager(), new ContextEngine(), taskManager, eventQueue));
            scheduler.getTaskExecutorRegistry().addTaskExecutor("blocked", dependencies -> executor);
            return executor;
        }

        @Override
        public void close() {
            if (handler instanceof BlockingResultHandler blockingHandler) {
                blockingHandler.release.complete(null);
            }
            scheduler.stop();
            eventQueue.stop();
        }
    }

    private static final class BlockingResultHandler extends RecordingEventHandler {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CompletableFuture<Void> release = new CompletableFuture<>();

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            return handleResult(inputs);
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return handleResult(inputs);
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return handleResult(inputs);
        }

        private Map<String, Object> handleResult(EventHandlerInput inputs) {
            started.countDown();
            release.join();
            inputs.getSession().writeStream("callback-finished");
            return Map.of();
        }
    }

    private static final class BlockedTaskExecutor extends TestTaskExecutor {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CompletableFuture<Void> result = new CompletableFuture<>();

        private BlockedTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            started.countDown();
            // Asynchronous executors using join() do not stop on interrupt.
            result.join();
            return Collections.emptyIterator();
        }
    }

    private static TaskScheduler scheduler(ControllerConfig config, TaskManager taskManager) {
        return new TaskScheduler(
                config,
                taskManager,
                new ContextEngine(),
                new AbilityManager(),
                new EventQueue(config),
                new BaseCard("agent-1", "agent", "test agent")
        );
    }

    private static Task task(String sessionId, String taskId, TaskStatus status) {
        Task task = new Task(sessionId, taskId, "test_task");
        task.setStatus(status);
        return task;
    }

    private static boolean isAllTasksProcessed(Object item) {
        if (!(item instanceof ControllerOutputChunk chunk) || chunk.getControllerPayload() == null) {
            return false;
        }
        return ControllerOutputPayload.ALL_TASKS_PROCESSED.equals(chunk.getControllerPayload().getType());
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
     * Test executor used by scheduler parity tests.
     *
     * <p>Mirrors Python's {@code TaskExecutor} extension point in
     * {@code openjiuwen/core/controller/modules/task_scheduler.py}.</p>
     */
    private static class TestTaskExecutor extends TaskExecutor {
        private final List<ControllerOutputChunk> chunks;

        private TestTaskExecutor(TaskExecutorDependencies dependencies) {
            this(dependencies, List.of());
        }

        private TestTaskExecutor(TaskExecutorDependencies dependencies, List<ControllerOutputChunk> chunks) {
            super(dependencies);
            this.chunks = chunks;
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return chunks.iterator();
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
     * Test handler that records scheduler-published task events.
     *
     * <p>Mirrors Python's event handler callbacks consumed by
     * {@code openjiuwen/core/controller/modules/task_scheduler.py}.</p>
     */
    private static class RecordingEventHandler extends EventHandler {
        private final AtomicReference<Event> completionEvent = new AtomicReference<>();

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
            completionEvent.set(inputs.getEvent());
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of();
        }
    }

    /**
     * Test session dependency used by the scheduler.
     *
     * <p>Mirrors Python's {@code Session} dependency in
     * {@code openjiuwen/core/controller/modules/task_scheduler.py}.</p>
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
