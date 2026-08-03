/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Smoke checks for the isolated T01018 candidate.
 * <p>
 * Mirrors Python's {@code MessageManager} behavior in
 * {@code openjiuwen/core/memory/manage/mem_model/message_manager.py}.
 * </p>
 */
public final class MessageManagerCandidateSmokeTest {

    private MessageManagerCandidateSmokeTest() {
    }

    public static void main(String[] args) {
        addValidatesRequiredFieldsAndDelegatesPythonMessageDict();
        getUsesDescStoreQueryAndReturnsChronologicalRows();
        getByIdReturnsTimestampAndMapsValueErrorToNull();
        deleteByUserAndScopeReturnsWhetherRowsWereDeleted();
        explicitNullTimestampIsPreserved();
    }

    private static void addValidatesRequiredFieldsAndDelegatesPythonMessageDict() {
        RecordingStore store = new RecordingStore();
        MessageManager manager = new MessageManager(store);
        ZonedDateTime timestamp = ZonedDateTime.parse("2026-06-15T10:15:30+08:00");
        MessageAddRequest request = MessageAddRequest.builder()
                .userId("user-1")
                .scopeId("scope-1")
                .content("hello")
                .role("user")
                .sessionId("session-1")
                .timestamp(timestamp)
                .build();

        String messageId = manager.add(request).join();

        require("msg-1".equals(messageId), "add should return store message id");
        require(store.lastMessageAdd.containsKey("message"), "message key exists");
        require(store.lastMessageAdd.containsKey("user_id"), "user_id key exists");
        require(store.lastMessageAdd.containsKey("scope_id"), "scope_id key exists");
        require(store.lastMessageAdd.containsKey("session_id"), "session_id key exists");
        require(store.lastMessageAdd.containsKey("timestamp"), "timestamp key exists");
        BaseMessage message = (BaseMessage) store.lastMessageAdd.get("message");
        require("user".equals(message.getRole()), "message role");
        require("hello".equals(message.getContent()), "message content");
        require("user-1".equals(store.lastMessageAdd.get("user_id")), "user id");
        require("scope-1".equals(store.lastMessageAdd.get("scope_id")), "scope id");
        require("session-1".equals(store.lastMessageAdd.get("session_id")), "session id");
        require(timestamp.equals(store.lastMessageAdd.get("timestamp")), "timestamp");

        expectBaseError(() -> manager.add(MessageAddRequest.builder().scopeId("s").content("c").build()),
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "must provide user_id for add message");
        expectBaseError(() -> manager.add(MessageAddRequest.builder().userId("u").content("c").build()),
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "must provide scope_id for add message");
        expectBaseError(() -> manager.add(MessageAddRequest.builder().userId("u").scopeId("s").build()),
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "must provide content for add message");
        expectIllegalArgument(() -> manager.add(MessageAddRequest.builder()
                .userId("u")
                .scopeId("s")
                .content("c")
                .build()), "role must not be null");
    }

    private static void getUsesDescStoreQueryAndReturnsChronologicalRows() {
        RecordingStore store = new RecordingStore();
        ZonedDateTime older = ZonedDateTime.parse("2026-06-15T09:00:00+08:00");
        ZonedDateTime newer = ZonedDateTime.parse("2026-06-15T10:00:00+08:00");
        store.messages = List.of(
                row(new BaseMessage("assistant", "newer"), metadata("m2", newer)),
                row(new BaseMessage("user", "older"), metadata("m1", older))
        );
        MessageManager manager = new MessageManager(store);

        List<Map.Entry<BaseMessage, ZonedDateTime>> result = manager.get("user-1", "scope-1", null, 2).join();

        require("user-1".equals(store.lastMessageFilter.get("user_id")), "filter user id");
        require("scope-1".equals(store.lastMessageFilter.get("scope_id")), "filter scope id");
        require(store.lastMessageFilter.containsKey("session_id"), "filter keeps session_id key");
        require(store.lastMessageFilter.get("session_id") == null, "filter session id null value");
        require(store.lastLimit == 2, "limit");
        require("timestamp".equals(store.lastOrderBy), "order by timestamp");
        require("desc".equals(store.lastOrderDirection), "order direction desc");
        require(result.size() == 2, "result size");
        require("older".equals(result.get(0).getKey().getContent()), "older row first after reverse");
        require(older.equals(result.get(0).getValue()), "older timestamp");
        require("newer".equals(result.get(1).getKey().getContent()), "newer row second after reverse");
        require(newer.equals(result.get(1).getValue()), "newer timestamp");

        expectBaseError(() -> manager.get("u", "s", "sess", 0),
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                "message length Must bigger than zero for get message");
    }

