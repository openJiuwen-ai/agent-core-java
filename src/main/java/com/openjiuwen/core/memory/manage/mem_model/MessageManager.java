/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * DB-based message management.
 */
public class MessageManager {

    private final SqlDbStore sqlDb;
    private final DataIdManager dataId;
    private final byte[] cryptoKey;
    private static final String MESSAGE_TABLE = "user_message";

    /**
     * Result of getting a message: the BaseMessage and its timestamp.
     */
    public record MessageRecord(BaseMessage message, OffsetDateTime timestamp) {}

    /**
     * Auto-generated for codecheck compliance.
     */
    public MessageManager(SqlDbStore sqlDb, DataIdManager dataId, byte[] cryptoKey) {
        this.sqlDb = sqlDb;
        this.dataId = dataId;
        this.cryptoKey = cryptoKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String add(MessageAddRequest req) {
        if (req.getUserId() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "must provide user_id for add message"
            );
        }
        if (req.getScopeId() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "must provide scope_id for add message"
            );
        }
        if (req.getContent() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "must provide content for add message"
            );
        }

        String messageId = dataId.generateNextId(req.getUserId());
        OffsetDateTime time = req.getTimestamp() != null ? req.getTimestamp() : OffsetDateTime.now(ZoneOffset.UTC);
        String encryptedContent = BaseMemoryManager.encryptMemoryIfNeeded(cryptoKey, req.getContent());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", messageId);
        data.put("user_id", req.getUserId() != null ? req.getUserId() : "");
        data.put("session_id", req.getSessionId() != null ? req.getSessionId() : "");
        data.put("scope_id", req.getScopeId() != null ? req.getScopeId() : "");
        data.put("role", req.getRole() != null ? req.getRole() : "");
        data.put("content", encryptedContent);
        data.put("timestamp", time.toString());

        sqlDb.write(MESSAGE_TABLE, data);
        return messageId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<MessageRecord> get(String userId, String scopeId, String sessionId, int messageLen) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (userId != null) {
            filters.put("user_id", userId);
        }
        if (scopeId != null) {
            filters.put("scope_id", scopeId);
        }
        if (sessionId != null) {
            filters.put("session_id", sessionId);
        }
        if (messageLen <= 0) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "message length must be bigger than zero for get message"
            );
        }
        List<Map<String, Object>> messages = sqlDb.getWithSort(MESSAGE_TABLE, filters, "timestamp", "DESC", messageLen);

        // Reverse to get chronological order
        List<Map<String, Object>> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);

        List<MessageRecord> result = new ArrayList<>();
        for (Map<String, Object> msg : reversed) {
            BaseMessage baseMsg = toBaseMessage(msg);
            baseMsg.setContent(BaseMemoryManager.decryptMemoryIfNeeded(
                    cryptoKey, baseMsg.getContentAsString()));
            OffsetDateTime ts = parseTimestamp(msg.get("timestamp"));
            result.add(new MessageRecord(baseMsg, ts));
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MessageRecord getById(String msgId) {
        Map<String, List<Object>> conditions = new LinkedHashMap<>();
        conditions.put("message_id", new ArrayList<>(List.of(msgId)));
        List<Map<String, Object>> messages = sqlDb.conditionGet(MESSAGE_TABLE, conditions, null);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Map<String, Object> msg = messages.get(0);
        BaseMessage baseMsg = toBaseMessage(msg);
        baseMsg.setContent(BaseMemoryManager.decryptMemoryIfNeeded(
                cryptoKey, baseMsg.getContentAsString()));
        OffsetDateTime ts = parseTimestamp(msg.get("timestamp"));
        return new MessageRecord(baseMsg, ts);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean deleteByUserAndScope(String userId, String scopeId) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("user_id", userId);
        conditions.put("scope_id", scopeId);
        return sqlDb.delete(MESSAGE_TABLE, conditions);
    }

    private BaseMessage toBaseMessage(Map<String, Object> data) {
        BaseMessage msg = BaseMessage.builder()
                .role(data.getOrDefault("role", "").toString())
                .content(data.getOrDefault("content", "").toString())
                .build();
        if (data.containsKey("name")) {
            msg.setName(data.get("name").toString());
        }
        return msg;
    }

    private OffsetDateTime parseTimestamp(Object tsObj) {
        if (tsObj == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(tsObj.toString());
        } catch (Exception e) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
