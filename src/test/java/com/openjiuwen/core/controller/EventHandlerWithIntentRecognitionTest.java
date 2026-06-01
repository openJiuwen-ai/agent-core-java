/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.controller;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventHandlerWithIntentRecognition;
import com.openjiuwen.core.controller.modules.IntentRecognizer;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventHandlerWithIntentRecognition.
 * Translated from Python tests/unit_tests/core/controller/test_event_handler_with_intent_recognition.py
 */
class EventHandlerWithIntentRecognitionTest {

    private EventHandlerWithIntentRecognition handler;
    private ControllerConfig mockConfig;
    private TaskManager mockTaskManager;
    private TaskScheduler mockTaskScheduler;
    private ContextEngine mockContextEngine;
    private AgentSessionApi mockSession;
    private IntentRecognizer mockRecognizer;

    private InputEvent sampleInputEvent;
    private Intent sampleCreateIntent;
    private Intent samplePauseIntent;
    private Intent sampleResumeIntent;
    private Intent sampleContinueIntent;
    private Intent sampleCancelIntent;
    private Intent sampleUnknownIntent;

    @BeforeEach
    void setUp() throws Exception {
        mockConfig = new ControllerConfig();
        mockConfig.setIntentLlmId("test_llm_id");
        mockConfig.setIntentConfidenceThreshold(0.8);

        mockTaskManager = mock(TaskManager.class);
        mockTaskScheduler = mock(TaskScheduler.class);
        mockContextEngine = mock(ContextEngine.class);
        mockSession = mock(AgentSessionApi.class);
        when(mockSession.getSessionId()).thenReturn("test_session_id");

        // Create handler using a mock ModelProvider
        IntentRecognizer.ModelProvider mockModelProvider = mock(IntentRecognizer.ModelProvider.class);
        handler = new EventHandlerWithIntentRecognition(mockModelProvider);

        // Inject dependencies
        handler.setConfig(mockConfig);
        handler.setTaskManager(mockTaskManager);
        handler.setTaskScheduler(mockTaskScheduler);
        handler.setContextEngine(mockContextEngine);

        // Create and inject mock recognizer via reflection
        mockRecognizer = mock(IntentRecognizer.class);
        Field recognizerField = EventHandlerWithIntentRecognition.class.getDeclaredField("recognizer");
        recognizerField.setAccessible(true);
        recognizerField.set(handler, mockRecognizer);

        // Create sample input event
        sampleInputEvent = new InputEvent(
                new ArrayList<>(List.of(new DataFrame.TextDataFrame("Create a new task")))
        );

        // Create sample intents
        sampleCreateIntent = new Intent(
                IntentType.CREATE_TASK, sampleInputEvent, "task1",
                "Test task description", null, null, null, 0.9, null
        );

        samplePauseIntent = new Intent(IntentType.PAUSE_TASK, sampleInputEvent, "task1");

        sampleResumeIntent = new Intent(IntentType.RESUME_TASK, sampleInputEvent, "task1");

        sampleContinueIntent = new Intent(
                IntentType.CONTINUE_TASK, sampleInputEvent, "task2",
                "Continue task description", List.of("task1"), null, null, 0.9, null
        );

        sampleCancelIntent = new Intent(IntentType.CANCEL_TASK, sampleInputEvent, "task1");

        sampleUnknownIntent = new Intent(
                IntentType.UNKNOWN_TASK, sampleInputEvent, "",
                null, null, null, null, 1.0, "Could you please clarify?"
        );
    }

    // ==================== Handle Input Tests ====================

    @Nested
    @DisplayName("Handle Input Tests")
    class HandleInputTests {

        @Test
        @DisplayName("test handling input with CREATE_TASK intent")
        void testHandleInputCreateTask() {
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(sampleCreateIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskManager).addTask(argThat((Task task) ->
                    "task1".equals(task.getTaskId())
                            && "Test task description".equals(task.getDescription())
                            && task.getStatus() == TaskStatus.SUBMITTED
            ));
        }

        @Test
        @DisplayName("test handling input with PAUSE_TASK intent")
        void testHandleInputPauseTask() {
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(samplePauseIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskScheduler).pauseTask("task1");
        }

