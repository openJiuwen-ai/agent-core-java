/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Message utilities for adding and retrieving chat messages.
 *
 * <p>Mirrors Python's {@code MessageUtils} in
 * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
 */
public final class MessageUtils {

    private MessageUtils() {
    }

    public static boolean shouldAddUserMessage(String query, ContextEnginePort contextEngine, SessionPort session) {
        AgentContextPort agentContext = contextEngine.getContext(session.getSessionId());
        List<BaseMessage> lastMessages = agentContext.getMessages(1);
        if (lastMessages.isEmpty()) {
            return true;
        }
        BaseMessage lastMessage = lastMessages.get(0);
        return !("user".equals(lastMessage.getRole()) && Objects.equals(lastMessage.getContent(), query));
    }

    public static CompletionStage<Void> addUserMessage(Object query, ContextEnginePort contextEngine,
                                                       SessionPort session) {
        if (!shouldAddUserMessage(String.valueOf(query), contextEngine, session)) {
            return CompletableFuture.completedFuture(null);
        }
        AgentContextPort agentContext = contextEngine.getContext(session.getSessionId());
        UserMessage userMessage = new UserMessage();
        userMessage.setRole("user");
        userMessage.setContent(query);
        return agentContext.addMessages(userMessage);
    }

    public static CompletionStage<Void> addAiMessage(AssistantMessage aiMessage, ContextEnginePort contextEngine,
                                                     SessionPort session) {
        if (aiMessage == null) {
            return CompletableFuture.completedFuture(null);
        }
        return contextEngine.getContext(session.getSessionId()).addMessages(aiMessage);
    }

    public static CompletionStage<Void> addToolMessage(ToolMessage toolMessage, ContextEnginePort contextEngine,
                                                       SessionPort session) {
        if (toolMessage == null) {
            return CompletableFuture.completedFuture(null);
        }
        return contextEngine.getContext(session.getSessionId()).addMessages(toolMessage);
    }

    public static CompletionStage<Void> addWorkflowMessage(BaseMessage message, String workflowId,
                                                           ContextEnginePort contextEngine, SessionPort session) {
        return contextEngine.getContext(workflowId, session.getSessionId()).addMessages(message);
    }

    public static List<BaseMessage> getChatHistory(ContextEnginePort contextEngine, SessionPort session,
                                                   AgentConfigView config) {
        List<BaseMessage> chatHistory = contextEngine.getContext(session.getSessionId()).getMessages();
        int maxRounds = config.constrain().reservedMaxChatRounds();
        int windowSize = maxRounds <= 0 ? chatHistory.size() : 2 * maxRounds;
        int fromIndex = Math.max(0, chatHistory.size() - windowSize);
        return new ArrayList<>(chatHistory.subList(fromIndex, chatHistory.size()));
    }

    /**
     * Narrow context-engine surface consumed by {@link MessageUtils}.
     *
     * <p>Mirrors Python's {@code ContextEngine} calls in
     * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
     */
    public interface ContextEnginePort {
        AgentContextPort getContext(String sessionId);

        AgentContextPort getContext(String contextId, String sessionId);
    }

    /**
     * Narrow context surface consumed by {@link MessageUtils}.
     *
     * <p>Mirrors Python context objects used in
     * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
     */
    public interface AgentContextPort {
        List<BaseMessage> getMessages();

        default List<BaseMessage> getMessages(int size) {
            List<BaseMessage> messages = getMessages();
            if (size <= 0 || messages.isEmpty()) {
                return List.of();
            }
            int fromIndex = Math.max(0, messages.size() - size);
            return new ArrayList<>(messages.subList(fromIndex, messages.size()));
        }

        CompletionStage<Void> addMessages(BaseMessage message);
    }

    /**
     * Narrow session surface consumed by {@link MessageUtils}.
     *
     * <p>Mirrors Python's {@code Session.get_session_id} use in
     * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
     */
    public interface SessionPort {
        String getSessionId();
    }

    /**
     * Narrow agent-config surface consumed by {@link MessageUtils}.
     *
     * <p>Mirrors Python's {@code AgentConfig} use in
     * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
     */
    public interface AgentConfigView {
        ConstrainView constrain();
    }

    /**
     * Narrow constrain surface consumed by {@link MessageUtils}.
     *
     * <p>Mirrors Python's {@code config.constrain.reserved_max_chat_rounds} use in
     * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
     */
    public interface ConstrainView {
        int reservedMaxChatRounds();
    }
}
