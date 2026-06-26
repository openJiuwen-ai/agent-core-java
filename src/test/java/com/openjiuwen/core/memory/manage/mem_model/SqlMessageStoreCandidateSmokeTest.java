/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Focused candidate validation for {@link SqlMessageStore}.
 *
 * <p>Mirrors Python's {@code SqlMessageStore} in
 * {@code openjiuwen/core/memory/manage/mem_model/sql_message_store.py}.</p>
 */
public final class SqlMessageStoreCandidateSmokeTest {

    private SqlMessageStoreCandidateSmokeTest() {
    }

    public static void main(String[] args) {
        verifiesAddMessagePersistsPythonFields();
        verifiesBatchAddAndLookup();
        verifiesGetMessagesFilterSemantics();
        verifiesUpdateDeleteCountAndSchemaVersion();
        System.out.println("PASS SqlMessageStoreCandidateSmokeTest");
    }

    private static void verifiesAddMessagePersistsPythonFields() {
        RecordingSqlDbStore db = new RecordingSqlDbStore();
        SqlMessageStore store = new SqlMessageStore(null, db, "user_message");
        ZonedDateTime timestamp = ZonedDateTime.parse("2026-06-15T01:02:03Z");
        BaseMessage message = new BaseMessage("user", "hello");

        String messageId = store.addMessage(linkedMap(
                "message", message,
                "user_id", "user-1",
                "scope_id", "scope-1",
                "timestamp", timestamp
        )).join();

        require(messageId.startsWith("msg_"), "message id prefix");
        require(messageId.endsWith("_1781485323000"), "message id timestamp milliseconds");
        require("user_message".equals(db.writeTable), "write table");
        require(db.writeData.get("message_id").equals(messageId), "message_id written");
        require(db.writeData.get("user_id").equals("user-1"), "user_id written");
        require(db.writeData.get("session_id").equals(""), "missing session defaults to empty string");
        require(db.writeData.get("scope_id").equals("scope-1"), "scope_id written");
        require(db.writeData.get("role").equals("user"), "role written");
        require(db.writeData.get("content").equals("hello"), "string content stored directly");
        require(db.writeData.get("timestamp").equals(timestamp), "timestamp written");
    }

    private static void verifiesBatchAddAndLookup() {
        RecordingSqlDbStore db = new RecordingSqlDbStore();
        SqlMessageStore store = new SqlMessageStore(db);
        BaseMessage first = new BaseMessage("user", "one");
        BaseMessage second = new BaseMessage("assistant", "two");

        List<String> ids = store.addMessages(List.of(
                linkedMap("message", first, "timestamp", ZonedDateTime.parse("2026-06-15T01:00:00Z")),
                linkedMap("message", second, "timestamp", ZonedDateTime.parse("2026-06-15T01:00:01Z"))
        )).join();

        require(ids.size() == 2, "batch ids");
        require(db.writes.size() == 2, "batch writes");

        db.conditionRows = List.of(linkedMap(
                "message_id", "msg-lookup",
                "user_id", "user-1",
                "scope_id", "scope-1",
                "session_id", "session-1",
                "role", "assistant",
                "content", "stored text",
                "timestamp", "2026-06-15T01:02:03Z"
        ));

        Map.Entry<BaseMessage, MessageMetadata> entry = store.getMessageById("msg-lookup").join();

        require(db.conditionConditions.equals(Map.of("message_id", List.of("msg-lookup"))), "lookup condition");
        require(entry.getKey().getRole().equals("assistant"), "lookup role");
        require(entry.getKey().getContent().equals("stored text"), "lookup content");
        require(entry.getValue().getMessageId().equals("msg-lookup"), "metadata message id");
        require(entry.getValue().getTimestamp().equals(ZonedDateTime.parse("2026-06-15T01:02:03Z")),
                "metadata timestamp");
    }