        @Test
        @DisplayName("test handling input with RESUME_TASK intent - paused task")
        void testHandleInputResumeTask() {
            Task pausedTask = new Task("test_session_id", "task1", "test_task");
            pausedTask.setDescription("Test task");
            pausedTask.setStatus(TaskStatus.PAUSED);
            when(mockTaskManager.getTask(any(TaskFilter.class))).thenReturn(List.of(pausedTask));
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(sampleResumeIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskManager).updateTask(argThat((Task task) ->
                    task.getStatus() == TaskStatus.SUBMITTED));
        }

        @Test
        @DisplayName("test handling input with RESUME_TASK intent - task not paused")
        void testHandleInputResumeTaskNotPaused() {
            Task workingTask = new Task("test_session_id", "task1", "test_task");
            workingTask.setDescription("Test task");
            workingTask.setStatus(TaskStatus.WORKING);
            when(mockTaskManager.getTask(any(TaskFilter.class))).thenReturn(List.of(workingTask));
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(sampleResumeIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskManager, never()).updateTask(any(Task.class));
        }

        @Test
        @DisplayName("test handling input with CONTINUE_TASK intent")
        void testHandleInputContinueTask() {
            Task dependentTask = new Task("test_session_id", "task1", "test_task");
            dependentTask.setDescription("Dependent task");
            dependentTask.setStatus(TaskStatus.COMPLETED);
            dependentTask.setContextId("test_session_id_task1");
            dependentTask.setInputs(List.of(sampleInputEvent));
            when(mockTaskManager.getTask(any(TaskFilter.class))).thenReturn(List.of(dependentTask));
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages()).thenReturn(List.of());
            when(mockContextEngine.getContext("test_session_id_task1", "test_session_id"))
                    .thenReturn(mockContext);
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(sampleContinueIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskManager).addTask(argThat((Task task) ->
                    "task2".equals(task.getTaskId())
                            && "Continue task description".equals(task.getDescription())
                            && task.getInputs().contains(sampleInputEvent)
                            && task.getStatus() == TaskStatus.SUBMITTED
            ));
        }

        @Test
        @DisplayName("test handling input with CANCEL_TASK intent")
        void testHandleInputCancelTask() {
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(sampleCancelIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskScheduler).cancelTask("task1");
        }

        @Test
        @DisplayName("test handling input with UNKNOWN_TASK intent")
        void testHandleInputUnknownTask() {
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(sampleUnknownIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockSession).writeStream(argThat((Map<String, Object> data) ->
                    "Could you please clarify?".equals(data.get("clarification_prompt"))));
        }

        @Test
        @DisplayName("test handling input with multiple intents")
        void testHandleInputMultipleIntents() {
            when(mockRecognizer.recognize(any(), any()))
                    .thenReturn(List.of(sampleCreateIntent, samplePauseIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskManager).addTask(any(Task.class));
            verify(mockTaskScheduler).pauseTask("task1");
        }
    }

    // ==================== Handle Task Interaction Tests ====================

    @Nested
    @DisplayName("Handle Task Interaction Tests")
    class HandleTaskInteractionTests {

        @Test
        @DisplayName("test handling task interaction event")
        void testHandleTaskInteraction() {
            List<DataFrame> interactionData = List.of(
                    new DataFrame.JsonDataFrame(Map.of("type", "input_required", "message", "Please provide input"))
            );
            TaskInteractionEvent interactionEvent = new TaskInteractionEvent(interactionData, null);

            EventHandlerInput inputs = new EventHandlerInput(interactionEvent, mockSession);
            handler.handleTaskInteraction(inputs);

            verify(mockSession).writeStream(argThat((Map<String, Object> data) ->
                    data.containsKey("interaction")));
        }

        @Test
        @DisplayName("test handling task interaction with wrong event type")
        void testHandleTaskInteractionWrongEventType() {
            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            assertThrows(Exception.class, () -> handler.handleTaskInteraction(inputs));
        }
    }

    // ==================== Handle Task Completion Tests ====================

