/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;

import java.util.List;

/**
 * Message utilities for adding and retrieving messages in the context engine.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.common.utils.message_utils.MessageUtils}.
 */
public final class MessageUtils {

    private MessageUtils() {
    }

    /**
     * Check if a user message should be added (deduplication).
     *
     * @param query         user input
     * @param contextEngine context engine
     * @param session       session instance
     * @return true if the message should be added
     */
    public static boolean shouldAddUserMessage(String query, ContextEngine contextEngine, Session session) {
        ModelContext agentContext = contextEngine.getContext("default_context_id", session.getSessionId());
        if (agentContext == null) {
            return true;
        }

        List<BaseMessage> lastMessages = agentContext.getMessages();
        if (lastMessages == null || lastMessages.isEmpty()) {
            return true;
        }

        BaseMessage lastMessage = lastMessages.get(lastMessages.size() - 1);
        if ("user".equals(lastMessage.getRole()) && query != null && query.equals(lastMessage.getContent())) {
            Loggers.CONTEXT_ENGINE.info("Skipping duplicate user message");
            return false;
        }

        return true;
    }

    /**
     * Add a user message to the chat history.
     *
     * @param query         user input
     * @param contextEngine context engine
     * @param session       session instance
     */
    public static void addUserMessage(Object query, ContextEngine contextEngine, Session session) {
        String queryStr = query != null ? query.toString() : "";
        if (shouldAddUserMessage(queryStr, contextEngine, session)) {
            ModelContext agentContext = contextEngine.getContext("default_context_id", session.getSessionId());
            if (agentContext != null) {
                UserMessage userMessage = new UserMessage(queryStr);
                agentContext.addMessages(List.of(userMessage));
                if (UserConfig.isSensitive()) {
                    Loggers.CONTEXT_ENGINE.info("Added user message");
                } else {
                    Loggers.CONTEXT_ENGINE.info("Added user message: " + queryStr);
                }
            }
        }
    }

    /**
     * Add an assistant message to the chat history.
     *
     * @param aiMessage     assistant message object
     * @param contextEngine context engine
     * @param session       session instance
     */
    public static void addAiMessage(AssistantMessage aiMessage, ContextEngine contextEngine, Session session) {
        if (aiMessage != null) {
            ModelContext agentContext = contextEngine.getContext("default_context_id", session.getSessionId());
            if (agentContext != null) {
                agentContext.addMessages(List.of(aiMessage));
            }
        }
    }

    /**
     * Add a tool message to the chat history.
     *
     * @param toolMessage   tool message object
     * @param contextEngine context engine
     * @param session       session instance
     */
    public static void addToolMessage(ToolMessage toolMessage, ContextEngine contextEngine, Session session) {
        if (toolMessage != null) {
            ModelContext agentContext = contextEngine.getContext("default_context_id", session.getSessionId());
            if (agentContext != null) {
                agentContext.addMessages(List.of(toolMessage));
            }
        }
    }

    /**
     * Add a message to a specific workflow's chat history.
     *
     * @param message       message object
     * @param workflowId    workflow ID
     * @param contextEngine context engine
     * @param session       session instance
     */
    public static void addWorkflowMessage(BaseMessage message, String workflowId,
                                           ContextEngine contextEngine, Session session) {
        ModelContext workflowContext = contextEngine.getContext(workflowId, session.getSessionId());
        if (workflowContext != null) {
            workflowContext.addMessages(List.of(message));
        }
    }

    /**
     * Get chat history, limited by max rounds.
     *
     * @param contextEngine context engine
     * @param session       session instance
     * @param maxRounds     maximum number of dialogue rounds to return
     * @return chat history message list
     */
    public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session, int maxRounds) {
        ModelContext agentContext = contextEngine.getContext("default_context_id", session.getSessionId());
        if (agentContext == null) {
            return List.of();
        }
        List<BaseMessage> chatHistory = agentContext.getMessages();
        if (chatHistory == null || chatHistory.isEmpty()) {
            return List.of();
        }
        int limit = 2 * maxRounds;
        if (chatHistory.size() <= limit) {
            return chatHistory;
        }
        return chatHistory.subList(chatHistory.size() - limit, chatHistory.size());
    }
}
