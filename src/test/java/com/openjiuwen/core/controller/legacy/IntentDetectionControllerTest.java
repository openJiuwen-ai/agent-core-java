/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskInput;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests legacy intent detection controller routing behavior.
 *
 * <p>Mirrors Python's {@code IntentDetectionController} in
 * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.</p>
 */
class IntentDetectionControllerTest {

    @Test
    void taskQueueRegistersFindsCancelsAndUnregistersTasks() {
        IntentDetectionController.TaskQueue queue = new IntentDetectionController.TaskQueue();
        Task task = new Task();
        task.setTaskId("task-1");
        CompletableFuture<Void> future = new CompletableFuture<>();

        queue.registerTask("conv-1", task, future, "workflow-1");

        assertThat(queue.hasRunningTask("conv-1")).isTrue();
        assertThat(queue.findTask("conv-1").getTask()).isSameAs(task);
        assertThat(queue.findTask("conv-1").getTargetId()).isEqualTo("workflow-1");
        assertThat(queue.cancelRunningTask("conv-1")).isTrue();
        assertThat(future).isCancelled();

        queue.unregisterTask("conv-1");

        assertThat(queue.hasRunningTask("conv-1")).isFalse();
        assertThat(queue.findTask("conv-1")).isNull();
    }

    @Test
    void intentDefaultsMetadataToMutableMap() {
        IntentDetectionController.Intent intent = new IntentDetectionController.Intent();

        intent.getMetadata().put("default_response_text", "hello");

        assertThat(intent.getIntentType()).isEqualTo(IntentDetectionController.IntentType.UNKNOWN);
        assertThat(intent.getMetadata()).containsEntry("default_response_text", "hello");
    }

    @Test
    void defaultResponseWritesWorkflowFinalFrame() {
        TestController controller = new TestController();
        FakeSession session = new FakeSession("session-1");
        IntentDetectionController.Intent intent = IntentDetectionController.Intent.builder()
                .intentType(IntentDetectionController.IntentType.DEFAULT_RESPONSE)
                .metadata(Map.of("default_response_text", "answer"))
                .build();

        Map<String, Object> result = controller.handleDefaultResponse(null, intent, session);

        assertThat(result).containsEntry("status", "default_response");
        assertThat(result).containsEntry("result_type", "answer");
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.get("output");
        assertThat(output).containsEntry("answer", "answer");
        assertThat(session.streams).hasSize(1);
        OutputSchema outputSchema = (OutputSchema) session.streams.get(0);
        assertThat(outputSchema.getType()).isEqualTo("workflow_final");
        assertThat(outputSchema.getIndex()).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outputSchema.getPayload();
        assertThat(payload).containsEntry("response", "answer");
    }

    @Test
    void cancelIntentMarksTaskCancelled() {
        TestController controller = new TestController();
        Task task = new Task();
        task.setTaskId("task-1");
        IntentDetectionController.Intent intent = IntentDetectionController.Intent.builder()
                .intentType(IntentDetectionController.IntentType.CANCEL_TASK)
                .task(task)
                .build();

        Map<String, Object> result = controller.handleCancel(null, intent, new FakeSession("session-1"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(result).containsEntry("status", "cancelled");
        assertThat(result).containsEntry("task_id", "task-1");
    }

    @Test
    void resumeRemapsQueryToInterruptedComponent() {
        TestController controller = new TestController();
        FakeSession session = new FakeSession("session-1");
        Map<String, Object> interruptedInfo = new LinkedHashMap<>();
        interruptedInfo.put("component_id", "questioner-2");
        Map<String, Object> interruptedTasks = new LinkedHashMap<>();
        interruptedTasks.put("workflow_1", interruptedInfo);
        session.state.put("workflow_controller", Map.of("interrupted_tasks", interruptedTasks));
        Task task = new Task();
        task.setTaskId("task-1");
        task.setStatus(TaskStatus.INTERRUPTED);
        task.setInput(new TaskInput("workflow.1", "workflow", new LinkedHashMap<>()));
        Event event = Event.createUserEvent("updated answer", "conv-1", "user-1", Map.of());
        IntentDetectionController.Intent intent = IntentDetectionController.Intent.builder()
                .intentType(IntentDetectionController.IntentType.RESUME_TASK)
                .task(task)
                .build();

        Map<String, Object> result = controller.handleResume(event, intent, session);

        assertThat(result).containsEntry("status", "executed");
        assertThat(task.getInput().getArguments()).isInstanceOf(InteractiveInput.class);
        InteractiveInput input = (InteractiveInput) task.getInput().getArguments();
        assertThat(input.getUserInputs()).containsEntry("questioner-2", "updated answer");
    }

    @Test
    void handleEventAddsUserMessageAndReturnsDefaultResponse() {
        ContextEngine contextEngine = new ContextEngine();
        FakeSession session = new FakeSession("session-1");
        ModelContext context = contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        TestController controller = new TestController(null, contextEngine);
        controller.intent = IntentDetectionController.Intent.builder()
                .intentType(IntentDetectionController.IntentType.DEFAULT_RESPONSE)
                .metadata(Map.of("default_response_text", "fallback"))
                .build();
        Event event = Event.createUserEvent("hello", "conv-1", "user-1", Map.of());

        Map<String, Object> result = controller.handleEvent(event, session);

        assertThat(result).containsEntry("status", "default_response");
        List<BaseMessage> messages = context.getMessages(null, true);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
        assertThat(messages.get(0).getContent()).isEqualTo("hello");
        assertThat(session.streams).hasSize(1);
    }

    private static final class TestController extends IntentDetectionController {
        private Intent intent = new Intent();

        private TestController() {
        }

        private TestController(Object config, ContextEngine contextEngine) {
            super(config, contextEngine);
        }

        @Override
        protected Intent intentDetection(Event event, Object session) {
            return intent;
        }

        @Override
        protected Map<String, Object> execTask(Event.EventContent messageContent, Task task, Object session) {
            return Map.of("status", "executed", "task_id", task.getTaskId());
        }

        @Override
        protected Map<String, Object> interruptTask(Task task, Object session) {
            return Map.of("status", "interrupted", "task_id", task.getTaskId());
        }
    }

    private static final class FakeSession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> streams = new ArrayList<>();

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
            streams.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return streams.iterator();
        }
    }
}