    @Nested
    @DisplayName("Handle Task Completion Tests")
    class HandleTaskCompletionTests {

        @Test
        @DisplayName("test handling task completion event")
        void testHandleTaskCompletion() {
            List<DataFrame> resultData = List.of(
                    new DataFrame.JsonDataFrame(Map.of("status", "completed", "output", "Task completed successfully"))
            );
            TaskCompletionEvent completionEvent = new TaskCompletionEvent(resultData, null);

            EventHandlerInput inputs = new EventHandlerInput(completionEvent, mockSession);
            handler.handleTaskCompletion(inputs);

            verify(mockSession).writeStream(argThat((Map<String, Object> data) ->
                    data.containsKey("result")));
        }

        @Test
        @DisplayName("test handling task completion with wrong event type")
        void testHandleTaskCompletionWrongEventType() {
            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            assertThrows(Exception.class, () -> handler.handleTaskCompletion(inputs));
        }
    }

    // ==================== Handle Task Failed Tests ====================

    @Nested
    @DisplayName("Handle Task Failed Tests")
    class HandleTaskFailedTests {

        @Test
        @DisplayName("test handling task failed event")
        void testHandleTaskFailed() {
            TaskFailedEvent failedEvent = new TaskFailedEvent("Task execution failed", null);

            EventHandlerInput inputs = new EventHandlerInput(failedEvent, mockSession);
            handler.handleTaskFailed(inputs);

            verify(mockSession).writeStream(argThat((Map<String, Object> data) ->
                    "Task execution failed".equals(data.get("error_message"))));
        }

        @Test
        @DisplayName("test handling task failed with wrong event type")
        void testHandleTaskFailedWrongEventType() {
            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            assertThrows(Exception.class, () -> handler.handleTaskFailed(inputs));
        }
    }

    // ==================== Supplement Intent Tests ====================

    @Nested
    @DisplayName("Supplement Intent Tests")
    class SupplementIntentTests {

        @Test
        @DisplayName("test handling input with SUPPLEMENT_TASK intent")
        void testHandleInputSupplementTask() {
            Task existingTask = new Task("test_session_id", "task1", "test_task");
            existingTask.setDescription("Original task");
            existingTask.setStatus(TaskStatus.WORKING);
            when(mockTaskManager.getTask(any(TaskFilter.class))).thenReturn(List.of(existingTask));

            Intent supplementIntent = new Intent(
                    IntentType.SUPPLEMENT_TASK, sampleInputEvent, "task1",
                    null, null, "Mock supplementary_info.", null, 0.9, null
            );
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(supplementIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskScheduler).pauseTask("task1");
            verify(mockTaskManager).updateTask(argThat((Task task) ->
                    task.getDescription().contains("任务补充信息")
                            && task.getStatus() == TaskStatus.SUBMITTED
            ));
        }
    }

    // ==================== Modify Intent Tests ====================

    @Nested
    @DisplayName("Modify Intent Tests")
    class ModifyIntentTests {

        @Test
        @DisplayName("test handling input with MODIFY_TASK intent")
        void testHandleInputModifyTask() {
            Task existingTask = new Task("test_session_id", "task1", "test_task");
            existingTask.setDescription("Original task");
            existingTask.setStatus(TaskStatus.WORKING);
            when(mockTaskManager.getTask(any(TaskFilter.class))).thenReturn(List.of(existingTask));

            Intent modifyIntent = new Intent(
                    IntentType.MODIFY_TASK, sampleInputEvent, "task1",
                    "Modified task description", null, null,
                    "Mock_modification_details", 0.9, null
            );
            when(mockRecognizer.recognize(any(), any())).thenReturn(List.of(modifyIntent));

            EventHandlerInput inputs = new EventHandlerInput(sampleInputEvent, mockSession);
            handler.handleInput(inputs);

            verify(mockTaskScheduler).cancelTask("task1");
            verify(mockTaskManager).updateTask(argThat((Task task) ->
                    "Modified task description".equals(task.getDescription())
                            && task.getStatus() == TaskStatus.SUBMITTED
            ));
        }
    }
}
