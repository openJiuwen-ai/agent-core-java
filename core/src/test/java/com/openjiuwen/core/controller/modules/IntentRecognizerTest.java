/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.AgentSessionApi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for {@link IntentRecognizer}.
 *
 * <p>Mirrors Python's {@code IntentRecognizer} in
 * {@code openjiuwen/core/controller/modules/intent_recognizer.py}.</p>
 *
 * <p>Mirrors Python's {@code TestIntentRecognizer} in
 * {@code tests/unit_tests/core/controller/test_intent_recognizer.py}.</p>
 */
class IntentRecognizerTest {

    @Test
    void recognizesToolCallsAndCreatesContextWhenMissing() {
        ControllerConfig config = new ControllerConfig();
        config.setEnableIntentRecognition(true);
        config.setIntentLlmId("ds_model");

        TaskManager taskManager = new TaskManager(config);
        ContextEngine contextEngine = new ContextEngine();
        FakeSession session = new FakeSession("session_id");

        Queue<AssistantMessage> responses = new ArrayDeque<>();
        responses.add(toolCall("call_create_0", "create_task",
                "{\"confidence\":0.9,\"task_description\":\"搜索北京今天天气\"}"));
        responses.add(textResponse("任务已创建"));

        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            assertThat(options.getTools()).isNotEmpty();
            return CompletableFuture.completedFuture(responses.remove());
        });
        IntentRecognizer recognizer = new IntentRecognizer(
                config,
                taskManager,
                new Object(),
                contextEngine,
                (modelId, currentSession) -> {
                    assertThat(modelId).isEqualTo("ds_model");
                    assertThat(currentSession).isSameAs(session);
                    return model;
                });

        InputEvent firstEvent = new InputEvent(List.of(new DataFrame.TextDataFrame("帮我搜索北京今天天气")));
        List<Intent> intents = recognizer.recognize(firstEvent, session);

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).getIntentType()).isEqualTo(IntentType.CREATE_TASK);
        assertThat(intents.get(0).getTargetTaskDescription()).isEqualTo("搜索北京今天天气");
        assertThat(intents.get(0).getTargetTaskId()).isNotBlank();

        ModelContext context = contextEngine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId());
        assertThat(context).isNotNull();
        assertThat(context.getMessages(null, true))
                .extracting(BaseMessage::getRole)
                .containsExactly("user", "assistant", "tool", "assistant");
        assertThat(context.getMessages(null, true).get(2)).isInstanceOf(ToolMessage.class);

        Task task = new Task(session.getSessionId(), intents.get(0).getTargetTaskId(), "custom");
        task.setDescription(intents.get(0).getTargetTaskDescription());
        task.setPriority(1);
        task.setStatus(TaskStatus.WORKING);
        taskManager.addTask(task);

        String firstTaskId = intents.get(0).getTargetTaskId();
        responses.add(multiToolCall(
                ToolCall.builder()
                        .id("call_cancel_1")
                        .type("function")
                        .name("cancel_task")
                        .arguments("{\"confidence\":0.9,\"task_id\":\"" + firstTaskId + "\"}")
                        .build(),
                ToolCall.builder()
                        .id("call_create_1")
                        .type("function")
                        .name("create_task")
                        .arguments("{\"confidence\":0.9,\"task_description\":\"搜索北京昨天天气\"}")
                        .build()));
        responses.add(textResponse("任务已更新"));

        InputEvent secondEvent = new InputEvent(List.of(new DataFrame.TextDataFrame("不搜了，改成昨天天气")));
        intents = recognizer.recognize(secondEvent, session);

        assertThat(intents).hasSize(2);
        assertThat(intents.get(0).getIntentType()).isEqualTo(IntentType.CANCEL_TASK);
        assertThat(intents.get(0).getTargetTaskId()).isEqualTo(firstTaskId);
        assertThat(intents.get(1).getIntentType()).isEqualTo(IntentType.CREATE_TASK);
        assertThat(intents.get(1).getTargetTaskDescription()).isEqualTo("搜索北京昨天天气");
    }

    @Test
    void rejectsUnsupportedIntentRecognitionInputs() {
        IntentRecognizer recognizer = recognizerWithResponses(textResponse("unused"));
        FakeSession session = new FakeSession("session_id");

        assertThatThrownBy(() -> recognizer.recognize(
                new InputEvent(List.of(new DataFrame.FileDataFrame("a.txt", "text/plain", new byte[0], null))),
                session))
                .hasMessageContaining("Inputs with files or jsons are not supported for intent recognition.");

        assertThatThrownBy(() -> recognizer.recognize(
                new InputEvent(List.of(
                        new DataFrame.TextDataFrame("first"),
                        new DataFrame.TextDataFrame("second"))),
                session))
                .hasMessageContaining("Multiple inputs are not supported for intent recognition.");
    }

    @Test
    @Disabled("LLM API required; skipped in Python source test_real_case")
    void testRealCase() {
        // Mirrors Python's skipped real API case.
    }

    private static IntentRecognizer recognizerWithResponses(AssistantMessage... messages) {
        ControllerConfig config = new ControllerConfig();
        config.setIntentLlmId("ds_model");
        Queue<AssistantMessage> responses = new ArrayDeque<>(List.of(messages));
        Model model = new Model((inputMessages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(responses.remove()));
        return new IntentRecognizer(
                config,
                new TaskManager(config),
                new Object(),
                new ContextEngine(),
                (modelId, session) -> model);
    }

    private static AssistantMessage textResponse(String text) {
        return new AssistantMessage(text);
    }

    private static AssistantMessage toolCall(String id, String name, String arguments) {
        return multiToolCall(ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build());
    }

    private static AssistantMessage multiToolCall(ToolCall... calls) {
        AssistantMessage message = new AssistantMessage("");
        message.setToolCalls(List.of(calls));
        return message;
    }

    /**
     * Test helper session dependency.
     *
     * <p>Mirrors Python's {@code Session.get_session_id} dependency in
     * {@code openjiuwen/core/controller/modules/intent_recognizer.py}.</p>
     *
     * <p>Mirrors Python's {@code TestIntentRecognizer.setUp} helper context in
     * {@code tests/unit_tests/core/controller/test_intent_recognizer.py}.</p>
     */
    private static final class FakeSession implements AgentSessionApi, ContextEngine.SessionPort {

        private final String sessionId;

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return java.util.Collections.emptyIterator();
        }
    }
}
