/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.utils;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.Session;

import java.util.Collections;
import java.util.List;

/**
 * Reasoner utility methods for legacy controllers.
 * Mirrors Python's {@code ReasonerUtils}.
 */
public final class ReasonerUtils {

    private ReasonerUtils() {
    }

    /**
     * Get history by max conversation rounds.
     * Mirrors Python's {@code ReasonerUtils.get_chat_history()}.
     */
    public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session,
                                                    int chatHistoryMaxTurn) {
        if (contextEngine == null || session == null) {
            return Collections.emptyList();
        }
        ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
        if (agentContext == null) {
            return Collections.emptyList();
        }
        List<BaseMessage> chatHistory = agentContext.getMessages();
        if (chatHistory == null || chatHistory.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = 2 * chatHistoryMaxTurn;
        if (chatHistory.size() <= limit) {
            return chatHistory;
        }
        return chatHistory.subList(chatHistory.size() - limit, chatHistory.size());
    }
}
