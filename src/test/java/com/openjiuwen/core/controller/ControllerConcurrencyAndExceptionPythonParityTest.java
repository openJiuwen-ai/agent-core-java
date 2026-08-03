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
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Missing-test parity coverage for controller concurrency and exception handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.controller.test_controller_concurrency_and_exception} in
 * {@code tests/unit_tests/core/controller/test_controller_concurrency_and_exception.py}.</p>
 */
class ControllerConcurrencyAndExceptionPythonParityTest {

    @Test
    void concurrentSessionsIsolation() {
        Harness harness = harness(
                "test_concurrent_sessions",
                new ConcurrentSessionEventHandler(),
                Map.of("normal", builder(NormalTaskExecutor::new))
        );
        try {
            LiveSession session1 = new LiveSession("session_1");
            LiveSession session2 = new LiveSession("session_2");
            LiveSession session3 = new LiveSession("session_3");

            CompletableFuture<List<String>> future1 = streamAsync(harness, "request from session 1", session1);
            CompletableFuture<List<String>> future2 = streamAsync(harness, "request from session 2", session2);
            CompletableFuture<List<String>> future3 = streamAsync(harness, "request from session 3", session3);
            List<String> output1 = join(future1);
            List<String> output2 = join(future2);
            List<String> output3 = join(future3);

            assertSessionCompleted(output1, "session_1");
            assertSessionCompleted(output2, "session_2");
            assertSessionCompleted(output3, "session_3");
            assertThat(harness.controller.getTaskManager().getTask(null))
                    .filteredOn(task -> task.getStatus() == TaskStatus.COMPLETED)
                    .hasSizeGreaterThanOrEqualTo(3);
        } finally {
            harness.controller.stop();
        }
    }

    @Test
    void sessionTaskIsolation() {
        Harness normalHarness = harness(
                "test_session_1",
                new ConcurrentSessionEventHandler(),
                Map.of("normal", builder(NormalTaskExecutor::new))
        );
        Harness failingHarness = harness(
                "test_session_2",
                new FailingTaskEventHandler(),
                Map.of(
                        "normal", builder(NormalTaskExecutor::new),
                        "failing", builder(FailingTaskExecutor::new)
                )
        );
        try {
            CompletableFuture<List<String>> normal = streamAsync(
                    normalHarness,
                    "normal request",
                    new LiveSession("normal_session")
            );
            CompletableFuture<List<String>> failing = streamAsync(
                    failingHarness,
                    "failing request",
                    new LiveSession("failing_session")
            );

            assertThat(join(normal)).anySatisfy(text -> assertThat(text).contains("completed"));
            assertThat(join(failing)).isNotNull();
            assertThat(failingHarness.controller.getTaskManager().getTask(null))
                    .filteredOn(task -> task.getStatus() == TaskStatus.FAILED)
                    .isNotEmpty();
            assertThat(normalHarness.controller.getTaskManager().getTask(null))
                    .filteredOn(task -> task.getStatus() == TaskStatus.FAILED)
                    .isEmpty();
        } finally {
            normalHarness.controller.stop();
            failingHarness.controller.stop();
        }
    }

