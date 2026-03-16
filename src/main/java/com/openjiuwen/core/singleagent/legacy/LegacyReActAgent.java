/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.MessageUtils;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Backward-compatible wrapper over the modern ReActAgent.
 */
public class LegacyReActAgent extends BaseAgent {

    private final com.openjiuwen.core.singleagent.agents.ReActAgent delegate;

    public LegacyReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools) {
        super(agentConfig);
        this.delegate = new com.openjiuwen.core.singleagent.agents.ReActAgent(
                AgentCard.builder()
                        .id(agentConfig.getId())
                        .name(agentConfig.getId())
                        .description(agentConfig.getDescription())
                        .build()
        );
        this.delegate.configure(toModernConfig(agentConfig));
        if (tools != null) {
            addTools(tools);
        }
        if (workflows != null) {
            addWorkflows(workflows);
        }
    }

    public LegacyReActAgent(LegacyReActAgentConfig agentConfig) {
        this(agentConfig, null, null);
    }

    @Override
    public void addTools(List<Tool> newTools) {
        super.addTools(newTools);
        if (newTools != null) {
            for (Tool tool : newTools) {
                delegate.getAbilityManager().add(tool.getCard());
            }
        }
    }

    @Override
    public void addWorkflows(List<Workflow> newWorkflows) {
        super.addWorkflows(newWorkflows);
        if (newWorkflows != null) {
            for (Workflow workflow : newWorkflows) {
                delegate.getAbilityManager().add(workflow.getCard());
            }
        }
    }

    /**
     * Call LLM for reasoning.
     *
     * <p>Mirrors Python's {@code LegacyReActAgent.call_model(user_input, session, is_first_call)}.</p>
     *
     * @param userInput   user input text
     * @param session     current session
     * @param isFirstCall whether this is the first call (adds user message to context)
     * @return LLM output as AssistantMessage
     */
    public AssistantMessage callModel(String userInput, Session session, boolean isFirstCall) {
        if (isFirstCall) {
            MessageUtils.addUserMessage(userInput, getContextEngine(), session);
        }

        LegacyReActAgentConfig legacyConfig = (LegacyReActAgentConfig) getAgentConfig();
        int maxRounds = legacyConfig.getConstrain() != null
                ? legacyConfig.getConstrain().getReservedMaxChatRounds() : 10;
        List<com.openjiuwen.core.foundation.llm.schema.BaseMessage> chatHistory =
                MessageUtils.getChatHistory(getContextEngine(), session, maxRounds);

        List<Map<String, Object>> messages = new java.util.ArrayList<>();

        // Build system prompt from template
        if (legacyConfig.getPromptTemplate() != null && !legacyConfig.getPromptTemplate().isEmpty()) {
            for (Map<String, String> prompt : legacyConfig.getPromptTemplate()) {
                messages.add(new java.util.LinkedHashMap<>(prompt));
            }
        }

        // Add chat history
        for (com.openjiuwen.core.foundation.llm.schema.BaseMessage msg : chatHistory) {
            Map<String, Object> msgMap = new java.util.LinkedHashMap<>();
            if (msg.getRole() != null) {
                msgMap.put("role", msg.getRole());
            }
            if (msg.getContent() != null) {
                msgMap.put("content", msg.getContent());
            }
            messages.add(msgMap);
        }

        // Get tools
        List<ToolInfo> toolInfos = com.openjiuwen.core.runner.Runner.resourceMgr()
                .getToolInfos(null, null, legacyConfig.getId(),
                        com.openjiuwen.core.runner.resourcemanager.ResourceMgr.TagMatchStrategy.ALL);

        // Get LLM and invoke
        Model llm = delegate.getLlm();
        AssistantMessage llmOutput;
        try {
            llmOutput = llm.invoke(
                    messages, toolInfos, null, null,
                    null, null, null, null, null, null
            );
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed", e);
        }

        // Store AI message
        MessageUtils.addAiMessage(llmOutput, getContextEngine(), session);
        return llmOutput;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Session session) {
        AgentSessionApi effectiveSession = toAgentSession(inputs, session);
        try {
            return delegate.invoke(inputs, effectiveSession);
        } finally {
            if (session == null) {
                effectiveSession.postRun();
            }
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        AgentSessionApi effectiveSession = toAgentSession(inputs, session);
        Iterator<Object> iterator = delegate.stream(inputs, effectiveSession, List.of(StreamMode.OUTPUT));
        if (session == null) {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    boolean hasNext = iterator.hasNext();
                    if (!hasNext) {
                        effectiveSession.postRun();
                    }
                    return hasNext;
                }

                @Override
                public Object next() {
                    return iterator.next();
                }
            };
        }
        return iterator;
    }

    public static LegacyReActAgentConfig createReActAgentConfig(String agentId,
                                                                String agentVersion,
                                                                String description,
                                                                ModelConfig model,
                                                                List<Map<String, String>> promptTemplate) {
        return LegacyReActAgentConfig.builder()
                .id(agentId)
                .version(agentVersion)
                .description(description)
                .model(model)
                .promptTemplate(promptTemplate != null ? promptTemplate : List.of())
                .build();
    }

    private static ReActAgentConfig toModernConfig(LegacyReActAgentConfig legacyConfig) {
        ReActAgentConfig config = ReActAgentConfig.builder()
                .memScopeId(legacyConfig.getMemoryScopeId())
                .promptTemplateName(legacyConfig.getPromptTemplateName())
                .promptTemplate(legacyConfig.getPromptTemplate())
                .maxIterations(legacyConfig.getConstrain() != null ? legacyConfig.getConstrain().getMaxIteration() : 5)
                .contextEngineConfig(ContextEngineConfig.builder()
                        .defaultWindowRoundNum(legacyConfig.getConstrain() != null
                                ? legacyConfig.getConstrain().getReservedMaxChatRounds() : 10)
                        .maxContextMessageNum((legacyConfig.getConstrain() != null
                                ? legacyConfig.getConstrain().getReservedMaxChatRounds() : 10) * 2)
                        .build())
                .build();

        ModelConfig model = legacyConfig.getModel();
        if (model != null) {
            BaseModelInfo modelInfo = model.modelInfo() != null ? model.modelInfo() : new BaseModelInfo();
            config.setModelProvider(model.modelProvider());
            config.setModelName(modelInfo.getModelName());
            config.setApiKey(modelInfo.getApiKey());
            config.setApiBase(modelInfo.getApiBase());
            config.setModelClientConfig(ModelClientConfig.builder()
                    .clientProvider(model.modelProvider())
                    .apiKey(modelInfo.getApiKey() != null ? modelInfo.getApiKey() : "")
                    .apiBase(modelInfo.getApiBase() != null ? modelInfo.getApiBase() : "")
                    .verifySsl(false)
                    .build());
            config.setModelConfigObj(ModelRequestConfig.builder()
                    .modelName(modelInfo.getModelName())
                    .temperature(modelInfo.getTemperature())
                    .topP(modelInfo.getTopP())
                    .build());
        }
        return config;
    }

    private AgentSessionApi toAgentSession(Map<String, Object> inputs, Session session) {
        if (session instanceof AgentSessionApi agentSessionApi) {
            return agentSessionApi;
        }
        String sessionId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
        AgentSessionApi agentSessionApi = AgentSessionApi.create(sessionId, null, delegate.getCard());
        agentSessionApi.preRun(inputs);
        return agentSessionApi;
    }
}
