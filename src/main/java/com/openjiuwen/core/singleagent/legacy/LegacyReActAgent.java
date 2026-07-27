/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.common.utils.MessageUtils;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
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
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
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
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new TypeReference<>() {
            };

    private Model llm;

    public LegacyReActAgent(LegacyReActAgentConfig agentConfig) {
        this(agentConfig, null, null);
    }

    public LegacyReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools) {
        super(agentConfig);
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

    public void setLlm(Model llm) {
        this.llm = llm;
    }

    @Override
    public void addTools(List<Tool> newTools) {
        super.addTools(newTools);
    }

    /**
     * Call LLM for reasoning.
     *
     * @param userInput   user input text
     * @param session     current session
     * @param isFirstCall whether this is the first call (adds user message to context)
     * @return LLM output as AssistantMessage
     */
    public AssistantMessage callModel(String userInput, AgentSessionApi session, boolean isFirstCall) {
        ensureContext(session);
        MessageUtils.ContextEnginePort contextPort = new ContextEngineMessagePort(getContextEngine());
        MessageUtils.SessionPort sessionPort = new SessionMessagePort(session.getSessionId());
        LegacyReActAgentConfig config = legacyConfig();

        if (isFirstCall) {
            MessageUtils.addUserMessage(userInput, contextPort, sessionPort).toCompletableFuture().join();
        }

        List<BaseMessage> chatHistory = MessageUtils.getChatHistory(contextPort, sessionPort,
                new ConfigMessagePort(config));

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
        messages.addAll(chatHistory);

        List<ToolInfo> tools = taggedToolInfos(config.getId());
        Model llmModel = getLlm();
        String modelName = modelName(config);

        AssistantMessage llmOutput;
        try {
            llmOutput = llmModel.invoke(messages, tools, null, null, null, null, modelName, null, null, null);
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed", e);
        }

        MessageUtils.addAiMessage(llmOutput, contextPort, sessionPort).toCompletableFuture().join();
        return llmOutput;
    }

    /**
     * Execute a tool call from LLM output.
     *
     * @param toolCall the tool call to execute
     * @param session  current session
     * @return tool execution result
     */
    public Object executeToolCall(ToolCall toolCall, AgentSessionApi session) {
        ensureContext(session);
        String toolName = toolCall == null ? null : toolCall.getName();
        Map<String, Object> toolArgs = parseToolArguments(toolCall == null ? null : toolCall.getArguments());
        Tool tool = resolveTool(toolName, session);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        try {
            Object result = tool.invoke(toolArgs);
            ToolMessage toolMessage = new ToolMessage(String.valueOf(result), toolCall.getId());
            MessageUtils.addToolMessage(
                    toolMessage,
                    new ContextEngineMessagePort(getContextEngine()),
                    new SessionMessagePort(session.getSessionId())
            ).toCompletableFuture().join();
            return result;
        } catch (Exception error) {
            throw new RuntimeException("Tool execution failed", error);
        }
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Session session) {
        Map<String, Object> safeInputs = new LinkedHashMap<>(inputs == null ? Map.of() : inputs);
        AgentSessionApi activeSession = session;
        boolean sessionCreated = false;
        if (activeSession == null) {
            String sessionId = Objects.toString(safeInputs.getOrDefault("conversation_id", "default_session"));
            AgentSession agentSession =
                    AgentSession.createAgentSession(sessionId, null,
                            AgentCard.builder().id(legacyConfig().getId()).name(legacyConfig().getId())
                                    .description(legacyConfig().getDescription()).build());
            agentSession.preRun(safeInputs);
            activeSession = agentSession;
            sessionCreated = true;
        }
        return innerInvoke(activeSession, safeInputs, sessionCreated);
    }

    public Object innerInvoke(AgentSessionApi session, Map<String, Object> inputs, boolean sessionCreated) {
        ensureContext(session);
        Object result;
        try {
            String userInput = Objects.toString(inputs.getOrDefault("query", ""), "");
            if (userInput.isEmpty()) {
                result = new LinkedHashMap<>(Map.of(
                        "output", "No query provided",
                        "result_type", "error"
                ));
            } else {
                result = reactLoop(userInput, session, 0, true);
            }
        } catch (RuntimeException error) {
            throw error;
        }
        if (sessionCreated && session instanceof AgentSession) {
            ((AgentSession) session).postRun();
        }
        return result;
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        Object result = invoke(inputs, session);
        return result == null ? List.<Object>of().iterator() : List.of(result).iterator();
    }

    /**
     * Create a LegacyReActAgentConfig.
     *
     * @param agentId       agent identifier
     * @param agentVersion  agent version
     * @param description   agent description
     * @param model         model configuration
     * @param promptTemplate prompt template entries
     * @return a new LegacyReActAgentConfig
     */
    public static LegacyReActAgentConfig createReActAgentConfig(String agentId,
                                                                 String agentVersion,
                                                                 String description,
                                                                 ModelConfig model,
                                                                 List<Map<String, Object>> promptTemplate) {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setModel(model);
        config.setPromptTemplate(promptTemplate != null ? promptTemplate : List.of());
        return config;
    }

    // ======================== Private helpers ========================

    private Object reactLoop(String userInput, AgentSessionApi session, int iteration, boolean isFirstCall) {
        ConstrainConfig constrain = legacyConfig().getConstrain();
        int maxIteration = constrain != null ? constrain.getMaxIteration() : ConstrainConfig.DEFAULT_MAX_ITERATION;
        if (iteration >= maxIteration) {
            return new LinkedHashMap<>(Map.of(
                    "output", "Exceeded max iteration",
                    "result_type", "error"
            ));
        }
        AssistantMessage llmOutput = callModel(userInput, session, isFirstCall);
        List<ToolCall> toolCalls = llmOutput.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return new LinkedHashMap<>(Map.of(
                    "output", Objects.toString(llmOutput.getContent(), ""),
                    "result_type", "answer"
            ));
        }
        for (ToolCall toolCall : toolCalls) {
            executeToolCall(toolCall, session);
        }
        return reactLoop(userInput, session, iteration + 1, false);
    }

    private static final String DEFAULT_CONTEXT_ID = "default_context_id";

    private void ensureContext(AgentSessionApi session) {
        String sessionId = session != null ? session.getSessionId() : "default_session_id";
        Object sessionObj = session instanceof Session ? (Session) session : session;
        getContextEngine().createContext(DEFAULT_CONTEXT_ID, sessionObj);
    }

    private Tool resolveTool(String toolName, AgentSessionApi session) {
        if (toolName == null || toolName.isEmpty()) {
            return null;
        }
        for (Tool candidate : this.tools) {
            if (candidate != null && candidate.getCard() != null
                    && (toolName.equals(candidate.getCard().getId())
                    || toolName.equals(candidate.getCard().getName()))) {
                return candidate;
            }
        }
        try {
            Object found = Runner.resourceMgr().getTool(toolName);
            if (found instanceof Tool) {
                return (Tool) found;
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<ToolInfo> taggedToolInfos(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        try {
            return Runner.resourceMgr().getToolInfos(null, null, agentId, TagMatchStrategy.ALL);
        } catch (RuntimeException ignored) {
            return List.of();
        }
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
        BaseModelInfo modelInfo = model.getModelInfo() != null ? model.getModelInfo() : BaseModelInfo.builder().build();
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
                .headers(modelInfo.getHeaders())
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

    // ======================== Port adapters ========================

    private record SessionMessagePort(String sessionId) implements MessageUtils.SessionPort {
        @Override
        public String getSessionId() {
            return sessionId;
        }
    }

    private record ContextEngineMessagePort(ContextEngine engine) implements MessageUtils.ContextEnginePort {
        @Override
        public MessageUtils.AgentContextPort getContext(String sessionId) {
            return new ModelContextMessagePort(engine.getContext(DEFAULT_CONTEXT_ID, sessionId));
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
            context.addMessages(message);
            return CompletableFuture.completedFuture(null);
        }
    }

    private record ConfigMessagePort(LegacyReActAgentConfig config) implements MessageUtils.AgentConfigView {
        @Override
        public MessageUtils.ConstrainView constrain() {
            ConstrainConfig constrain = config.getConstrain();
            return () -> constrain == null ? 10
                    : constrain.getReservedMaxChatRounds();
        }
    }
}
