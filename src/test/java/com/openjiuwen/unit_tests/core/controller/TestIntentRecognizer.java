/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.controller;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.IntentRecognizer;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's {@code tests/unit_tests/core/controller/test_intent_recognizer.py}.
 */
class TestIntentRecognizer {

    @Test
    @DisplayName("intent recognizer converts LLM tool calls into create/cancel intents")
    void testIntentRecognizer() throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        toolCall("call_create_0", "create_task",
                                "{\"confidence\":0.9,\"task_description\":\"搜索北京今天天气\"}"),
                        textResponse("任务已创建"),
                        multiToolCall(
                                ToolCall.builder()
                                        .id("call_cancel_1")
                                        .type("function")
                                        .name("cancel_task")
                                        .arguments("{\"confidence\":0.9,\"task_id\":\"FIRST_TASK\"}")
                                        .build(),
                                ToolCall.builder()
                                        .id("call_create_1")
                                        .type("function")
                                        .name("create_task")
                                        .arguments("{\"confidence\":0.9,\"task_description\":\"搜索北京昨天天气\"}")
                                        .build()),
                        textResponse("任务已更新"));

        ControllerConfig config = new ControllerConfig();
        config.setEnableIntentRecognition(true);
        config.setIntentLlmId("ds_model");

        TaskManager taskManager = new TaskManager(config);
        ContextEngine contextEngine = new ContextEngine();
        IntentRecognizer recognizer = new IntentRecognizer(
                config,
                taskManager,
                new AbilityManager(),
                contextEngine,
                (modelId, session) -> model);

        String sessionId = "session_id";
        AgentSessionApi session = AgentSessionApi.create(sessionId, null, null);
        contextEngine.createContext(sessionId, session);

        InputEvent firstEvent = new InputEvent(List.of(
                new DataFrame.TextDataFrame("帮我搜索北京今天天气")));
        List<Intent> intents = recognizer.recognize(firstEvent, session);

        assertEquals(1, intents.size());
        assertEquals(IntentType.CREATE_TASK, intents.get(0).getIntentType());
        assertFalse(intents.get(0).getTargetTaskId().isBlank());

        Task task = new Task(sessionId, intents.get(0).getTargetTaskId(), "custom");
        task.setDescription(intents.get(0).getTargetTaskDescription());
        task.setPriority(1);
        task.setStatus(TaskStatus.WORKING);
        taskManager.addTask(task);

        String firstTaskId = intents.get(0).getTargetTaskId();
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        multiToolCall(
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
                                        .build()),
                        textResponse("任务已更新"));

        InputEvent secondEvent = new InputEvent(List.of(
                new DataFrame.TextDataFrame("不搜了，改成昨天天气")));
        intents = recognizer.recognize(secondEvent, session);

        assertEquals(2, intents.size());
        assertEquals(IntentType.CANCEL_TASK, intents.get(0).getIntentType());
        assertEquals(firstTaskId, intents.get(0).getTargetTaskId());
        assertEquals(IntentType.CREATE_TASK, intents.get(1).getIntentType());
        assertEquals("搜索北京昨天天气", intents.get(1).getTargetTaskDescription());
    }

    @Test
    @Disabled("LLM API required")
    @DisplayName("real LLM intent-recognition case")
    void testRealCase() {
        // Mirrors Python's skipped real API case.
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
}