    private static void getByIdReturnsTimestampAndMapsValueErrorToNull() {
        RecordingStore store = new RecordingStore();
        ZonedDateTime timestamp = ZonedDateTime.parse("2026-06-15T11:00:00+08:00");
        store.messageById = row(new BaseMessage("assistant", "hi"), metadata("m1", timestamp));
        MessageManager manager = new MessageManager(store);

        Map.Entry<BaseMessage, ZonedDateTime> result = manager.getById("m1").join();

        require("hi".equals(result.getKey().getContent()), "get by id content");
        require(timestamp.equals(result.getValue()), "get by id timestamp");

        store.throwSyncValueError = true;
        require(manager.getById("missing").join() == null, "sync ValueError maps to null");

        store.throwSyncValueError = false;
        store.throwAsyncValueError = true;
        require(manager.getById("missing").join() == null, "async ValueError maps to null");
    }

    private static void deleteByUserAndScopeReturnsWhetherRowsWereDeleted() {
        RecordingStore store = new RecordingStore();
        MessageManager manager = new MessageManager(store);

        store.deleteCount = 2;
        require(manager.deleteByUserAndScope("user-1", "scope-1").join(), "delete true");
        require("user-1".equals(store.lastDeleteFilter.get("user_id")), "delete user id");
        require("scope-1".equals(store.lastDeleteFilter.get("scope_id")), "delete scope id");

        store.deleteCount = 0;
        require(!manager.deleteByUserAndScope("user-1", "scope-1").join(), "delete false");
    }

    private static void explicitNullTimestampIsPreserved() {
        MessageAddRequest request = MessageAddRequest.builder()
                .userId("user-1")
                .scopeId("scope-1")
                .content("content")
                .timestamp(null)
                .build();
        require(request.getTimestamp() == null, "explicit null timestamp is preserved");

        MessageAddRequest defaultRequest = MessageAddRequest.builder().build();
        require(defaultRequest.getTimestamp() != null, "omitted timestamp defaults to now");
    }

    private static MessageMetadata metadata(String messageId, ZonedDateTime timestamp) {
        return new MessageMetadata(messageId, "user-1", "scope-1", "session-1", timestamp, "user");
    }

    private static Map.Entry<BaseMessage, MessageMetadata> row(BaseMessage message, MessageMetadata metadata) {
        return new AbstractMap.SimpleImmutableEntry<>(message, metadata);
    }

    private static void expectBaseError(Runnable action, StatusCode expectedStatus, String expectedMessagePart) {
        try {
            action.run();
            throw new AssertionError("expected BaseError");
        } catch (BaseError error) {
            require(error.getStatus() == expectedStatus, "status " + expectedStatus);
            require(error.getMessage().contains(expectedMessagePart), "message contains " + expectedMessagePart);
        }
    }

    private static void expectIllegalArgument(Runnable action, String expectedMessagePart) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException error) {
            require(error.getMessage().contains(expectedMessagePart), "message contains " + expectedMessagePart);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingStore extends BaseMessageStore {
        private Map<String, Object> lastMessageAdd;
        private Map<String, Object> lastMessageFilter;
        private Map<String, Object> lastDeleteFilter;
        private int lastLimit;
        private String lastOrderBy;
        private String lastOrderDirection;
        private int deleteCount;
        private boolean throwSyncValueError;
        private boolean throwAsyncValueError;
        private List<Map.Entry<BaseMessage, MessageMetadata>> messages = new ArrayList<>();
        private Map.Entry<BaseMessage, MessageMetadata> messageById;

        @Override
        public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
            this.lastMessageAdd = messageAdd;
            return CompletableFuture.completedFuture("msg-1");
        }

        @Override
        public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId) {
            if (throwSyncValueError) {
                throw new IllegalArgumentException("message not found");
            }
            if (throwAsyncValueError) {
                CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalArgumentException("message not found"));
                return failed;
            }
            return CompletableFuture.completedFuture(messageById);
        }

        @Override
        public CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
                Map<String, Object> messageFilter,
                int limit,
                String orderBy,
                String orderDirection) {
            this.lastMessageFilter = messageFilter;
            this.lastLimit = limit;
            this.lastOrderBy = orderBy;
            this.lastOrderDirection = orderDirection;
            return CompletableFuture.completedFuture(messages);
        }

        @Override
        public CompletableFuture<Boolean> updateMessage(String messageId, Object content) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> deleteMessageById(String messageId) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter) {
            this.lastDeleteFilter = messageFilter;
            return CompletableFuture.completedFuture(deleteCount);
        }

        @Override
        public CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public CompletableFuture<Integer> getSchemaVersion() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setSchemaVersion(int version) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
