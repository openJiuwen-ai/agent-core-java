/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Intent recognizer.
 * <p>
 * Recognizes user intent from input events by using LLM tool-calling.
 * Converts events into {@link Intent} objects.
 * <p>
 * Mirrors Python's {@code IntentRecognizer} in
 * {@code openjiuwen/core/controller/modules/intent_recognizer.py}.
 */
public class IntentRecognizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_MESSAGE_LEN = 50;

    private final ControllerConfig config;
    private final TaskManager taskManager;
    private final ContextEngine contextEngine;
    private final Object abilityManager;

    private final SystemMessage systemMessage;

    private static final String USER_PROMPT_TEMPLATE = """
            你当前拥有的任务有：
            %s

            当前用户的输入为：
            %s

            请根据你当前的任务和用户输入，进行合适的任务操作。
            """;

    private static final String SYSTEM_PROMPT = """
            # 角色
            你是一个任务管理助手，专门使用工具创建和管理任务。你的核心理念是：**任何用户请求都可以转化为一个任务**，并由任务管理器处理。

            # 核心原则
            1. **任务化一切**：对于任何用户请求（包括信息查询、事务处理、提醒等），你的第一反应不是直接执行或拒绝，而是思考如何将它创建为一个任务。
            2. **透明管理**：如果任务需要外部能力（如天气API），你仍然创建它，并明确告知用户任务的状态。

            # 工作流程
            1. **解析请求**：理解用户想做什么。
            2. **任务操作**：使用工具创建一个对应的任务或修改已有任务。
            3. **永远不拒绝**：不声称“超出能力范围”，而是告知用户任务会由其他执行器处理。

            # 任务目标
            - 根据用户输入，**总是优先创建对应的任务**。
            - 使用工具进行任务操作（创建、更新、列表、删除）。
            - 只有纯粹闲聊或问候时不调用工具。
            """;

    /**
     * Functional interface for obtaining a Model instance.
     * This avoids a direct dependency on Runner.
     *
     * <p>Mirrors Python's {@code Runner.resource_mgr.get_model(...)} use in
     * {@code openjiuwen/core/controller/modules/intent_recognizer.py}.</p>
     */
    @FunctionalInterface
    public interface ModelProvider {
        Model getModel(String modelId, AgentSessionApi session);
    }

    private final ModelProvider modelProvider;

    public IntentRecognizer(
            ControllerConfig config,
            TaskManager taskManager,
            Object abilityManager,
            ContextEngine contextEngine,
            ModelProvider modelProvider
    ) {
        this.config = config;
        this.taskManager = taskManager;
        this.abilityManager = abilityManager;
        this.contextEngine = contextEngine;
        this.modelProvider = modelProvider;
        this.systemMessage = new SystemMessage(SYSTEM_PROMPT);
    }

    private UserMessage prepareUserMessage(String query) {
        List<Task> tasks = taskManager.getTask(null);
        StringBuilder taskPrompt = new StringBuilder();
        if (tasks != null && !tasks.isEmpty()) {
            for (Task task : tasks) {
                taskPrompt.append("## Task id: ").append(task.getTaskId())
                        .append("\n### Task description: ").append(task.getDescription())
                        .append("\nStatus: ").append(task.getStatus())
                        .append("\n");
            }
        } else {
            taskPrompt.append("无");
        }

        String prompt = String.format(USER_PROMPT_TEMPLATE, taskPrompt, query);
        return new UserMessage(prompt);
    }

    /**
     * Recognize intents from an event.
     *
     * @param event   input event
     * @param session session object
     * @return list of recognized intents
     */
    public List<Intent> recognize(Event event, AgentSessionApi session) {
        ModelContext context = contextEngine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId());
        if (context == null) {
            context = contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        }

        if (!(event instanceof InputEvent inputEvent)) {
            throw new IllegalArgumentException("Event must be an InputEvent for intent recognition");
        }

        List<DataFrame> inputs = inputEvent.getInputData();
        List<DataFrame.TextDataFrame> texts = new ArrayList<>();
        boolean hasFiles = false;
        boolean hasJsons = false;
        for (DataFrame df : inputs) {
            if (df instanceof DataFrame.TextDataFrame tdf) {
                texts.add(tdf);
            } else if (df instanceof DataFrame.FileDataFrame) {
                hasFiles = true;
            } else if (df instanceof DataFrame.JsonDataFrame) {
                hasJsons = true;
            }
        }

        if (hasFiles || hasJsons) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Inputs with files or jsons are not supported for intent recognition.");
        }
        if (texts.size() > 1) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Multiple inputs are not supported for intent recognition.");
        }

        Model model = modelProvider.getModel(config.getIntentLlmId(), session);
        UserMessage userMessage = prepareUserMessage(texts.get(0).text());
        context.addMessages(userMessage).toCompletableFuture().join();

        IntentToolkits toolkits = new IntentToolkits(event, config.getIntentConfidenceThreshold());

        List<BaseMessage> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(context.getMessages(MAX_MESSAGE_LEN, true));

        AssistantMessage response;
        try {
            response = model.invoke(
                    messages,
                    ModelInvokeOptions.builder()
                            .tools(toolkits.getOpenaiToolSchemas(config.getIntentTypeList()))
                            .build()
            ).toCompletableFuture().join();
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "LLM invocation failed: " + e.getMessage());
        }
        context.addMessages(response).toCompletableFuture().join();

        List<Intent> intents = new ArrayList<>();
        while (true) {
            if (response.getToolCalls() == null || response.getToolCalls().isEmpty()) {
                break;
            }
            for (ToolCall toolCall : response.getToolCalls()) {
                try {
                    Map<String, Object> args = OBJECT_MAPPER.readValue(
                            toolCall.getArguments(), new TypeReference<>() {});
                    IntentToolkits.IntentResult result = toolkits.dispatch(toolCall.getName(), args);
                    intents.add(result.intent());
                    context.addMessages(new ToolMessage(result.message(), toolCall.getId()))
                            .toCompletableFuture().join();
                } catch (Exception e) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                            "error_msg", "Failed to parse tool call: " + e.getMessage());
                }
            }

            messages = new ArrayList<>();
            messages.add(systemMessage);
            messages.addAll(context.getMessages(MAX_MESSAGE_LEN, true));

            try {
                response = model.invoke(
                        messages,
                        ModelInvokeOptions.builder()
                                .tools(toolkits.getOpenaiToolSchemas(null))
                                .build()
                ).toCompletableFuture().join();
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                        "error_msg", "LLM invocation failed: " + e.getMessage());
            }
            context.addMessages(response).toCompletableFuture().join();
        }

        return intents;
    }
}