    @Test
    void eventRoutingToCorrectSession() {
        Harness harness = harness(
                "test_event_routing",
                new ConcurrentSessionEventHandler(),
                Map.of("normal", builder(NormalTaskExecutor::new))
        );
        try {
            List<CompletableFuture<List<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(streamAsync(harness, "request " + i, new LiveSession("session_" + i)));
            }

            for (int i = 0; i < futures.size(); i++) {
                List<String> output = join(futures.get(i));
                String expectedSessionId = "session_" + i;
                assertThat(output).isNotEmpty();
                assertThat(output).anySatisfy(text -> assertThat(text).contains(expectedSessionId));
            }
        } finally {
            harness.controller.stop();
        }
    }

    @Test
    void taskExecutionExceptionHandling() {
        Harness harness = harness(
                "test_task_exception",
                new FailingTaskEventHandler(),
                Map.of(
                        "normal", builder(NormalTaskExecutor::new),
                        "failing", builder(FailingTaskExecutor::new)
                )
        );
        try {
            List<String> output = harness.collectStreamOutput("test task exception", new LiveSession("test_exception"));

            Task failed = task(harness, "failing_task");
            Task normal = task(harness, "normal_task");

            assertThat(output).isNotNull();
            assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(failed.getErrorMessage()).contains("failed intentionally");
            assertThat(normal.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        } finally {
            harness.controller.stop();
        }
    }

    @Test
    void streamOutputExceptionIsolation() {
        Harness harness = harness(
                "test_stream_exception",
                new StreamExceptionTaskEventHandler(),
                Map.of(
                        "normal", builder(NormalTaskExecutor::new),
                        "stream_exception", builder(ExceptionInStreamTaskExecutor::new)
                )
        );
        try {
            harness.collectStreamOutput("test stream exception", new LiveSession("test_stream_exception"));

            assertThat(task(harness, "stream_fail_task").getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(task(harness, "normal_task_2").getStatus()).isEqualTo(TaskStatus.COMPLETED);
        } finally {
            harness.controller.stop();
        }
    }

    @Test
    void eventHandlerExceptionIsolation() {
        Harness harness = harness(
                "test_handler_exception",
                new ExceptionInEventHandlerEventHandler(),
                Map.of("normal", builder(NormalTaskExecutor::new))
        );
        try {
            List<String> output = harness.collectStreamOutput(
                    "test handler exception",
                    new LiveSession("test_handler_exception")
            );

            assertThat(output).isNotEmpty();
            assertThat(harness.controller.getTaskManager().getTask(null))
                    .filteredOn(task -> task.getStatus() == TaskStatus.COMPLETED)
                    .hasSizeGreaterThanOrEqualTo(2);
        } finally {
            harness.controller.stop();
        }
    }

    @Test
    void exceptionInConcurrentSessions() {
        Harness normalHarness = harness(
                "normal_agent",
                new ConcurrentSessionEventHandler(),
                Map.of("normal", builder(NormalTaskExecutor::new))
        );
        Harness failingHarness = harness(
                "failing_agent",
                new FailingTaskEventHandler(),
                Map.of(
                        "normal", builder(NormalTaskExecutor::new),
                        "failing", builder(FailingTaskExecutor::new)
                )
        );
        try {
            CompletableFuture<List<String>> normal1 = streamAsync(
                    normalHarness,
                    "test",
                    new LiveSession("normal_1")
            );
            CompletableFuture<List<String>> failing = streamAsync(
                    failingHarness,
                    "test",
                    new LiveSession("failing")
            );
            CompletableFuture<List<String>> normal2 = streamAsync(
                    normalHarness,
                    "test",
                    new LiveSession("normal_2")
            );

            assertThat(join(normal1)).isNotEmpty();
            assertThat(join(failing)).isNotEmpty();
            assertThat(join(normal2)).isNotEmpty();
        } finally {
            normalHarness.controller.stop();
            failingHarness.controller.stop();
        }
    }

    @Test
    void taskTimeout() throws Exception {
        Harness harness = harness(
                "test_timeout",
                new TimeoutTestEventHandler(),
                Map.of("slow", builder(dependencies -> new SlowTaskExecutor(dependencies, 1.0D)))
        );
        harness.controller.getTaskScheduler().getConfig().setTaskTimeout(0.2D);
        harness.controller.getTaskScheduler().getConfig().setScheduleInterval(0.1D);
        LiveSession session = new LiveSession("test_timeout_session");
        CompletableFuture<List<String>> stream = streamAsync(harness, "test timeout", session);
        try {
            waitUntil(() -> statusCount(harness, TaskStatus.FAILED) == 2, Duration.ofSeconds(3));

            assertThat(harness.controller.getTaskManager().getTask(TaskFilter.bySessionId(session.getSessionId())))
                    .isNotEmpty();
            assertThat(statusCount(harness, TaskStatus.COMPLETED)).isGreaterThanOrEqualTo(1);
            assertThat(statusCount(harness, TaskStatus.FAILED)).isEqualTo(2);
            join(stream);
        } finally {
            harness.controller.stop();
            stream.cancel(true);
        }
    }

    @Test
    void timeoutVsManualCancel() throws Exception {
        Harness harness = harness(
                "test_timeout_vs_cancel",
                new TimeoutTestEventHandler(),
                Map.of("slow", builder(dependencies -> new SlowTaskExecutor(dependencies, 2.0D)))
        );
        harness.controller.getTaskScheduler().getConfig().setTaskTimeout(5.0D);
        harness.controller.getTaskScheduler().getConfig().setScheduleInterval(0.1D);
        LiveSession session = new LiveSession("test_cancel_session");
        CompletableFuture<List<String>> stream = streamAsync(harness, "test manual cancel", session);
        try {
            waitUntil(() -> taskStatus(harness, "timeout_task_0") == TaskStatus.WORKING, Duration.ofSeconds(3));

            boolean success = harness.controller.getTaskScheduler().cancelTask("timeout_task_0");
            waitUntil(() -> taskStatus(harness, "timeout_task_0") == TaskStatus.CANCELED, Duration.ofSeconds(2));

            assertThat(success).isTrue();
            assertThat(taskStatus(harness, "timeout_task_0")).isEqualTo(TaskStatus.CANCELED);
        } finally {
            harness.controller.stop();
            stream.cancel(true);
        }
    }

    private static Harness harness(String agentId, EventHandler eventHandler,
                                   Map<String, Function<TaskExecutorDependencies, TaskExecutor>> executors) {
        ControllerConfig config = new ControllerConfig();
        config.setScheduleInterval(0.1D);
        config.setMaxConcurrentTasks(5);
        config.setEnableTaskPersistence(true);
        Controller controller = new Controller();
        controller.init(new BaseCard(agentId, "Test Agent " + agentId, "Test agent for controller parity"),
                config,
                new AbilityManager(),
                new ContextEngine());
        controller.setEventHandler(eventHandler);
        executors.forEach(controller::addTaskExecutor);
        return new Harness(controller);
    }

    private static Function<TaskExecutorDependencies, TaskExecutor> builder(
            Function<TaskExecutorDependencies, ? extends TaskExecutor> fn) {
        return dependencies -> fn.apply(dependencies);
    }

    private static CompletableFuture<List<String>> streamAsync(Harness harness, String query, LiveSession session) {
        return CompletableFuture.supplyAsync(() -> harness.collectStreamOutput(query, session));
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("stream did not complete", e);
        }
    }

    private static InputEvent input(String query) {
        return InputEvent.fromUserInput(Map.of("query", query));
    }

    private static void assertSessionCompleted(List<String> output, String sessionId) {
        assertThat(output).isNotEmpty();
        assertThat(output).anySatisfy(text -> assertThat(text).contains(sessionId));
        assertThat(output).anySatisfy(text -> assertThat(text).contains("completed"));
    }

    private static Task task(Harness harness, String taskId) {
        return harness.controller.getTaskManager().getTask(TaskFilter.byTaskId(taskId)).get(0);
    }

    private static TaskStatus taskStatus(Harness harness, String taskId) {
        List<Task> tasks = harness.controller.getTaskManager().getTask(TaskFilter.byTaskId(taskId));
        return tasks.isEmpty() ? null : tasks.get(0).getStatus();
    }

    private static long statusCount(Harness harness, TaskStatus status) {
        return harness.controller.getTaskManager().getTask(null)
                .stream()
                .filter(task -> task.getStatus() == status)
                .count();
    }

    private static void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        fail("condition was not met before timeout " + timeout);
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
                    } else if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame) {
                        output.add(jsonDataFrame.data().toString());
                    }
                }
            }
        }
        return output;
    }

    private record Harness(Controller controller) {
        private List<String> collectStreamOutput(String query, LiveSession session) {
            return collectText(controller.stream(input(query), session, List.of()));
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

        private ControllerOutputChunk chunk(int index, String type, String text, boolean lastChunk) {
            return new ControllerOutputChunk(
                    index,
                    new ControllerOutputPayload(type, List.of(new DataFrame.TextDataFrame(text))),
                    lastChunk
            );
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

    private static final class NormalTaskExecutor extends BaseTestExecutor {
        private NormalTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            List<ControllerOutputChunk> chunks = new ArrayList<>();
            chunks.add(processing(0, "Task " + taskId + " started in session " + session.getSessionId()));
            for (int i = 1; i <= 3; i++) {
                chunks.add(processing(i, "Task " + taskId + " progress " + i + "/3"));
            }
            chunks.add(completion(4, "Task " + taskId + " completed in session " + session.getSessionId()));
            return new ScriptedIterator(chunks, -1, null, 20L);
        }
    }

    private static final class FailingTaskExecutor extends BaseTestExecutor {
        private FailingTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return new ScriptedIterator(
                    List.of(processing(0, "Task " + taskId + " starting...")),
                    1,
                    "Task " + taskId + " failed intentionally",
                    20L
            );
        }
    }

    private static final class ExceptionInStreamTaskExecutor extends BaseTestExecutor {
        private ExceptionInStreamTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return new ScriptedIterator(
                    List.of(
                            processing(0, "Task " + taskId + " started"),
                            processing(1, "Task " + taskId + " progress 1/3")
                    ),
                    2,
                    "Task " + taskId + " stream failed",
                    20L
            );
        }
    }

    private static final class SlowTaskExecutor extends BaseTestExecutor {
        private final double sleepSeconds;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private SlowTaskExecutor(TaskExecutorDependencies dependencies, double sleepSeconds) {
            super(dependencies);
            this.sleepSeconds = sleepSeconds;
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return new Iterator<>() {
                private int index;
                private boolean done;

                @Override
                public boolean hasNext() {
                    if (done || cancelled.get()) {
                        return false;
                    }
                    if (index == 0) {
                        return true;
                    }
                    if (index == 1) {
                        double duration = "timeout_task_2".equals(taskId) ? 0.08D : sleepSeconds;
                        sleepInterruptibly(duration);
                        return !done && !cancelled.get();
                    }
                    return false;
                }

                @Override
                public ControllerOutputChunk next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    if (index == 0) {
                        index++;
                        return processing(0, "Slow task " + taskId + " started, will sleep for " + sleepSeconds + "s");
                    }
                    index++;
                    done = true;
                    return completion(1, "Slow task " + taskId + " completed");
                }

                private void sleepInterruptibly(double seconds) {
                    try {
                        Thread.sleep((long) (seconds * 1000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (!cancelled.get()) {
                            done = true;
                            throw new IllegalStateException("Task " + taskId + " interrupted", e);
                        }
                    }
                }
            };
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            cancelled.set(true);
            return true;
        }
    }

    private static final class ScriptedIterator implements Iterator<ControllerOutputChunk> {
        private final List<ControllerOutputChunk> chunks;
        private final int failAfterChunks;
        private final String failureMessage;
        private final long delayMillis;
        private int cursor;

        private ScriptedIterator(List<ControllerOutputChunk> chunks, int failAfterChunks,
                                 String failureMessage, long delayMillis) {
            this.chunks = chunks;
            this.failAfterChunks = failAfterChunks;
            this.failureMessage = failureMessage;
            this.delayMillis = delayMillis;
        }

        @Override
        public boolean hasNext() {
            if (failAfterChunks >= 0 && cursor >= failAfterChunks) {
                throw new IllegalStateException(failureMessage);
            }
            return cursor < chunks.size();
        }

        @Override
        public ControllerOutputChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            sleepQuietly(delayMillis);
            return chunks.get(cursor++);
        }
    }

    private static class ConcurrentSessionEventHandler extends EventHandler {
        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(submittedTask(
                    sessionId,
                    "task_" + sessionId,
                    "normal",
                    1,
                    "context_" + sessionId
            ));
            return Map.of("status", "success", "session_id", sessionId);
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

    private static final class ExceptionInEventHandlerEventHandler extends EventHandler {
        private final AtomicInteger handleCount = new AtomicInteger();

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            List<Task> tasks = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                tasks.add(submittedTask(
                        inputs.getSession().getSessionId(),
                        "task_" + i,
                        "normal",
                        i,
                        "context_" + i
                ));
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
            if (handleCount.incrementAndGet() == 1) {
                throw new IllegalStateException("Exception in handle_task_completion");
            }
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "failed");
        }
    }

    private static final class FailingTaskEventHandler extends EventHandler {
        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "failing_task", "failing", 1, "failing_context"),
                    submittedTask(sessionId, "normal_task", "normal", 2, "normal_context")
            ));
            return Map.of("status", "success", "tasks_created", 2);
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
            TaskFailedEvent event = (TaskFailedEvent) inputs.getEvent();
            return Map.of("status", "failed", "error", event.getErrorMessage());
        }
    }

    private static final class StreamExceptionTaskEventHandler extends EventHandler {
        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            taskManager.addTask(List.of(
                    submittedTask(sessionId, "stream_fail_task", "stream_exception", 1, "stream_fail_context"),
                    submittedTask(sessionId, "normal_task_2", "normal", 2, "normal_context_2")
            ));
            return Map.of("status", "success", "tasks_created", 2);
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
            TaskFailedEvent event = (TaskFailedEvent) inputs.getEvent();
            return Map.of("status", "failed", "error", event.getErrorMessage());
        }
    }

    private static final class TimeoutTestEventHandler extends EventHandler {
        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            String sessionId = inputs.getSession().getSessionId();
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tasks.add(submittedTask(
                        sessionId,
                        "timeout_task_" + i,
                        "slow",
                        1,
                        "timeout_context_" + i
                ));
            }
            taskManager.addTask(tasks);
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            return Map.of("status", "success");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of("status", "success");
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
