/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code MessageManager} in
 * {@code openjiuwen/core/memory/manage/mem_model/message_manager.py}.
 */
public class MessageManager {
    private static final String MEMORY_TYPE = "message";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String ORDER_DESC = "desc";

    private final BaseMessageStore store;

    public MessageManager(BaseMessageStore store) {
        this.store = store;
    }

    public BaseMessageStore getStore() {
        return store;
    }

    public CompletableFuture<String> add(MessageAddRequest request) {
        if (request.getUserId() == null) {
            throw messageError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, "must provide user_id for add message");
        }
        if (request.getScopeId() == null) {
            throw messageError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, "must provide scope_id for add message");
        }
        if (request.getContent() == null) {
            throw messageError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, "must provide content for add message");
        }
        if (request.getRole() == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        Map<String, Object> messageAdd = new LinkedHashMap<>();
        messageAdd.put("message", new BaseMessage(request.getRole(), request.getContent()));
        messageAdd.put("user_id", request.getUserId());
        messageAdd.put("scope_id", request.getScopeId());
        messageAdd.put("session_id", request.getSessionId());
        messageAdd.put("timestamp", request.getTimestamp());
        return store.addMessage(messageAdd);
    }

    public CompletableFuture<List<Map.Entry<BaseMessage, ZonedDateTime>>> get() {
        return get(null, null, null, 10);
    }

    public CompletableFuture<List<Map.Entry<BaseMessage, ZonedDateTime>>> get(String userId,
                                                                              String scopeId,
                                                                              String sessionId) {
        return get(userId, scopeId, sessionId, 10);
    }

    public CompletableFuture<List<Map.Entry<BaseMessage, ZonedDateTime>>> get(String userId,
                                                                              String scopeId,
                                                                              String sessionId,
                                                                              int messageLen) {
        if (messageLen <= 0) {
            throw messageError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "message length Must bigger than zero for get message");
        }

        Map<String, Object> messageFilter = new LinkedHashMap<>();
        messageFilter.put("user_id", userId);
        messageFilter.put("scope_id", scopeId);
        messageFilter.put("session_id", sessionId);
        return store.getMessages(messageFilter, messageLen, TIMESTAMP_FIELD, ORDER_DESC)
                .thenApply(MessageManager::toChronologicalRows);
    }

    public CompletableFuture<Map.Entry<BaseMessage, ZonedDateTime>> getById(String messageId) {
        try {
            return store.getMessageById(messageId).handle((row, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrapCompletionException(throwable);
                    if (cause instanceof IllegalArgumentException) {
                        return null;
                    }
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new CompletionException(cause);
                }
                return toMessageTimestampEntry(row);
            });
        } catch (IllegalArgumentException ignored) {
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Boolean> deleteByUserAndScope(String userId, String scopeId) {
        Map<String, Object> messageFilter = new LinkedHashMap<>();
        messageFilter.put("user_id", userId);
        messageFilter.put("scope_id", scopeId);
        return store.deleteMessages(messageFilter).thenApply(count -> count > 0);
    }

    private static List<Map.Entry<BaseMessage, ZonedDateTime>> toChronologicalRows(
            List<Map.Entry<BaseMessage, MessageMetadata>> messagesWithMetadata) {
        List<Map.Entry<BaseMessage, MessageMetadata>> copy = new ArrayList<>(messagesWithMetadata);
        Collections.reverse(copy);

        List<Map.Entry<BaseMessage, ZonedDateTime>> result = new ArrayList<>(copy.size());
        for (Map.Entry<BaseMessage, MessageMetadata> row : copy) {
            result.add(toMessageTimestampEntry(row));
        }
        return result;
    }

    private static Map.Entry<BaseMessage, ZonedDateTime> toMessageTimestampEntry(
            Map.Entry<BaseMessage, MessageMetadata> row) {
        if (row == null) {
            return null;
        }
        MessageMetadata metadata = row.getValue();
        ZonedDateTime timestamp = metadata == null ? null : metadata.getTimestamp();
        return new AbstractMap.SimpleImmutableEntry<>(row.getKey(), timestamp);
    }

    private static RuntimeException messageError(StatusCode status, String errorMsg) {
        return ErrorHelper.buildError(status,
                "memory_type", MEMORY_TYPE,
                "error_msg", errorMsg);
    }

    private static Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
