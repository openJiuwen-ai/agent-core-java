/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.utils;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Message handler utility methods for legacy controllers.
 * Mirrors Python's {@code MessageHandlerUtils}.
 */
public final class MessageHandlerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MessageHandlerUtils.class);

    private MessageHandlerUtils() {
    }

    /**
     * Format LLM inputs by combining system prompt with chat history.
     */
    public static List<BaseMessage> formatLlmInputs(Object inputs, List<BaseMessage> chatHistory,
                                                     String promptTemplate, Map<String, Object> keywords) {
        List<BaseMessage> systemPrompt = new ArrayList<>();
        if (promptTemplate != null && !promptTemplate.isEmpty()) {
            systemPrompt.add(new BaseMessage("system", promptTemplate));
        }
        return concatSystemPromptWithChatHistory(systemPrompt, chatHistory);
    }

    /**
     * Concatenate system prompt with chat history.
     */
    public static List<BaseMessage> concatSystemPromptWithChatHistory(List<BaseMessage> systemPrompt,
                                                                      List<BaseMessage> chatHistory) {
        List<BaseMessage> resultMessages = new ArrayList<>();

        if (chatHistory == null || chatHistory.isEmpty() || !"system".equals(chatHistory.get(0).getRole())) {
            resultMessages.addAll(systemPrompt);
        }

        if (chatHistory != null) {
            resultMessages.addAll(chatHistory);
        }
        return resultMessages;
    }

    /**
     * Parse LLM output tool calls into tasks.
     */
    public static List<Task> parseLlmOutput(AssistantMessage response, Object agentConfig) {
        if (response == null || response.getToolCalls() == null) {
            return Collections.emptyList();
        }
        return createTasksFromToolCalls(response.getToolCalls(), agentConfig);
    }

    /**
     * Create tasks from tool calls.
     * Mirrors Python's {@code create_tasks_from_tool_calls()}.
     */
    @SuppressWarnings("unchecked")
    public static List<Task> createTasksFromToolCalls(List<ToolCall> toolCalls, Object agentConfig) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        List<Task> result = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            String toolName = toolCall.getName();
            Task.TaskInput taskInput = new Task.TaskInput();
            taskInput.setTargetName(toolName);
            taskInput.setArguments(toolCall.getArguments() != null ? toolCall.getArguments() : "{}");

            Task task = Task.builder()
                    .taskId(toolCall.getId())
                    .input(taskInput)
                    .build();
            result.add(task);
        }

        if (result.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.AGENT_TOOL_NOT_FOUND,
                    "error_msg", "failed to create task from tool calls");
        }
        return result;
    }

    /**
     * Check if exec result is an interaction result.
     */
    public static boolean isInteractionResult(Object execResult) {
        if (execResult instanceof Map<?, ?> map) {
            Object error = map.get("error");
            Object value = map.get("value");
            return Boolean.TRUE.equals(error) && value instanceof List;
        }
        return false;
    }

    /**
     * Create interrupt result.
     */
    public static Map<String, Object> createInterruptResult(Exception e, String toolName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", true);
        result.put("value", e.getMessage());
        result.put("tool_name", toolName);
        return result;
    }

    /**
     * Validate execution inputs.
     */
    public static boolean validateExecutionInputs(Object execResult, Object subTaskResult) {
        return execResult != null;
    }

    /**
     * Check whether a user message should be added (avoids duplicates and post-tool msgs).
     */
    public static boolean shouldAddUserMessage(String query, ContextEngine contextEngine, Session session) {
        ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
        if (agentContext == null) {
            return true;
        }
        List<BaseMessage> lastMessages = agentContext.getMessages(1, true);
        if (lastMessages == null || lastMessages.isEmpty()) {
            return true;
        }

        BaseMessage lastMessage = lastMessages.get(lastMessages.size() - 1);
        if ("tool".equals(lastMessage.getRole())) {
            LOG.info("Skipping user message - post-tool-call request");
            return false;
        }
        if ("user".equals(lastMessage.getRole()) && query != null && query.equals(lastMessage.getContentAsString())) {
            LOG.info("Skipping duplicate user message");
            return false;
        }
        return true;
    }

    /**
     * Add user message to context.
     * Mirrors Python's {@code add_user_message()}.
     */
    public static void addUserMessage(Object query, ContextEngine contextEngine, Session session) {
        String queryStr = query != null ? String.valueOf(query) : "";
        if (shouldAddUserMessage(queryStr, contextEngine, session)) {
            ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
            if (agentContext != null) {
                agentContext.addMessages(new UserMessage(queryStr));
                LOG.info("Added user message");
            }
        }
    }

    /**
     * Add AI message to context.
     */
    public static void addAiMessage(AssistantMessage aiMessage, ContextEngine contextEngine, Session session) {
        if (aiMessage != null) {
            ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
            if (agentContext != null) {
                agentContext.addMessages(aiMessage);
            }
        }
    }

    /**
     * Add tool result to context.
     */
    public static void addToolResult(Event event, ContextEngine contextEngine, Session session) {
        if (event == null || event.getContent() == null || event.getContent().getTaskResult() == null) {
            return;
        }
        ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
        if (agentContext == null) {
            return;
        }

        Object toolResult = event.getContent().getTaskResult();
        String content;
        if (toolResult instanceof OutputSchema outputSchema) {
            Object payload = outputSchema.getPayload();
            if (payload instanceof Map<?, ?> map) {
                Object output = map.get("output");
                content = output != null ? String.valueOf(output) : "";
            } else {
                content = String.valueOf(toolResult);
            }
        } else {
            content = String.valueOf(toolResult);
        }

        String taskId = event.getContext() != null ? event.getContext().getTaskId() : null;
        ToolMessage toolMessage = new ToolMessage(content, taskId);
        agentContext.addMessages(toolMessage);
    }

    /**
     * Get chat history limited by max rounds.
     */
    public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session, int maxRounds) {
        ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
        if (agentContext == null) {
            return Collections.emptyList();
        }
        List<BaseMessage> chatHistory = agentContext.getMessages();
        if (chatHistory == null) {
            return Collections.emptyList();
        }
        int limit = 2 * maxRounds;
        if (chatHistory.size() <= limit) {
            return chatHistory;
        }
        return chatHistory.subList(chatHistory.size() - limit, chatHistory.size());
    }

    /**
     * Filter and validate user input, extract fields by schema.
     */
    public static Map<String, Object> filterInputs(Map<String, Object> schema, Map<String, Object> userData) {
        if (schema == null || schema.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            Object spec = entry.getValue();
            boolean required = false;
            if (spec instanceof Map<?, ?> specMap) {
                required = Boolean.TRUE.equals(specMap.get("required"));
            }

            if (!userData.containsKey(key)) {
                if (required) {
                    throw new IllegalArgumentException("missing required parameter: " + key);
                }
                continue;
            }
            filtered.put(key, userData.get(key));
        }
        return filtered;
    }
}