    private static void verifiesGetMessagesFilterSemantics() {
        RecordingSqlDbStore db = new RecordingSqlDbStore();
        SqlMessageStore store = new SqlMessageStore(db);
        db.sortedRows = List.of(linkedMap(
                "message_id", "msg-1",
                "user_id", "user-1",
                "scope_id", "scope-1",
                "session_id", "",
                "role", "assistant",
                "content", "[\"part\", {\"type\": \"text\"}]",
                "timestamp", ZonedDateTime.parse("2026-06-15T01:02:03Z")
        ));

        List<Map.Entry<BaseMessage, MessageMetadata>> messages = store.getMessages(
                linkedMap("user_id", "user-1", "scope_id", "", "session_id", ""),
                3,
                "timestamp",
                "desc"
        ).join();

        require(db.sortFilters.equals(linkedMap("user_id", "user-1", "session_id", "")),
                "empty session_id is included for get_messages");
        require("DESC".equals(db.sortOrder), "sort order uppercased");
        require(db.sortLimit == 3, "limit forwarded");
        require(messages.size() == 1, "message count");
        require(messages.get(0).getKey().getContent() instanceof List<?>, "structured JSON content restored");
    }

    private static void verifiesUpdateDeleteCountAndSchemaVersion() {
        RecordingSqlDbStore db = new RecordingSqlDbStore();
        SqlMessageStore store = new SqlMessageStore(db);

        require(store.getCryptoKey() == null, "default crypto key is null");
        byte[] key = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        store.setCryptoKey(key);
        key[0] = 'x';
        require(store.getCryptoKey()[0] == '0', "crypto key is defensively copied");
        store.setCryptoKey(null);

        store.updateMessage("msg-1", List.of("a", Map.of("b", 2))).join();
        require(db.updateConditions.equals(linkedMap("message_id", "msg-1")), "update conditions");
        require(String.valueOf(db.updateData.get("content")).startsWith("[\"a\", {\"b\": 2}]"), "list content json");

        db.sortedRows = List.of(
                linkedMap("message_id", "msg-1"),
                linkedMap("message_id", "msg-2")
        );
        int deleted = store.deleteMessages(linkedMap("user_id", "", "scope_id", "scope-1", "session_id", "")).join();
        require(deleted == 2, "delete returns pre-delete count");
        require(db.deleteConditions.equals(linkedMap("scope_id", "scope-1")),
                "delete_messages skips falsy filter values");

        store.deleteMessageById("msg-3").join();
        require(db.deleteConditions.equals(linkedMap("message_id", "msg-3")), "delete by id conditions");

        db.conditionRows = List.of(linkedMap("table_name", "user_message", "schema_version", "4"));
        require(store.getSchemaVersion().join() == 4, "schema version parsed");

        store.setSchemaVersion(5).join();
        require("memory_meta".equals(db.writeTable), "schema version writes memory_meta");
        require(db.writeData.equals(linkedMap("table_name", "user_message", "schema_version", "5")),
                "schema version write data");
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingSqlDbStore extends SqlDbStore {

        private String writeTable;
        private Map<String, Object> writeData;
        private final List<Map<String, Object>> writes = new ArrayList<>();
        private Map<String, ?> conditionConditions;
        private List<Map<String, Object>> conditionRows = List.of();
        private Map<String, Object> sortFilters;
        private String sortOrder;
        private int sortLimit;
        private List<Map<String, Object>> sortedRows = List.of();
        private Map<String, ?> updateConditions;
        private Map<String, Object> updateData;
        private Map<String, ?> deleteConditions;

        private RecordingSqlDbStore() {
            super(new BaseDbStore<>() {
                @Override
                public Object getAsyncEngine() {
                    return null;
                }
            });
        }

        @Override
        public CompletableFuture<Boolean> write(String table, Map<String, Object> data) {
            this.writeTable = table;
            this.writeData = new LinkedHashMap<>(data);
            this.writes.add(new LinkedHashMap<>(data));
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> conditionGet(
                String table,
                Map<String, ?> conditions,
                List<String> columns
        ) {
            require(columns == null, "columns are not requested");
            this.conditionConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(conditionRows);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> getWithSort(
                String table,
                Map<String, Object> filters,
                String sortBy,
                String order,
                int limit
        ) {
            this.sortFilters = new LinkedHashMap<>(filters);
            this.sortOrder = order;
            this.sortLimit = limit;
            return CompletableFuture.completedFuture(sortedRows);
        }

        @Override
        public CompletableFuture<Boolean> update(
                String table,
                Map<String, ?> conditions,
                Map<String, Object> data
        ) {
            this.updateConditions = new LinkedHashMap<>(conditions);
            this.updateData = new LinkedHashMap<>(data);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> delete(String table, Map<String, ?> conditions) {
            this.deleteConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exist(String table, Map<String, Object> conditions) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
