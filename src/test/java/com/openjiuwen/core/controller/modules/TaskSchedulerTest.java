/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
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
import com.openjiuwen.core.single_agent.AbilityManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final class TestTaskExecutor extends TaskExecutor {
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
    private static final class RecordingEventHandler extends EventHandler {
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
