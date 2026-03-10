/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * LLM Agent - ReAct style Agent based on ControllerAgent.
 *
 * <p>Core features:
 * <ol>
 *   <li>Inherits ControllerAgent, holds LlmController (via EventHandler)</li>
 *   <li>Supports LLM reasoning to generate task plans</li>
 *   <li>Supports multi-round conversations and task execution</li>
 *   <li>Optionally writes conversation messages to long-term memory</li>
 * </ol>
 *
 * <p>Mirrors Python's {@code LLMAgent} in {@code openjiuwen.core.application.llm_agent}.
 */
public class LlmAgent extends ControllerAgent {

    private final LlmAgentConfig agentConfig;
    private final LongTermMemory longTermMemoryInstance;
    private final boolean enableMemory;

    /**
     * Create LlmAgent with the given configuration.
     *
     * @param agentConfig the LLM agent configuration
     */
    public LlmAgent(LlmAgentConfig agentConfig) {
        super(buildAgentCard(agentConfig), new Controller(), buildControllerConfig(agentConfig));
        this.agentConfig = agentConfig;
        this.longTermMemoryInstance = LongTermMemory.getInstance();

        String memoryScopeId = agentConfig.getMemoryScopeId();
        this.enableMemory = memoryScopeId != null && !memoryScopeId.isEmpty()
                && (agentConfig.getAgentMemoryConfig().isEnableLongTermMem()
                || !agentConfig.getAgentMemoryConfig().getMemVariables().isEmpty());

        // Set up the LlmEventHandler on the controller
        LlmEventHandler eventHandler = new LlmEventHandler(agentConfig, getContextEngine());
        getController().setEventHandler(eventHandler);
    }

    @Override
    public ControllerOutput invoke(Object inputs, Session session) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }

        ControllerOutput result;
        try {
            result = super.invoke(inputs, effectiveSession);
        } finally {
            if (managedSession != null) {
                managedSession.postRun();
            }
        }

        if (enableMemory && inputs instanceof Map<?, ?> inputMap) {
            writeMessagesToMemoryAsync(inputMap, result);
        }

        return result;
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }

        Iterator<Object> streamIter = super.stream(inputs, effectiveSession, streamModes);
        List<String> answerOutputs = new ArrayList<>();

        return new Iterator<>() {
            private boolean finalized;

            @Override
            public boolean hasNext() {
                boolean hasNext = streamIter.hasNext();
                if (!hasNext) {
                    finalizeStream();
                }
                return hasNext;
            }

            @Override
            public Object next() {
                try {
                    Object item = streamIter.next();
                    String answer = extractAnswerOutput(item);
                    if (!answer.isEmpty()) {
                        answerOutputs.add(answer);
                    }
                    return item;
                } catch (NoSuchElementException e) {
                    finalizeStream();
                    throw e;
                }
            }

            private void finalizeStream() {
                if (finalized) {
                    return;
                }
                finalized = true;
                if (enableMemory && inputs instanceof Map<?, ?> inputMap) {
                    writeMessagesToMemoryAsync(inputMap, String.join("", answerOutputs));
                }
                if (managedSession != null) {
                    managedSession.postRun();
                }
            }
        };
    }

    /**
     * Set prompt template and propagate to controller.
     *
     * @param promptTemplate new prompt template
     */
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        agentConfig.setPromptTemplate(promptTemplate);
        Controller ctrl = getController();
        if (ctrl.getEventHandler() instanceof LlmEventHandler llmHandler) {
            llmHandler.setPromptTemplate(promptTemplate);
        }
    }

    public LlmAgentConfig getAgentConfig() {
        return agentConfig;
    }

    // ==================== Private Helpers ====================

    private static AgentCard buildAgentCard(LlmAgentConfig config) {
        return AgentCard.builder()
                .id(config.getId())
                .name(config.getId())
                .description(config.getDescription())
                .build();
    }

    private static ControllerConfig buildControllerConfig(LlmAgentConfig config) {
        ControllerConfig cc = new ControllerConfig();
        cc.setMaxConcurrentTasks(1);
        return cc;
    }

    private AgentSessionApi createManagedSession(Object inputs) {
        String sessionId = "default_session";
        if (inputs instanceof Map<?, ?> inputMap) {
            Object conversationId = inputMap.get("conversation_id");
            if (conversationId instanceof String s && !s.isBlank()) {
                sessionId = s;
            }
        }
        return AgentSessionApi.create(sessionId, null, getCard());
    }

    private void writeMessagesToMemoryAsync(Map<?, ?> inputs, Object result) {
        CompletableFuture.runAsync(() -> {
            try {
                writeMessagesToMemory(inputs, result);
            } catch (Exception e) {
                Loggers.AGENT.error("Add memory failed: {}", e.getMessage());
            }
        });
    }

    private void writeMessagesToMemory(Map<?, ?> inputs, Object result) {
        Object userIdObj = inputs.get("user_id");
        if (userIdObj == null) {
            return;
        }
        String userId = userIdObj.toString();
        String sessionId = inputs.containsKey("conversation_id")
                ? inputs.get("conversation_id").toString() : "default_session";

        List<BaseMessage> messageList = new ArrayList<>();

        // Add user message
        Object queryObj = inputs.get("query");
        if (queryObj instanceof String query && !query.isEmpty()) {
            messageList.add(new UserMessage(query));
        }

        // Add AI response message
        AssistantMessage assistantMessage = convertResponseToMessage(result);
        if (assistantMessage != null && assistantMessage.getContentAsString() != null
                && !assistantMessage.getContentAsString().isEmpty()) {
            messageList.add(assistantMessage);
        }

        if (!messageList.isEmpty()) {
            longTermMemoryInstance.addMessages(
                    messageList, agentConfig.getAgentMemoryConfig(),
                    userId, agentConfig.getMemoryScopeId(), sessionId
            );
        }
    }

    private static String extractAnswerOutput(Object result) {
        if (result instanceof OutputSchema output) {
            Object payload = output.getPayload();
            if (payload instanceof Map<?, ?> payloadMap) {
                if ("answer".equals(payloadMap.get("result_type"))
                        && payloadMap.get("output") instanceof String outputStr) {
                    return outputStr;
                }
            }
        }
        return "";
    }

    private static AssistantMessage convertResponseToMessage(Object result) {
        if (result instanceof OutputSchema output
                && "answer".equals(output.getType())
                && output.getPayload() instanceof Map<?, ?> payload) {
            Object response = payload.get("output");
            if (response instanceof String s && !s.isEmpty()) {
                return new AssistantMessage(s);
            }
        } else if (result instanceof Map<?, ?> map
                && "answer".equals(map.get("result_type"))
                && map.get("output") instanceof String s && !s.isEmpty()) {
            return new AssistantMessage(s);
        } else if (result instanceof String s && !s.isEmpty()) {
            return new AssistantMessage(s);
        } else if (result instanceof ControllerOutput co) {
            List<?> chunks = co.getDataAsChunks();
            if (chunks != null) {
                for (Object chunk : chunks) {
                    AssistantMessage msg = convertResponseToMessage(chunk);
                    if (msg != null) {
                        return msg;
                    }
                }
            }
        }
        return null;
    }
}
