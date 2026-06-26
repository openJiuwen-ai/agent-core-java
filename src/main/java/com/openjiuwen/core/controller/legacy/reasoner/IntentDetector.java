/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.controller.legacy.config.IntentDetectionConfig;
import com.openjiuwen.core.controller.legacy.constants.IntentDetectionConstants;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskInput;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.AgentSessionApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Intent detection module for message intent recognition and task generation.
 *
 * <p>Mirrors Python's {@code IntentDetector} in
 * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
 */
public class IntentDetector {

    private static final Logger LOG = LoggerFactory.getLogger(IntentDetector.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_CODE_FENCE = Pattern.compile("^\\s*```json\\s*|\\s*```\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRIPLE_QUOTE_JSON_FENCE = Pattern.compile("^\\s*'''json\\s*|\\s*'''\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final IntentDetectionConfig intentConfig;
    private final AgentConfigView agentConfig;
    private final ContextEngine contextEngine;
    private final AgentSessionApi session;
    private final ChatHistoryProvider chatHistoryProvider;
    private final LlmInvoker llmInvoker;
    private final SecureRandom secureRandom;

    public IntentDetector(IntentDetectionConfig intentConfig, AgentConfigView agentConfig, ContextEngine contextEngine,
                          AgentSessionApi session) {
        this(intentConfig, agentConfig, contextEngine, session, new ContextEngineChatHistoryProvider(),
                new ModelBackedLlmInvoker(), new SecureRandom());
    }

    public IntentDetector(IntentDetectionConfig intentConfig, AgentConfigView agentConfig, ContextEngine contextEngine,
                          AgentSessionApi session, ChatHistoryProvider chatHistoryProvider, LlmInvoker llmInvoker) {
        this(intentConfig, agentConfig, contextEngine, session, chatHistoryProvider, llmInvoker, new SecureRandom());
    }

    IntentDetector(IntentDetectionConfig intentConfig, AgentConfigView agentConfig, ContextEngine contextEngine,
                   AgentSessionApi session, ChatHistoryProvider chatHistoryProvider, LlmInvoker llmInvoker,
                   SecureRandom secureRandom) {
        this.intentConfig = intentConfig == null ? new IntentDetectionConfig() : intentConfig;
        this.agentConfig = Objects.requireNonNull(agentConfig, "agentConfig must not be null");
        this.contextEngine = contextEngine;
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.chatHistoryProvider = chatHistoryProvider == null
                ? new ContextEngineChatHistoryProvider()
                : chatHistoryProvider;
        this.llmInvoker = llmInvoker == null ? new ModelBackedLlmInvoker() : llmInvoker;
        this.secureRandom = secureRandom == null ? new SecureRandom() : secureRandom;
    }

    /**
     * Process event, detect intent and generate tasks.
     *
     * @param event input event
     * @return detected tasks
     */
    public CompletionStage<List<Task>> processMessage(Event event) {
        List<BaseMessage> llmInputs = prepareDetectionInput(event);
        String sessionId = session.getSessionId();
        LOG.info("[{}] <LLM Input>: {}", sessionId, llmInputs);

        return invokeLlmGetOutput(llmInputs)
                .thenApply(llmOutput -> {
                    LOG.info("[{}] <LLM Output>: {}", sessionId, llmOutput);
                    String detectedIntentId = parseIntentFromOutput(llmOutput);
                    return generateTasksFromIntent(detectedIntentId, event);
                });
    }

    List<Task> generateTasksFromIntent(String intentId, Event event) {
        List<Task> tasks = new ArrayList<>();
        String sessionId = session.getSessionId();
        String taskUniqueId = sessionId + "_intent_" + intentId + "_" + tokenHex4();

        if (IntentDetectionConstants.DEFAULT_CLASS.equals(intentId)) {
            return tasks;
        }

        List<? extends WorkflowView> workflows = safeWorkflows();
        if (workflows.isEmpty()) {
            TaskInput taskInput = new TaskInput(
                    intentId,
                    intentId,
                    event == null ? null : event.getContent()
            );
            tasks.add(createWorkflowTask(agentConfig.id(), taskUniqueId, taskInput));
            LOG.info("[{}] success to create task for intent (direct): {}", sessionId, intentId);
            return tasks;
        }

        for (WorkflowView workflow : workflows) {
            if (Objects.equals(workflow.id(), intentId)) {
                TaskInput taskInput = new TaskInput(
                        workflow.id(),
                        workflow.name(),
                        event == null ? null : event.getContent()
                );
                tasks.add(createWorkflowTask(agentConfig.id(), taskUniqueId, taskInput));
                LOG.info("[{}] success to create task for intent: {}", sessionId, intentId);
                break;
            }
        }
        return tasks;
    }

    String parseIntentFromOutput(String llmOutput) {
        String detectedIntentId = "";
        String sessionId = session.getSessionId();
        try {
            String cleaned = llmOutput == null ? "" : llmOutput.strip();
            cleaned = JSON_CODE_FENCE.matcher(cleaned).replaceAll("");
            cleaned = TRIPLE_QUOTE_JSON_FENCE.matcher(cleaned).replaceAll("");

            Map<String, Object> outputData = OBJECT_MAPPER.readValue(
                    cleaned,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            int detectedClassNumber = parseClassNumber(outputData.getOrDefault("result", ""));
            List<String> categoryList = safeCategoryList();
            if (detectedClassNumber <= 0 || detectedClassNumber > categoryList.size()) {
                LOG.warn("get unknown class");
            } else {
                String detectedIntentName = categoryList.get(detectedClassNumber - 1);
                List<? extends WorkflowView> workflows = safeWorkflows();
                if (workflows.isEmpty()) {
                    LOG.info("[{}] get intent (direct category): {}", sessionId, detectedIntentName);
                    return detectedIntentName;
                }
                for (WorkflowView workflow : workflows) {
                    String workflowLabel = workflow.description() == null || workflow.description().isEmpty()
                            ? workflow.name()
                            : workflow.description();
                    if (Objects.equals(workflowLabel, detectedIntentName)) {
                        detectedIntentId = workflow.id();
                        LOG.info("[{}] get intent: {}", sessionId, detectedIntentId);
                        break;
                    }
                }
                return detectedIntentId;
            }
        } catch (Exception e) {
            LOG.error("failed to parse JSON from LLM output, error: {}", e.getMessage());
            throw new IllegalArgumentException("failed to parse JSON from LLM output", e);
        }

        return IntentDetectionConstants.DEFAULT_CLASS;
    }

    CompletionStage<String> invokeLlmGetOutput(List<BaseMessage> llmInputs) {
        try {
            return llmInvoker.invoke(llmInputs, agentConfig.model(), session)
                    .handle((value, throwable) -> {
                        if (throwable != null) {
                            Throwable cause = unwrap(throwable);
                            throw new CompletionException(buildInvokeError(cause));
                        }
                        return value == null ? "" : value.strip();
                    });
        } catch (Exception e) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(buildInvokeError(e));
            return failed;
        }
    }

    List<BaseMessage> prepareDetectionInput(Event event) {
        String categories = IntStream.range(0, safeCategoryList().size())
                .mapToObj(index -> "分类" + (index + 1) + "：" + safeCategoryList().get(index))
                .collect(Collectors.joining("\n"));
        String categoryList = "分类0：意图不明\n" + categories;

        Map<String, Object> currentInputs = new LinkedHashMap<>();
        currentInputs.put(IntentDetectionConstants.USER_PROMPT, intentConfig.getUserPrompt());
        currentInputs.put(IntentDetectionConstants.CATEGORY_LIST, categoryList);
        currentInputs.put(IntentDetectionConstants.DEFAULT_CLASS, intentConfig.getDefaultClass());
        currentInputs.put(IntentDetectionConstants.ENABLE_HISTORY, intentConfig.isEnableHistory());
        currentInputs.put(IntentDetectionConstants.ENABLE_INPUT, intentConfig.isEnableInput());
        currentInputs.put(IntentDetectionConstants.EXAMPLE_CONTENT,
                String.join("\n\n", intentConfig.getExampleContent() == null
                        ? List.of()
                        : intentConfig.getExampleContent()));
        currentInputs.put(IntentDetectionConstants.CHAT_HISTORY_MAX_TURN, intentConfig.getChatHistoryMaxTurn());
        currentInputs.put(IntentDetectionConstants.CHAT_HISTORY, "");

        if (intentConfig.isEnableHistory()) {
            List<BaseMessage> chatHistory = chatHistoryProvider.getChatHistory(
                    contextEngine,
                    session,
                    intentConfig.getChatHistoryMaxTurn()
            );
            StringBuilder chatHistoryBuilder = new StringBuilder();
            for (BaseMessage history : chatHistory) {
                String roleName = IntentDetectionConstants.ROLE_MAP.getOrDefault(history.getRole(), "用户");
                chatHistoryBuilder.append(roleName)
                        .append(": ")
                        .append(history.getContentAsString())
                        .append('\n');
            }
            currentInputs.put(IntentDetectionConstants.CHAT_HISTORY, chatHistoryBuilder.toString());
        }

        if (intentConfig.isEnableInput()) {
            String query = event == null || event.getContent() == null ? "" : event.getContent().getQueryText();
            currentInputs.put(IntentDetectionConstants.INPUT, query);
        }

        PromptTemplate template = intentConfig.getIntentDetectionTemplate() == null
                ? IntentDetectionConfig.getDefaultTemplate()
                : intentConfig.getIntentDetectionTemplate();
        return template.format(currentInputs).toMessages();
    }

    private Task createWorkflowTask(String agentId, String taskUniqueId, TaskInput taskInput) {
        Task task = new Task();
        task.setAgentId(agentId);
        task.setTaskId(taskUniqueId);
        task.setTaskType(TaskType.WORKFLOW);
        task.setInput(taskInput);
        return task;
    }

    private List<String> safeCategoryList() {
        return intentConfig.getCategoryList() == null ? List.of() : intentConfig.getCategoryList();
    }

    private List<? extends WorkflowView> safeWorkflows() {
        return agentConfig.workflows() == null ? List.of() : agentConfig.workflows();
    }

    private String tokenHex4() {
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static int parseClassNumber(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private static RuntimeException buildInvokeError(Throwable cause) {
        String message = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
        return ErrorHelper.buildError(
                StatusCode.AGENT_CONTROLLER_INVOKE_CALL_FAILED,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    /**
     * Narrow view of the Python agent config fields consumed by this detector.
     *
     * <p>Mirrors Python's {@code self.agent_config} access in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    public interface AgentConfigView {
        String id();

        List<? extends WorkflowView> workflows();

        ModelConfig model();
    }

    /**
     * Narrow view of the Python workflow fields consumed by this detector.
     *
     * <p>Mirrors Python's workflow access in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    public interface WorkflowView {
        String id();

        String name();

        String description();
    }

    /**
     * Small Java adapter for tests and callers that need a concrete agent config value.
     *
     * <p>Mirrors Python's agent-config field usage in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    public record AgentConfig(String id, List<Workflow> workflows, ModelConfig model) implements AgentConfigView {
        public AgentConfig {
            id = id == null ? "" : id;
            workflows = workflows == null ? List.of() : List.copyOf(workflows);
        }
    }

    /**
     * Small Java adapter for tests and callers that need a concrete workflow value.
     *
     * <p>Mirrors Python's workflow field usage in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    public record Workflow(String id, String name, String description) implements WorkflowView {
        public Workflow {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
        }
    }

    /**
     * Provides chat history equivalent to {@code ReasonerUtils.get_chat_history}.
     *
     * <p>Mirrors Python's {@code ReasonerUtils.get_chat_history} dependency in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    @FunctionalInterface
    public interface ChatHistoryProvider {
        List<BaseMessage> getChatHistory(ContextEngine contextEngine, AgentSessionApi session, int chatHistoryMaxTurn);
    }

    /**
     * Invokes the LLM and returns stripped message content.
     *
     * <p>Mirrors Python's {@code _invoke_llm_get_output} model call in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    @FunctionalInterface
    public interface LlmInvoker {
        CompletionStage<String> invoke(List<BaseMessage> llmInputs, ModelConfig modelConfig, AgentSessionApi session);
    }

    /**
     * ContextEngine-backed chat-history provider.
     *
     * <p>Mirrors Python's {@code ReasonerUtils.get_chat_history} use in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    public static final class ContextEngineChatHistoryProvider implements ChatHistoryProvider {
        @Override
        public List<BaseMessage> getChatHistory(ContextEngine contextEngine, AgentSessionApi session,
                                                int chatHistoryMaxTurn) {
            if (contextEngine == null || session == null) {
                return List.of();
            }
            ModelContext context = contextEngine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId());
            if (context == null) {
                return List.of();
            }
            List<BaseMessage> contextMessages = context.getMessages(null, true);
            List<BaseMessage> messages = contextMessages == null ? List.of() : contextMessages;
            int maxMessages = Math.max(0, chatHistoryMaxTurn) * 2;
            if (maxMessages == 0 || messages.size() <= maxMessages) {
                return List.copyOf(messages);
            }
            return List.copyOf(messages.subList(messages.size() - maxMessages, messages.size()));
        }
    }

    /**
     * Model-backed default invoker equivalent to Python's {@code ReasonerUtils.get_model(...).invoke(...)} flow.
     *
     * <p>Mirrors Python's {@code _invoke_llm_get_output} in
     * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
     */
    public static final class ModelBackedLlmInvoker implements LlmInvoker {
        @Override
        public CompletionStage<String> invoke(List<BaseMessage> llmInputs, ModelConfig modelConfig,
                                              AgentSessionApi session) {
            if (modelConfig == null) {
                throw new IllegalArgumentException("model config is none");
            }
            BaseModelInfo modelInfo = modelConfig.getModelInfo() == null
                    ? new BaseModelInfo()
                    : modelConfig.getModelInfo();
            ModelClientConfig modelClientConfig = ModelClientConfig.builder()
                    .clientId(HashUtil.generateKey(
                            modelInfo.getApiKey(),
                            modelInfo.getApiBase(),
                            modelConfig.getModelProvider()
                    ))
                    .clientProvider(modelConfig.getModelProvider())
                    .apiKey(modelInfo.getApiKey())
                    .apiBase(modelInfo.getApiBase())
                    .timeout(modelInfo.getTimeout())
                    .verifySsl(false)
                    .sslCert(null)
                    .customHeaders(modelInfo.getCustomHeaders() == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(modelInfo.getCustomHeaders()))
                    .build();
            ModelRequestConfig modelRequestConfig = ModelRequestConfig.builder()
                    .modelName(modelInfo.getModelName())
                    .temperature(modelInfo.getTemperature())
                    .topP(modelInfo.getTopP())
                    .extraFields(modelInfo.getExtraFields() == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(modelInfo.getExtraFields()))
                    .build();
            ModelInvokeOptions options = ModelInvokeOptions.builder()
                    .model(modelInfo.getModelName())
                    .build();
            return new Model(modelClientConfig, modelRequestConfig)
                    .invoke(llmInputs, options)
                    .thenApply(ModelBackedLlmInvoker::contentOf);
        }

        private static String contentOf(AssistantMessage message) {
            return message == null ? "" : message.getContentAsString();
        }
    }
}
