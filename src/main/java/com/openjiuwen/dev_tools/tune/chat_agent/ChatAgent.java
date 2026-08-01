/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.LegacyBaseAgent;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Simplest LLM chat single-agent.
 *
 * <p>Mirrors Python's {@code ChatAgent} in
 * {@code openjiuwen/dev_tools/tune/chat_agent/chat_agent.py}.</p>
 */
public class ChatAgent extends LegacyBaseAgent {
    private static final String DEFAULT_SESSION_ID = "default_session";
    private static final String CONVERSATION_ID = "conversation_id";
    private static final String LLM_CALL_KEY = "llm_call";

    private final ChatAgentConfig agentConfig;
    private final LLMCall llmCall;
    private final AgentSession defaultSession;

    public ChatAgent(ChatAgentConfig agentConfig) {
        super(Objects.requireNonNull(agentConfig, "agentConfig"));
        this.agentConfig = agentConfig;
        LLMCallConfig llmConfig = Objects.requireNonNull(agentConfig.getLlmCallConfig(), "agentConfig.model");
        ModelRequestConfig modelConfig = Objects.requireNonNull(llmConfig.getModel(), "agentConfig.model.model");
        this.llmCall = new LLMCall(
                modelConfig.getModelName(),
                initModel(modelConfig, llmConfig.getModelClient()),
                llmConfig.getSystemPrompt(),
                llmConfig.getUserPrompt(),
                llmConfig.isFreezeSystemPrompt(),
                llmConfig.isFreezeUserPrompt(),
                LLM_CALL_KEY
        );
        this.defaultSession = AgentSession.createAgentSession(DEFAULT_SESSION_ID, null, agentCard(agentConfig));
    }

    public static ChatAgentConfig createChatAgentConfig(String agentId,
                                                       String agentVersion,
                                                       String description,
                                                       LLMCallConfig model) {
        ChatAgentConfig config = new ChatAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setLlmCallConfig(model);
        return config;
    }

    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig, List<? extends Tool> tools) {
        ChatAgent agent = new ChatAgent(agentConfig);
        agent.addTools(tools == null ? List.of() : tools);
        return agent;
    }

    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig) {
        return createChatAgent(agentConfig, List.of());
    }

    protected Model initModel(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        return new Model(modelClientConfig, modelConfig);
    }

    @Override
    protected ContextEngine createContextEngine() {
        return new ContextEngine(new ContextEngineConfig());
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        MutableInputs mutableInputs = mutableInputs(inputs);
        AgentSessionApi agentSession = session == null ? defaultSession : session;
        ModelContext agentContext = getContextEngine().createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        List<ToolInfo> toolInfos = getTaggedToolInfos(toolIds());
        return llmCall.invoke(mutableInputs.values(), agentSession, agentContext.getMessages(null, true), toolInfos)
                .thenApply(ChatAgent::messageOutput);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        MutableInputs mutableInputs = mutableInputs(inputs);
        AgentSession ownedSession = null;
        AgentSessionApi agentSession = session;
        if (agentSession == null) {
            ownedSession = AgentSession.createAgentSession(
                    mutableInputs.sessionId(),
                    null,
                    new AgentCard(agentConfig.getId(), "", "")
            );
            ownedSession.preRun(Map.of("inputs", mutableInputs.values()));
            agentSession = ownedSession;
        }

        ModelContext agentContext = getContextEngine().createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        Iterator<AssistantMessageChunk> delegate = llmCall.stream(
                mutableInputs.values(),
                agentSession,
                agentContext.getMessages(null, true),
                getTaggedToolInfos(null)
        );
        return new ChatAgentStreamIterator(delegate, ownedSession);
    }

    public Map<String, LLMCall> getLlmCalls() {
        return Map.of(LLM_CALL_KEY, llmCall);
    }

    public Map<String, LLMCall> get_llm_calls() {
        return getLlmCalls();
    }

    public LegacyBaseAgent copy() {
        return createChatAgent(agentConfig);
    }

    public ChatAgentConfig getTypedAgentConfig() {
        return agentConfig;
    }

    private List<String> toolIds() {
        List<String> ids = new ArrayList<>();
        for (Object tool : getTools()) {
            if (tool instanceof Tool typedTool) {
                ids.add(typedTool.getCard().getId());
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    private List<ToolInfo> getTaggedToolInfos(List<String> toolIds) {
        if (getTools().isEmpty()) {
            return List.of();
        }
        return Runner.resourceMgr().getToolInfos(
                toolIds,
                null,
                List.of(agentConfig.getId()),
                TagMatchStrategy.ALL
        );
    }

    private static Map<String, Object> messageOutput(AssistantMessage result) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("output", result == null ? null : result.getContent());
        output.put("tool_calls", result == null ? null : result.getToolCalls());
        return output;
    }

    private static Map<String, Object> chunkOutput(AssistantMessageChunk result) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("output", result == null ? null : result.getContent());
        output.put("tool_calls", result == null ? null : result.getToolCalls());
        return output;
    }

    private static MutableInputs mutableInputs(Map<String, Object> inputs) {
        Map<String, Object> values = inputs == null ? new LinkedHashMap<>() : inputs;
        Object sessionIdValue;
        try {
            sessionIdValue = values.remove(CONVERSATION_ID);
        } catch (UnsupportedOperationException error) {
            values = new LinkedHashMap<>(inputs);
            sessionIdValue = values.remove(CONVERSATION_ID);
        }
        String sessionId = sessionIdValue == null ? DEFAULT_SESSION_ID : String.valueOf(sessionIdValue);
        return new MutableInputs(values, sessionId);
    }

    private static AgentCard agentCard(ChatAgentConfig config) {
        return new AgentCard(config.getId(), "", config.getDescription());
    }

    private record MutableInputs(Map<String, Object> values, String sessionId) {
    }

    private static final class ChatAgentStreamIterator implements Iterator<Object> {
        private final Iterator<AssistantMessageChunk> delegate;
        private final AgentSession ownedSession;
        private boolean closed;

        private ChatAgentStreamIterator(Iterator<AssistantMessageChunk> delegate, AgentSession ownedSession) {
            this.delegate = delegate;
            this.ownedSession = ownedSession;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                cleanupOwnedSession();
            }
            return hasNext;
        }

        @Override
        public Object next() {
            AssistantMessageChunk chunk = delegate.next();
            if (!delegate.hasNext()) {
                cleanupOwnedSession();
            }
            return chunkOutput(chunk);
        }

        private void cleanupOwnedSession() {
            if (closed || ownedSession == null) {
                return;
            }
            closed = true;
            ownedSession.closeStream();
            ownedSession.commit();
        }
    }
}
