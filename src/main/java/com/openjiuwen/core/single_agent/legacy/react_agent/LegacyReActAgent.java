/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy.react_agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.common.utils.MessageUtils;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.agent.BaseAgent;
import com.openjiuwen.core.single_agent.legacy.config.ConstrainConfig;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Legacy ReAct agent compatibility implementation.
 *
 * <p>Mirrors Python's {@code LegacyReActAgent} in
 * {@code openjiuwen/core/single_agent/legacy/react_agent.py}.</p>
 */
public class LegacyReActAgent extends BaseAgent {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ResourceMgr FALLBACK_RESOURCE_MGR = new ResourceMgr();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new TypeReference<>() {
            };

    private Model llm;

    public LegacyReActAgent(LegacyReActAgentConfig agentConfig) {
        this(agentConfig, null, null);
    }

    public LegacyReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools) {
        super(Objects.requireNonNull(agentConfig, "agentConfig"));
        if (tools != null) {
            addTools(tools);
        }
        if (workflows != null) {
            addWorkflows(workflows);
        }
    }

    public LegacyReActAgentConfig legacyConfig() {
        return (LegacyReActAgentConfig) getAgentConfig();
    }

    public Model getLlm() {
        if (llm == null) {
            llm = buildModel(legacyConfig());
        }
        return llm;
    }

    public Model _get_llm() {
        return getLlm();
    }

    public void setLlm(Model llm) {
        this.llm = llm;
    }

    public void set_llm(Model llm) {
        setLlm(llm);
    }

    @Override
    public void addTools(List<?> incomingTools) {
        super.addTools(incomingTools);
        if (incomingTools == null) {
            return;
        }
        String agentId = legacyConfig().getId();
        for (Object tool : incomingTools) {
            if (tool instanceof Tool typedTool) {
                try {
                    resourceMgr().addTool(typedTool, List.of(agentId), true);
                } catch (RuntimeException ignored) {
                    // The base class already registered the tool globally; tagged registration is best effort.
                }
            }
        }
    }

    public CompletionStage<AssistantMessage> callModel(String userInput, AgentSessionApi session,
                                                       boolean isFirstCall) {
        ensureContext(session);
        MessageUtils.ContextEnginePort contextPort = new ContextEngineMessagePort(getContextEngine());
        MessageUtils.SessionPort sessionPort = new SessionMessagePort(session.getSessionId());
        LegacyReActAgentConfig config = legacyConfig();

        CompletionStage<Void> initialStage = isFirstCall
                ? MessageUtils.addUserMessage(userInput, contextPort, sessionPort)
                : CompletableFuture.completedFuture(null);

        return initialStage.thenCompose(ignored -> {
            List<BaseMessage> messages = new ArrayList<>();
            try {
                messages.addAll(promptTemplateMessages(config.getPromptTemplate()));
            } catch (RuntimeException error) {
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_PROMPT_PARAM_ERROR,
                        null,
                        null,
                        error,
                        Map.of("error_msg", error.getMessage())
                );
            }
            messages.addAll(MessageUtils.getChatHistory(contextPort, sessionPort, new ConfigMessagePort(config)));

            List<ToolInfo> tools = taggedToolInfos(config.getId());
            ModelInvokeOptions options = ModelInvokeOptions.builder()
                    .tools(tools)
                    .model(modelName(config))
                    .build();
            return getLlm().invoke(messages, options).thenApply(output -> {
                MessageUtils.addAiMessage(output, contextPort, sessionPort).toCompletableFuture().join();
                return output;
            });
        });
    }

    public CompletionStage<AssistantMessage> call_model(String userInput, AgentSessionApi session,
                                                        boolean isFirstCall) {
        return callModel(userInput, session, isFirstCall);
    }

    public CompletionStage<Object> executeToolCall(ToolCall toolCall, AgentSessionApi session) {
        ensureContext(session);
        String toolName = toolCall == null ? null : toolCall.getName();
        Map<String, Object> toolArgs = parseToolArguments(toolCall == null ? null : toolCall.getArguments());
        Tool tool = resolveTool(toolName, session);
        if (tool == null) {
            return failedStage(new IllegalArgumentException("Tool not found: " + toolName));
        }

        try {
            Object result = tool.invoke(toolArgs);
            ToolMessage toolMessage = new ToolMessage(String.valueOf(result), toolCall.getId());
            MessageUtils.addToolMessage(
                    toolMessage,
                    new ContextEngineMessagePort(getContextEngine()),
                    new SessionMessagePort(session.getSessionId())
            ).toCompletableFuture().join();
            return CompletableFuture.completedFuture(result);
        } catch (Exception error) {
            return failedStage(error);
        }
    }

    public CompletionStage<Object> _execute_tool_call(ToolCall toolCall, AgentSessionApi session) {
        return executeToolCall(toolCall, session);
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        Map<String, Object> safeInputs = new LinkedHashMap<>(inputs == null ? Map.of() : inputs);
        AgentSessionApi activeSession = session;
        boolean sessionCreated = false;
        if (activeSession == null) {
            String sessionId = Objects.toString(safeInputs.getOrDefault("conversation_id", "default_session"));
            AgentSession agentSession = AgentSession.createAgentSession(sessionId, null, agentCard());
            agentSession.preRun(Map.of("inputs", safeInputs));
            activeSession = agentSession;
            sessionCreated = true;
        }
        AgentSessionApi finalSession = activeSession;
        boolean finalSessionCreated = sessionCreated;
        return innerInvoke(finalSession, safeInputs, finalSessionCreated);
    }

    public CompletionStage<Object> invoke(Map<String, Object> inputs) {
        return invoke(inputs, null);
    }

    public CompletionStage<Object> innerInvoke(AgentSessionApi session, Map<String, Object> inputs,
                                               boolean sessionCreated) {
        ensureContext(session);
        CompletionStage<Object> resultStage;
        try {
            String userInput = Objects.toString(inputs.getOrDefault("query", ""), "");
            if (userInput.isEmpty()) {
                resultStage = CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                        "output", "No query provided",
                        "result_type", "error"
                )));
            } else {
                resultStage = reactLoop(userInput, session, 0, true);
            }
        } catch (RuntimeException error) {
            resultStage = failedStage(error);
        }
        if (!sessionCreated) {
            return resultStage;
        }
        return resultStage.whenComplete((ignored, error) -> closeStreamAndCommit(session));
    }

    public CompletionStage<Object> _inner_invoke(AgentSessionApi session, Map<String, Object> inputs,
                                                 boolean sessionCreated) {
        return innerInvoke(session, inputs, sessionCreated);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                   List<StreamMode> streamModes) {
        Map<String, Object> safeInputs = new LinkedHashMap<>(inputs == null ? Map.of() : inputs);
        AgentSessionApi activeSession = session;
        boolean needCleanup = false;
        boolean ownStream = false;
        if (activeSession == null) {
            String sessionId = Objects.toString(safeInputs.getOrDefault("conversation_id", "default_session"));
            AgentSession agentSession = AgentSession.createAgentSession(sessionId, null, agentCard());
            agentSession.preRun(Map.of("inputs", safeInputs));
            activeSession = agentSession;
            needCleanup = true;
            ownStream = true;
        } else if (!getTools().isEmpty()) {
            addTools(getTools());
        }
        return innerStream(activeSession, safeInputs, needCleanup, ownStream);
    }

    public Iterator<Object> stream(Map<String, Object> inputs) {
        return stream(inputs, null, List.of(StreamMode.OUTPUT));
    }

    public Iterator<Object> innerStream(AgentSessionApi session, Map<String, Object> inputs,
                                        boolean needCleanup, boolean ownStream) {
        ensureContext(session);
        Object finalResult = null;
        try {
            finalResult = invoke(inputs, session).toCompletableFuture().join();
            session.writeStream(new OutputSchema(
                    "answer",
                    0,
                    new LinkedHashMap<>(Map.of(
                            "output", finalResult,
                            "result_type", "answer"
                    ))
            ));
        } catch (RuntimeException ignored) {
            // Python logs and lets cleanup run; the Java port preserves the non-throwing stream path.
        } finally {
            if (needCleanup) {
                closeStreamAndCommit(session);
            }
        }
        if (ownStream) {
            return session.streamIterator();
        }
        return finalResult == null ? List.<Object>of().iterator() : List.of(finalResult).iterator();
    }

    public Iterator<Object> _inner_stream(AgentSessionApi session, Map<String, Object> inputs,
                                          boolean needCleanup, boolean ownStream) {
        return innerStream(session, inputs, needCleanup, ownStream);
    }

    public static LegacyReActAgentConfig createReactAgentConfig(String agentId,
                                                                String agentVersion,
                                                                String description,
                                                                ModelConfig model,
                                                                List<Map<String, Object>> promptTemplate) {
        return LegacyReActAgentFactory.createReactAgentConfig(
                agentId,
                agentVersion,
                description,
                model,
                promptTemplate
        );
    }

    public static LegacyReActAgentConfig create_react_agent_config(String agentId,
                                                                   String agentVersion,
                                                                   String description,
                                                                   ModelConfig model,
                                                                   List<Map<String, Object>> promptTemplate) {
        return createReactAgentConfig(agentId, agentVersion, description, model, promptTemplate);
    }

    private CompletionStage<Object> reactLoop(String userInput, AgentSessionApi session,
                                              int iteration, boolean isFirstCall) {
        int maxIteration = legacyConfig().getConstrain().getMaxIteration();
        if (iteration >= maxIteration) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                    "output", "Exceeded max iteration",
                    "result_type", "error"
            )));
        }
        return callModel(userInput, session, isFirstCall).thenCompose(llmOutput -> {
            List<ToolCall> toolCalls = llmOutput.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                        "output", Objects.toString(llmOutput.getContent(), ""),
                        "result_type", "answer"
                )));
            }
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (ToolCall toolCall : toolCalls) {
                chain = chain.thenCompose(ignored -> executeToolCall(toolCall, session).thenApply(result -> null));
            }
            return chain.thenCompose(ignored -> reactLoop(userInput, session, iteration + 1, false));
        });
    }

    private void ensureContext(AgentSessionApi session) {
        getContextEngine().createContext(
                ContextEngine.DEFAULT_CONTEXT_ID,
                new ContextSessionPort(session.getSessionId())
        );
    }

    private AgentCard agentCard() {
        LegacyReActAgentConfig config = legacyConfig();
        return new AgentCard(config.getId(), config.getId(), config.getDescription());
    }

    private Tool resolveTool(String toolName, AgentSessionApi session) {
        if (toolName == null || toolName.isEmpty()) {
            return null;
        }
        List<Tool> taggedTools = taggedTools(legacyConfig().getId(), session);
        for (Tool candidate : taggedTools) {
            if (candidate != null && candidate.getCard() != null
                    && (toolName.equals(candidate.getCard().getId())
                    || toolName.equals(candidate.getCard().getName()))) {
                return candidate;
            }
        }
        return resourceMgr().getTool(toolName);
    }

    private static List<BaseMessage> promptTemplateMessages(List<Map<String, Object>> promptTemplate) {
        List<BaseMessage> messages = new ArrayList<>();
        for (Map<String, Object> prompt : promptTemplate == null ? List.<Map<String, Object>>of() : promptTemplate) {
            if (prompt == null) {
                continue;
            }
            messages.add(messageFromMap(prompt));
        }
        return messages;
    }

    private static BaseMessage messageFromMap(Map<String, Object> prompt) {
        String role = Objects.toString(prompt.getOrDefault("role", "user"));
        Object content = prompt.getOrDefault("content", "");
        String name = prompt.get("name") == null ? null : String.valueOf(prompt.get("name"));
        BaseMessage message;
        if ("system".equals(role)) {
            message = new SystemMessage(Objects.toString(content, ""), name);
        } else if ("assistant".equals(role)) {
            message = AssistantMessage.builder().content(content).name(name).build();
        } else if ("tool".equals(role)) {
            message = new ToolMessage(
                    Objects.toString(content, ""),
                    Objects.toString(prompt.get("tool_call_id"), null),
                    name
            );
        } else if ("user".equals(role)) {
            message = new UserMessage(Objects.toString(content, ""), name);
        } else {
            message = new BaseMessage(role, content);
            message.setName(name);
        }
        return message;
    }

    private static Map<String, Object> parseToolArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(rawArguments, STRING_OBJECT_MAP);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static Model buildModel(LegacyReActAgentConfig config) {
        ModelConfig model = config.getModel();
        if (model == null) {
            throw new IllegalStateException("model is required for LegacyReActAgent");
        }
        BaseModelInfo modelInfo = model.getModelInfo() == null ? new BaseModelInfo() : model.getModelInfo();
        String apiKey = nullToEmpty(modelInfo.getApiKey());
        String apiBase = nullToEmpty(modelInfo.getApiBase());
        String provider = nullToEmpty(model.getModelProvider());
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId(HashUtil.generateKey(apiKey, apiBase, provider))
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(false)
                .sslCert(null)
                .customHeaders(modelInfo.getCustomHeaders())
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelInfo.getModelName())
                .temperature(modelInfo.getTemperature())
                .topP(modelInfo.getTopP())
                .extraFields(modelInfo.getExtraFields() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(modelInfo.getExtraFields()))
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private static ResourceMgr resourceMgr() {
        try {
            Object result = Class.forName("com.openjiuwen.core.runner.Runner")
                    .getMethod("getResourceMgr")
                    .invoke(null);
            if (result instanceof ResourceMgr manager) {
                return manager;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return FALLBACK_RESOURCE_MGR;
    }

    private static List<ToolInfo> taggedToolInfos(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        try {
            return resourceMgr().getToolInfos(null, null, List.of(agentId), TagMatchStrategy.ALL);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<Tool> taggedTools(String agentId, AgentSessionApi session) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        try {
            return resourceMgr().getToolsByTag(List.of(agentId), TagMatchStrategy.ALL, session);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static String modelName(LegacyReActAgentConfig config) {
        ModelConfig model = config.getModel();
        if (model == null || model.getModelInfo() == null) {
            return null;
        }
        return model.getModelInfo().getModelName();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void closeStreamAndCommit(AgentSessionApi session) {
        invokeNoArg(session, "closeStream");
        invokeNoArg(session, "close_stream");
        invokeNoArg(session, "commit");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (IllegalAccessException | NoSuchMethodException ignored) {
            return null;
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static <T> CompletionStage<T> failedStage(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private record SessionMessagePort(String sessionId) implements MessageUtils.SessionPort {
        @Override
        public String getSessionId() {
            return sessionId;
        }
    }

    private record ContextSessionPort(String sessionId) implements ContextEngine.SessionPort {
        @Override
        public String getSessionId() {
            return sessionId;
        }
    }

    private record ContextEngineMessagePort(ContextEngine engine) implements MessageUtils.ContextEnginePort {
        @Override
        public MessageUtils.AgentContextPort getContext(String sessionId) {
            return new ModelContextMessagePort(engine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, sessionId));
        }

        @Override
        public MessageUtils.AgentContextPort getContext(String contextId, String sessionId) {
            return new ModelContextMessagePort(engine.getContext(contextId, sessionId));
        }
    }

    private record ModelContextMessagePort(ModelContext context) implements MessageUtils.AgentContextPort {
        @Override
        public List<BaseMessage> getMessages() {
            return context == null ? List.of() : context.getMessages(null, true);
        }

        @Override
        public List<BaseMessage> getMessages(int size) {
            return context == null ? List.of() : context.getMessages(size, true);
        }

        @Override
        public CompletionStage<Void> addMessages(BaseMessage message) {
            if (context == null) {
                return CompletableFuture.completedFuture(null);
            }
            return context.addMessages(message).thenApply(ignored -> null);
        }
    }

    private record ConfigMessagePort(LegacyReActAgentConfig config) implements MessageUtils.AgentConfigView {
        @Override
        public MessageUtils.ConstrainView constrain() {
            ConstrainConfig constrain = config.getConstrain();
            return () -> constrain == null ? 10 : constrain.getReservedMaxChatRounds();
        }
    }
}
