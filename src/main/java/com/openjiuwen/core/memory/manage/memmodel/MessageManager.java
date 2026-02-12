/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * DB-Based Message Management.
 * <p>
 * Corresponds to Python: manage/mem_model/message_manager.py
 */
public class MessageManager {

    private final SqlDbStore sqlDb;
    private final String messageTable = "user_message";
    private final DataIdManager dataId;
    private final byte[] cryptoKey;

    /**
     * Create a new MessageManager.
     *
     * @param sqlDbStore the SQL database store
     * @param dataIdManager the data ID manager
     * @param cryptoKey the encryption key (empty array for no encryption)
     */
    public MessageManager(SqlDbStore sqlDbStore, DataIdManager dataIdManager, byte[] cryptoKey) {
        this.sqlDb = sqlDbStore;
        this.dataId = dataIdManager;
        this.cryptoKey = cryptoKey != null ? cryptoKey : new byte[0];
    }

    /**
     * Add a new message.
     *
     * @param req the message add request
     * @return CompletableFuture containing the message ID
     */
    public CompletableFuture<String> add(MessageAddRequest req) {
        if (req.userId().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "must provide user_id for add message",
                    null, null,
                    Map.of("memory_type", "message")
            );
        }
        if (req.scopeId().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "must provide scope_id for add message",
                    null, null,
                    Map.of("memory_type", "message")
            );
        }
        if (req.content().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "must provide content for add message",
                    null, null,
                    Map.of("memory_type", "message")
            );
        }

        String messageId = dataId.generateNextId(req.userId().get());
        Instant time = req.timestamp();
        String encryptedContent = BaseMemoryManager.encryptMemoryIfNeeded(
                cryptoKey, req.content().get()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("message_id", messageId);
        data.put("user_id", req.userId().orElse(""));
        data.put("session_id", req.sessionId().orElse(""));
        data.put("scope_id", req.scopeId().orElse(""));
        data.put("role", req.role().orElse(""));
        data.put("content", encryptedContent);
        data.put("timestamp", time.toString());

        return sqlDb.write(messageTable, data)
                .thenApply(success -> messageId);
    }

    /**
     * Get messages by filter criteria.
     *
     * @param userId optional user ID filter
     * @param scopeId optional scope ID filter
     * @param sessionId optional session ID filter
     * @param messageLen maximum number of messages to return
     * @return CompletableFuture containing list of (BaseMessage, Instant) pairs
     */
    public CompletableFuture<List<MessageWithTimestamp>> get(
            String userId, String scopeId, String sessionId, int messageLen) {

        if (messageLen <= 0) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "message length must be bigger than zero for get message",
                    null, null,
                    Map.of("memory_type", "message")
            );
        }

        Map<String, Object> filters = new HashMap<>();
        if (userId != null) {
            filters.put("user_id", userId);
        }
        if (scopeId != null) {
            filters.put("scope_id", scopeId);
        }
        if (sessionId != null) {
            filters.put("session_id", sessionId);
        }

        return sqlDb.getWithSort(messageTable, filters, "timestamp", "DESC", messageLen)
                .thenApply(messages -> {
                    List<MessageWithTimestamp> result = new ArrayList<>();
                    // Reverse to get chronological order
                    List<Map<String, Object>> reversed = new ArrayList<>(messages);
                    Collections.reverse(reversed);

                    for (Map<String, Object> message : reversed) {
                        String content = (String) message.get("content");
                        String decryptedContent = BaseMemoryManager.decryptMemoryIfNeeded(
                                cryptoKey, content
                        );

                        BaseMessage baseMsg = new BaseMessage(
                                (String) message.getOrDefault("role", ""),
                                decryptedContent
                        );

                        String timestampStr = (String) message.get("timestamp");
                        Instant timestamp = Instant.parse(timestampStr);

                        result.add(new MessageWithTimestamp(baseMsg, timestamp));
                    }
                    return result;
                });
    }

    /**
     * Get a message by its ID.
     *
     * @param msgId the message ID
     * @return CompletableFuture containing Optional of (BaseMessage, Instant) pair
     */
    public CompletableFuture<Optional<MessageWithTimestamp>> getById(String msgId) {
        Map<String, List<Object>> filters = new HashMap<>();
        filters.put("message_id", List.of(msgId));

        return sqlDb.conditionGet(messageTable, filters, null)
                .thenApply(messages -> {
                    if (messages == null || messages.isEmpty()) {
                        return Optional.empty();
                    }

                    Map<String, Object> message = messages.get(0);
                    String content = (String) message.get("content");
                    String decryptedContent = BaseMemoryManager.decryptMemoryIfNeeded(
                            cryptoKey, content
                    );

                    BaseMessage baseMsg = new BaseMessage(
                            (String) message.getOrDefault("role", ""),
                            decryptedContent
                    );

                    String timestampStr = (String) message.get("timestamp");
                    Instant timestamp = Instant.parse(timestampStr);

                    return Optional.of(new MessageWithTimestamp(baseMsg, timestamp));
                });
    }

    /**
     * Record containing a message and its timestamp.
     */
    public record MessageWithTimestamp(BaseMessage message, Instant timestamp) {
    }
}

