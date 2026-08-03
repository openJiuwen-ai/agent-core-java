/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskInput;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Utility methods used by the legacy controller message flow.
 *
 * <p>Mirrors Python's {@code MessageHandlerUtils} in
 * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
 */
public final class MessageHandlerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MessageHandlerUtils.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MessageHandlerUtils() {
    }

    public static List<BaseMessage> formatLlmInputs(Object inputs, List<BaseMessage> chatHistory,
                                                    AgentConfigView config, Map<String, Object> keywords) {
        Objects.requireNonNull(config, "config must not be null");
        Map<String, Object> userFields = new LinkedHashMap<>();
        if (inputs instanceof InteractiveInput) {
            userFields = new LinkedHashMap<>();
        } else if (inputs instanceof Map<?, ?> inputMap) {
            userFields = deepCopyMap(inputMap);
        } else {
            userFields.put("query", inputs);
        }

        if (keywords != null && !keywords.isEmpty()) {
            userFields.putAll(keywords);
        }

        List<BaseMessage> systemPrompt = PromptTemplate.builder()
                .content(config.promptTemplate())
                .build()
                .format(userFields)
                .toMessages();
        return concatSystemPromptWithChatHistory(systemPrompt, chatHistory);
    }

    public static List<BaseMessage> concatSystemPromptWithChatHistory(List<BaseMessage> systemPrompt,
                                                                      List<BaseMessage> chatHistory) {
        List<BaseMessage> resultMessages = new ArrayList<>();
        List<BaseMessage> safeSystemPrompt = systemPrompt == null ? List.of() : systemPrompt;
        List<BaseMessage> safeChatHistory = chatHistory == null ? List.of() : chatHistory;

        if (safeChatHistory.isEmpty() || !"system".equals(safeChatHistory.get(0).getRole())) {
            resultMessages.addAll(safeSystemPrompt);
        }
        resultMessages.addAll(safeChatHistory);
        return resultMessages;
    }

    public static List<Task> parseLlmOutput(BaseMessage response, AgentConfigView config) {
        if (response instanceof AssistantMessage assistantMessage) {
            return createTasksFromToolCalls(assistantMessage.getToolCalls(), config);
        }
        return createTasksFromToolCalls(List.of(), config);
    }

    public static List<Task> createTasksFromToolCalls(List<ToolCall> toolCalls, AgentConfigView config) {
        Objects.requireNonNull(config, "config must not be null");
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        List<Task> result = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            String toolName = toolCall.getName();
            for (WorkflowView workflow : safeWorkflows(config)) {
                if (workflow != null && Objects.equals(workflow.name(), toolName)) {
                    TaskInput taskInput = new TaskInput(
                            workflow.id() + "_" + workflow.version(),
                            toolName,
                            parseToolArguments(toolCall, toolName, "workflow")
                    );
                    result.add(createTask(toolCall.getId(), TaskType.WORKFLOW, taskInput));
                    break;
                }
            }
            for (PluginView plugin : safePlugins(config)) {
                if (plugin != null && Objects.equals(plugin.name(), toolName)) {
                    TaskInput taskInput = new TaskInput(
                            "",
                            toolName,
                            parseToolArguments(toolCall, toolName, "plugin")
                    );
                    result.add(createTask(toolCall.getId(), TaskType.PLUGIN, taskInput));
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TOOL_NOT_FOUND,
                    "error_msg",
                    "failed to create task from tool calls"
            );
        }
        return result;
    }

    public static TaskType determineTaskType(String toolName, AgentConfigView config) {
        Objects.requireNonNull(config, "config must not be null");
        for (WorkflowView workflow : safeWorkflows(config)) {
            if (workflow != null && Objects.equals(toolName, workflow.name())) {
                return TaskType.WORKFLOW;
            }
        }
        for (PluginView plugin : safePlugins(config)) {
            if (plugin != null && Objects.equals(toolName, plugin.name())) {
                return TaskType.PLUGIN;
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.AGENT_TOOL_NOT_FOUND,
                "error_msg",
                "not find tool call type: " + toolName
        );
    }

    public static boolean isInteractionResult(Object execResult) {
        if (!(execResult instanceof Map<?, ?> resultMap)) {
            return false;
        }
        return isPythonTruthy(resultMap.get("error")) && resultMap.get("value") instanceof List<?>;
    }

    public static Map<String, Object> createInterruptResult(Exception exception, String toolName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", true);
        result.put("value", exception == null ? null : exception.getMessage());
        result.put("tool_name", toolName);
        return result;
    }

    public static boolean validateExecutionInputs(Object execResult, Object subTaskResult) {
        return execResult != null;
    }

    public static boolean shouldAddUserMessage(Object query, ContextEngine contextEngine, AgentSessionApi session) {
        if (contextEngine == null || session == null) {
            return true;
        }
        ModelContext agentContext = contextEngine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId());
        if (agentContext == null) {
            return true;
        }
        List<BaseMessage> lastMessageList = agentContext.getMessages(1, true);
        if (lastMessageList == null || lastMessageList.isEmpty()) {
            return true;
        }

        BaseMessage lastMessage = lastMessageList.get(0);
        if ("tool".equals(lastMessage.getRole())) {
            LOG.info("Skipping user message - post-tool-call request");
            return false;
        }
        if ("user".equals(lastMessage.getRole()) && Objects.equals(lastMessage.getContent(), query)) {
            LOG.info("Skipping duplicate user message");
            return false;
        }
        return true;
    }

    public static CompletionStage<Void> addUserMessage(Object query, ContextEngine contextEngine,
                                                       AgentSessionApi session) {
        if (!shouldAddUserMessage(query, contextEngine, session)) {
            return CompletableFuture.completedFuture(null);
        }
        ModelContext agentContext = getDefaultContext(contextEngine, session);
        if (agentContext == null) {
            return CompletableFuture.completedFuture(null);
        }
        UserMessage userMessage = UserMessage.builder()
                .content(query)
                .build();
        CompletionStage<List<BaseMessage>> added = agentContext.addMessages(userMessage);
        if (UserConfig.isSensitive()) {
            LOG.info("Added user message");
        } else {
            LOG.info("Added user message: {}", query);
        }
        return added.thenApply(ignored -> null);
    }

    public static CompletionStage<Void> addAiMessage(AssistantMessage aiMessage, ContextEngine contextEngine,
                                                     AgentSessionApi session) {
        if (aiMessage == null) {
            return CompletableFuture.completedFuture(null);
        }
        ModelContext agentContext = getDefaultContext(contextEngine, session);
        if (agentContext == null) {
            return CompletableFuture.completedFuture(null);
        }
        return agentContext.addMessages(aiMessage).thenApply(ignored -> null);
    }

    public static CompletionStage<Void> addToolResult(Event event, ContextEngine contextEngine,
                                                      AgentSessionApi session) {
        if (event == null) {
            return CompletableFuture.completedFuture(null);
        }
        ModelContext agentContext = getDefaultContext(contextEngine, session);
        if (agentContext == null) {
            return CompletableFuture.completedFuture(null);
        }

        Object toolResult = extractTaskOutput(event.getContent() == null ? null : event.getContent().getTaskResult());
        if (toolResult instanceof OutputSchema outputSchema) {
            Object payload = outputSchema.getPayload();
            if (payload instanceof Map<?, ?> payloadMap) {
                toolResult = payloadMap.containsKey("output") ? payloadMap.get("output") : "";
            }
        } else if (toolResult instanceof WorkflowOutputView workflowOutput) {
            toolResult = workflowOutput.result();
        } else {
            Object reflectedResult = readWorkflowResult(toolResult);
            if (reflectedResult != Unreadable.INSTANCE) {
                toolResult = reflectedResult;
            }
        }

        Object dumped = JsonUtils.safeJsonDumps(toolResult, String.valueOf(toolResult));
        String content = dumped == null ? null : String.valueOf(dumped);
        String taskId = event.getContext() == null ? null : event.getContext().getTaskId();
        ToolMessage toolMessage = ToolMessage.builder()
                .content(content)
                .toolCallId(taskId)
                .build();
        return agentContext.addMessages(toolMessage).thenApply(ignored -> null);
    }

    public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, AgentSessionApi session,
                                                   AgentConfigView config) {
        if (config == null || config.constrain() == null) {
            return List.of();
        }
        return sliceChatHistory(contextEngine, session, config.constrain().reservedMaxChatRounds());
    }

    public static Map<String, Object> filterInputs(Map<String, Object> schema, Map<String, Object> userData) {
        if (schema == null || schema.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safeUserData = userData == null ? Map.of() : userData;
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            boolean required = false;
            if (entry.getValue() instanceof Map<?, ?> specMap) {
                required = Boolean.TRUE.equals(specMap.get("required"));
            }
            if (!safeUserData.containsKey(key)) {
                if (required) {
                    throw new IllegalArgumentException("missing required parameter: " + key);
                }
                continue;
            }
            filtered.put(key, safeUserData.get(key));
        }
        return filtered;
    }

    public static CompletionStage<Void> addWorkflowMessageToChatHistory(BaseMessage message, String workflowId,
                                                                        ContextEngine contextEngine,
                                                                        AgentSessionApi session) {
        if (message == null || contextEngine == null || session == null) {
            return CompletableFuture.completedFuture(null);
        }
        ModelContext workflowContext = contextEngine.getContext(workflowId, session.getSessionId());
        if (workflowContext == null) {
            return CompletableFuture.completedFuture(null);
        }
        return workflowContext.addMessages(message).thenApply(ignored -> null);
    }

    static List<BaseMessage> sliceChatHistory(ContextEngine contextEngine, AgentSessionApi session,
                                              int chatHistoryMaxTurn) {
        ModelContext agentContext = getDefaultContext(contextEngine, session);
        if (agentContext == null) {
            return List.of();
        }
        List<BaseMessage> chatHistory = agentContext.getMessages(null, true);
        if (chatHistory == null || chatHistory.isEmpty()) {
            return List.of();
        }
        int start = pythonSliceStart(chatHistory.size(), -2 * chatHistoryMaxTurn);
        return List.copyOf(chatHistory.subList(start, chatHistory.size()));
    }

    private static ModelContext getDefaultContext(ContextEngine contextEngine, AgentSessionApi session) {
        if (contextEngine == null || session == null) {
            return null;
        }
        return contextEngine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId());
    }

    private static Task createTask(String taskId, TaskType taskType, TaskInput taskInput) {
        Task task = new Task();
        task.setTaskId(taskId);
        task.setTaskType(taskType);
        task.setInput(taskInput);
        return task;
    }

    private static Object parseToolArguments(ToolCall toolCall, String toolName, String toolKind) {
        try {
            return JsonUtils.safeJsonLoads(toolCall.getArguments());
        } catch (RuntimeException exception) {
            String message;
            if (UserConfig.isSensitive()) {
                LOG.error("LLM Agent parse tool call {}'s arguments error", toolKind);
                message = "LLM-generated " + toolKind + " arguments are invalid";
            } else {
                LOG.error("LLM Agent parse tool call {}({})'s arguments error: {}",
                        toolKind, toolName, toolCall.getArguments());
                message = "LLM-generated " + toolKind + " (" + toolName + ") arguments are invalid: "
                        + toolCall.getArguments();
            }
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_TOOL_EXECUTION_PROCESS_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", message)
            );
        }
    }

    private static List<? extends WorkflowView> safeWorkflows(AgentConfigView config) {
        List<? extends WorkflowView> workflows = config.workflows();
        return workflows == null ? List.of() : workflows;
    }

    private static List<? extends PluginView> safePlugins(AgentConfigView config) {
        List<? extends PluginView> plugins = config.plugins();
        return plugins == null ? List.of() : plugins;
    }

    private static Map<String, Object> deepCopyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), deepCopyValue(value)));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>();
            for (Object item : collection) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private static boolean isPythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static Object extractTaskOutput(Object taskResult) {
        if (taskResult == null) {
            return null;
        }
        Object output = invokeNoArg(taskResult, "getOutput");
        if (output != Unreadable.INSTANCE) {
            return output;
        }
        output = readField(taskResult, "output");
        if (output != Unreadable.INSTANCE) {
            return output;
        }
        return taskResult;
    }

    private static Object readWorkflowResult(Object toolResult) {
        if (toolResult == null) {
            return Unreadable.INSTANCE;
        }
        Object result = invokeNoArg(toolResult, "getResult");
        if (result != Unreadable.INSTANCE) {
            return result;
        }
        return readField(toolResult, "result");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return Unreadable.INSTANCE;
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return Unreadable.INSTANCE;
        }
    }

    private static int pythonSliceStart(int size, int startIndex) {
        if (startIndex < 0) {
            return Math.max(0, size + startIndex);
        }
        return Math.min(size, startIndex);
    }

    private enum Unreadable {
        INSTANCE
    }

    /**
     * Narrow agent configuration surface consumed by message-handler utilities.
     *
     * <p>Mirrors Python's {@code AgentConfig} fields used in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public interface AgentConfigView {
        Object promptTemplate();

        List<? extends WorkflowView> workflows();

        List<? extends PluginView> plugins();

        ConstrainView constrain();
    }

    /**
     * Narrow workflow surface consumed by tool-call task creation.
     *
     * <p>Mirrors Python's workflow access in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public interface WorkflowView {
        String id();

        String version();

        String name();
    }

    /**
     * Narrow plugin surface consumed by tool-call task creation.
     *
     * <p>Mirrors Python's plugin access in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public interface PluginView {
        String name();
    }

    /**
     * Narrow constrain surface consumed by chat-history slicing.
     *
     * <p>Mirrors Python's {@code config.constrain} access in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public interface ConstrainView {
        int reservedMaxChatRounds();
    }

    /**
     * Optional workflow-output adapter for Java callers with a concrete translated type.
     *
     * <p>Mirrors Python's {@code WorkflowOutput} use in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public interface WorkflowOutputView {
        Object result();
    }

    /**
     * Small immutable agent-config value for tests and simple callers.
     *
     * <p>Mirrors Python's {@code AgentConfig} fields used in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public record SimpleAgentConfig(Object promptTemplate, List<Workflow> workflows, List<Plugin> plugins,
                                    Constrain constrain) implements AgentConfigView {
        public SimpleAgentConfig {
            workflows = workflows == null ? List.of() : List.copyOf(workflows);
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
            constrain = constrain == null ? new Constrain(0) : constrain;
        }
    }

    /**
     * Small immutable workflow value for tests and simple callers.
     *
     * <p>Mirrors Python's workflow fields used in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public record Workflow(String id, String version, String name) implements WorkflowView {
        public Workflow {
            id = id == null ? "" : id;
            version = version == null ? "" : version;
            name = name == null ? "" : name;
        }
    }

    /**
     * Small immutable plugin value for tests and simple callers.
     *
     * <p>Mirrors Python's plugin fields used in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public record Plugin(String name) implements PluginView {
        public Plugin {
            name = name == null ? "" : name;
        }
    }

    /**
     * Small immutable constrain value for tests and simple callers.
     *
     * <p>Mirrors Python's constrain fields used in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public record Constrain(int reservedMaxChatRounds) implements ConstrainView {
    }
}
