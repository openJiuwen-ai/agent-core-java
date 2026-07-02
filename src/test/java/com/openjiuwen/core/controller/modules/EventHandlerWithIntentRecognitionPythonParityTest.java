/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code TestEventHandlerWithIntentRecognition} in
 * {@code tests/unit_tests/core/controller/test_event_handler_with_intent_recognition.py}.
 */
class EventHandlerWithIntentRecognitionPythonParityTest {

    @Test
    void handleInputCreateTaskCreatesSubmittedTask() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleCreateIntent()));

        harness.handler.handleInput(harness.input());

        Task task = harness.findTask("task1");
        assertThat(task.getTaskId()).isEqualTo("task1");
        assertThat(task.getDescription()).isEqualTo("Test task description");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
    }

    @Test
    void handleInputPauseTaskCallsScheduler() {
        TestHarness harness = TestHarness.withIntents(List.of(samplePauseIntent()));

        harness.handler.handleInput(harness.input());

        assertThat(harness.scheduler.pauseTaskIds).containsExactly("task1");
    }

    @Test
    void handleInputResumeTaskUpdatesPausedTask() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleResumeIntent()));
        harness.taskManager.addTask(task("task1", "Test task", TaskStatus.PAUSED));

        harness.handler.handleInput(harness.input());

        assertThat(harness.findTask("task1").getStatus()).isEqualTo(TaskStatus.SUBMITTED);
    }

    @Test
    void handleInputResumeTaskNotPausedDoesNotUpdate() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleResumeIntent()));
        harness.taskManager.addTask(task("task1", "Test task", TaskStatus.WORKING));

        harness.handler.handleInput(harness.input());

        assertThat(harness.findTask("task1").getStatus()).isEqualTo(TaskStatus.WORKING);
    }

    @Test
    void handleInputContinueTaskCarriesPreviousEvents() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleContinueIntent()));
        Task dependent = task("task1", "Dependent task", TaskStatus.COMPLETED);
        dependent.setContextId("test_session_id_task1");
        dependent.setInputs(List.of(harness.sampleInputEvent));
        harness.taskManager.addTask(dependent);

        harness.handler.handleInput(harness.input());

        Task task = harness.findTask("task2");
        assertThat(task.getTaskId()).isEqualTo("task2");
        assertThat(task.getInputs()).contains(harness.sampleInputEvent);
        assertThat(task.getInputs()).hasSize(2);
        List<DataFrame> inputData = ((InputEvent) task.getInputs().get(1)).getInputData();
        assertThat(inputData.get(inputData.size() - 1))
                .isInstanceOf(DataFrame.JsonDataFrame.class);
    }

    @Test
    void handleInputSupplementTaskPausesAndUpdatesDescription() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleSupplementIntent()));
        harness.taskManager.addTask(task("task1", "Original task", TaskStatus.WORKING));

        harness.handler.handleInput(harness.input());

        Task task = harness.findTask("task1");
        assertThat(harness.scheduler.pauseTaskIds).containsExactly("task1");
        assertThat(task.getDescription()).contains("任务补充信息");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
    }

    @Test
    void handleInputCancelTaskCallsScheduler() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleCancelIntent()));

        harness.handler.handleInput(harness.input());

        assertThat(harness.scheduler.cancelTaskIds).containsExactly("task1");
    }

    @Test
    void handleInputModifyTaskCancelsAndUpdatesTask() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleModifyIntent()));
        harness.taskManager.addTask(task("task1", "Original task", TaskStatus.WORKING));

        harness.handler.handleInput(harness.input());

        Task task = harness.findTask("task1");
        assertThat(harness.scheduler.cancelTaskIds).containsExactly("task1");
        assertThat(task.getDescription()).isEqualTo("Modified task description");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
        assertThat(task.getInputs()).hasSize(1);
    }

    @Test
    void handleInputUnknownTaskWritesClarificationPrompt() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleUnknownIntent()));

        harness.handler.handleInput(harness.input());

        assertStreamEntry(harness, "clarification_prompt", "Could you please clarify?");
    }

    @Test
    void handleInputMultipleIntentsProcessesAllIntents() {
        TestHarness harness = TestHarness.withIntents(List.of(sampleCreateIntent(), samplePauseIntent()));

        harness.handler.handleInput(harness.input());

        assertThat(harness.findTask("task1").getStatus()).isEqualTo(TaskStatus.SUBMITTED);
        assertThat(harness.scheduler.pauseTaskIds).containsExactly("task1");
    }

    @Test
    void handleTaskInteractionWritesInteraction() {
        TestHarness harness = TestHarness.withIntents(List.of());
        List<DataFrame> interaction = List.of(new DataFrame.JsonDataFrame(
                Map.of("type", "input_required", "message", "Please provide input")));

        harness.handler.handleTaskInteraction(new EventHandlerInput(
                new TaskInteractionEvent(interaction, null),
                harness.session));

        assertStreamEntry(harness, "interaction", interaction);
    }

    @Test
    void handleTaskInteractionRejectsWrongEventType() {
        TestHarness harness = TestHarness.withIntents(List.of());

        assertThrows(RuntimeException.class, () -> harness.handler.handleTaskInteraction(harness.input()));
    }

    @Test
    void handleTaskCompletionWritesResult() {
        TestHarness harness = TestHarness.withIntents(List.of());
        List<DataFrame> result = List.of(new DataFrame.JsonDataFrame(
                Map.of("status", "completed", "output", "Task completed successfully")));

        harness.handler.handleTaskCompletion(new EventHandlerInput(
                new TaskCompletionEvent(result, null),
                harness.session));

        assertStreamEntry(harness, "result", result);
    }

    @Test
    void handleTaskCompletionRejectsWrongEventType() {
        TestHarness harness = TestHarness.withIntents(List.of());

        assertThrows(RuntimeException.class, () -> harness.handler.handleTaskCompletion(harness.input()));
    }

    @Test
    void handleTaskFailedWritesErrorMessage() {
        TestHarness harness = TestHarness.withIntents(List.of());

        harness.handler.handleTaskFailed(new EventHandlerInput(
                new TaskFailedEvent("Task execution failed", null),
                harness.session));

        assertStreamEntry(harness, "error_message", "Task execution failed");
    }

    @Test
    void handleTaskFailedRejectsWrongEventType() {
        TestHarness harness = TestHarness.withIntents(List.of());

        assertThrows(RuntimeException.class, () -> harness.handler.handleTaskFailed(harness.input()));
    }

    private static Intent sampleCreateIntent() {
        return new Intent(IntentType.CREATE_TASK, sampleInputEvent(), "task1",
                "Test task description", null, null, null, 1.0d, null);
    }

    private static Intent samplePauseIntent() {
        return new Intent(IntentType.PAUSE_TASK, sampleInputEvent(), "task1");
    }

    private static Intent sampleResumeIntent() {
        return new Intent(IntentType.RESUME_TASK, sampleInputEvent(), "task1");
    }

    private static Intent sampleContinueIntent() {
        return new Intent(IntentType.CONTINUE_TASK, sampleInputEvent(), "task2",
                "Continue task description", List.of("task1"), null, null, 1.0d, null);
    }

    private static Intent sampleSupplementIntent() {
        return new Intent(IntentType.SUPPLEMENT_TASK, sampleInputEvent(), "task1",
                null, null, "Mock supplementary_info.", null, 1.0d, null);
    }

    private static Intent sampleCancelIntent() {
        return new Intent(IntentType.CANCEL_TASK, sampleInputEvent(), "task1");
    }

    private static Intent sampleModifyIntent() {
        return new Intent(IntentType.MODIFY_TASK, sampleInputEvent(), "task1",
                "Modified task description", null, null, "Mock_modification_details", 1.0d, null);
    }

    private static Intent sampleUnknownIntent() {
        return new Intent(IntentType.UNKNOWN_TASK, sampleInputEvent(), "",
                null, null, null, null, 1.0d, "Could you please clarify?");
    }

    private static InputEvent sampleInputEvent() {
        return new InputEvent(new ArrayList<>(List.of(new DataFrame.TextDataFrame("Create a new task"))));
    }

    private static void assertStreamEntry(TestHarness harness, String key, Object value) {
        assertThat(harness.session.streamWrites).hasSize(1);
        Object write = harness.session.streamWrites.get(0);
        assertThat(write).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) write).get(key)).isEqualTo(value);
    }

    private static Task task(String taskId, String description, TaskStatus status) {
        Task task = new Task("test_session_id", taskId, "test_task");
        task.setDescription(description);
        task.setPriority(1);
        task.setStatus(status);
        task.setContextId("test_session_id_" + taskId);
        return task;
    }

    private static void setRecognizer(EventHandlerWithIntentRecognition handler, IntentRecognizer recognizer) {
        try {
            Field field = EventHandlerWithIntentRecognition.class.getDeclaredField("recognizer");
            field.setAccessible(true);
            field.set(handler, recognizer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class TestHarness {
        private final InputEvent sampleInputEvent = sampleInputEvent();
        private final TestSession session = new TestSession("test_session_id");
        private final TaskManager taskManager;
        private final FakeTaskScheduler scheduler;
        private final EventHandlerWithIntentRecognition handler;

        private TestHarness(List<Intent> intents) {
            ControllerConfig config = new ControllerConfig();
            config.setIntentLlmId("test_llm_id");
            config.setIntentConfidenceThreshold(0.8d);
            taskManager = new TaskManager(config);
            ContextEngine contextEngine = new ContextEngine();
            scheduler = new FakeTaskScheduler(config, taskManager, contextEngine);
            handler = new EventHandlerWithIntentRecognition((modelId, sessionRef) -> null);
            handler.setConfig(config);
            handler.setTaskManager(taskManager);
            handler.setTaskScheduler(scheduler);
            handler.setContextEngine(contextEngine);
            handler.setAbilityManager(new Object());
            setRecognizer(handler, new StubRecognizer(intents));
        }

        private static TestHarness withIntents(List<Intent> intents) {
            return new TestHarness(intents);
        }

        private EventHandlerInput input() {
            return new EventHandlerInput(sampleInputEvent, session);
        }

        private Task findTask(String taskId) {
            return taskManager.getTask(TaskFilter.byTaskId(taskId)).get(0);
        }
    }

    private static final class StubRecognizer extends IntentRecognizer {
        private final List<Intent> intents;

        private StubRecognizer(List<Intent> intents) {
            super(null, null, null, null, null);
            this.intents = intents;
        }

        @Override
        public List<Intent> recognize(Event event, AgentSessionApi session) {
            return intents;
        }
    }

    private static final class FakeTaskScheduler extends TaskScheduler {
        private final List<String> pauseTaskIds = new ArrayList<>();
        private final List<String> cancelTaskIds = new ArrayList<>();

        private FakeTaskScheduler(ControllerConfig config, TaskManager taskManager, ContextEngine contextEngine) {
            super(config, taskManager, contextEngine, new Object(), new EventQueue(config),
                    new BaseCard("agent-1", "agent", "test agent"));
        }

        @Override
        public boolean pauseTask(String taskId) {
            pauseTaskIds.add(taskId);
            return true;
        }

        @Override
        public boolean cancelTask(String taskId) {
            cancelTaskIds.add(taskId);
            return true;
        }
    }

    private static final class TestSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> streamWrites = new ArrayList<>();

        private TestSession(String sessionId) {
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
            streamWrites.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return Collections.emptyIterator();
        }
    }
}
