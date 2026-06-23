/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.process.extract.Generator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestSqlMessageStore}, {@code TestMessageManagerWithStore}, and
 * {@code TestLongTermMemoryMessageStore} in
 * {@code tests/unit_tests/core/memory/test_message_store.py}.
 */
class MessageStoreMissingTest {

    private static final ZonedDateTime BASE_TIME = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    @Test
    void sqlMessageStoreAddMessage() {
        SqlMessageStore store = sqlMessageStore();

        String messageId = store.addMessage(messageAdd(new UserMessage("Hello, world!"),
                "user1", "scope1", "session1", BASE_TIME)).join();

        assertThat(messageId).isNotNull().startsWith("msg_");
    }

    @Test
    void sqlMessageStoreAddMessages() {
        SqlMessageStore store = sqlMessageStore();

        List<String> messageIds = store.addMessages(List.of(
                messageAdd(new UserMessage("Hello"), "user1", "scope1", "session1", BASE_TIME),
                messageAdd(new AssistantMessage("Hi there!"), "user1", "scope1", "session1", BASE_TIME.plusSeconds(1)),
                messageAdd(new SystemMessage("System prompt"), "user1", "scope1", "session1", BASE_TIME.plusSeconds(2))
        )).join();

        assertThat(messageIds).hasSize(3).allSatisfy(id -> assertThat(id).startsWith("msg_"));
    }

    @Test
    void sqlMessageStoreGetMessageById() {
        SqlMessageStore store = sqlMessageStore();
        String messageId = store.addMessage(messageAdd(new UserMessage("Test message"),
                "user1", "scope1", "session1", BASE_TIME)).join();

        Map.Entry<BaseMessage, MessageMetadata> result = store.getMessageById(messageId).join();

        assertThat(result.getKey().getContent()).isEqualTo("Test message");
        assertThat(result.getValue().getMessageId()).isEqualTo(messageId);
        assertThat(result.getValue().getUserId()).isEqualTo("user1");
        assertThat(result.getValue().getScopeId()).isEqualTo("scope1");
        assertThat(result.getValue().getSessionId()).isEqualTo("session1");
    }

    @Test
    void sqlMessageStoreGetMessages() {
        SqlMessageStore store = sqlMessageStore();
        store.addMessages(List.of(
                messageAdd(new UserMessage("Message 1"), "user1", "scope1", "session1", BASE_TIME),
                messageAdd(new AssistantMessage("Response 1"), "user1", "scope1", "session1", BASE_TIME.plusSeconds(1)),
                messageAdd(new UserMessage("Message 2"), "user1", "scope1", "session1", BASE_TIME.plusSeconds(2)),
                messageAdd(new AssistantMessage("Response 2"), "user1", "scope1", "session1", BASE_TIME.plusSeconds(3))
        )).join();

        List<Map.Entry<BaseMessage, MessageMetadata>> messages = store.getMessages(
                linkedMap("user_id", "user1", "scope_id", "scope1", "session_id", "session1"),
                10,
                "timestamp",
                "asc").join();

        assertThat(messages).extracting(row -> row.getKey().getContent())
                .containsExactly("Message 1", "Response 1", "Message 2", "Response 2");
    }

    @Test
    void sqlMessageStoreUpdateMessage() {
        SqlMessageStore store = sqlMessageStore();
        String messageId = store.addMessage(messageAdd(new UserMessage("Original content"),
                "user1", "scope1", "session1", BASE_TIME)).join();

        Boolean success = store.updateMessage(messageId, "Updated content").join();

        assertThat(success).isTrue();
        assertThat(store.getMessageById(messageId).join().getKey().getContent()).isEqualTo("Updated content");
    }

    @Test
    void sqlMessageStoreDeleteMessage() {
        SqlMessageStore store = sqlMessageStore();
        String messageId = store.addMessage(messageAdd(new UserMessage("Message to delete"),
                "user1", "scope1", "session1", BASE_TIME)).join();

        Boolean success = store.deleteMessageById(messageId).join();

        assertThat(success).isTrue();
        assertThatThrownBy(() -> store.getMessageById(messageId).join()).hasMessageContaining("not found");
    }

    @Test
    void messageManagerAddMessage() {
        MessageManager manager = new MessageManager(sqlMessageStore());

        String messageId = manager.add(MessageAddRequest.builder()
                .userId("user1")
                .scopeId("scope1")
                .content("Hello from MessageManager")
                .role("user")
                .sessionId("session1")
                .timestamp(BASE_TIME)
                .build()).join();

        assertThat(messageId).isNotNull();
    }

    @Test
    void messageManagerGetMessages() {
        MessageManager manager = new MessageManager(sqlMessageStore());
        for (int i = 0; i < 3; i++) {
            manager.add(MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .content("Message " + (i + 1))
                    .role(i % 2 == 0 ? "user" : "assistant")
                    .sessionId("session1")
                    .timestamp(BASE_TIME.plusSeconds(i))
                    .build()).join();
        }

        List<Map.Entry<BaseMessage, ZonedDateTime>> messages =
                manager.get("user1", "scope1", "session1", 10).join();

        assertThat(messages).extracting(row -> row.getKey().getContent())
                .containsExactly("Message 1", "Message 2", "Message 3");
    }

    @Test
    void messageManagerGetMessageById() {
        MessageManager manager = new MessageManager(sqlMessageStore());
        String messageId = manager.add(MessageAddRequest.builder()
                .userId("user1")
                .scopeId("scope1")
                .content("Test message")
                .role("user")
                .sessionId("session1")
                .timestamp(BASE_TIME)
                .build()).join();

        Map.Entry<BaseMessage, ZonedDateTime> result = manager.getById(messageId).join();

        assertThat(result.getKey().getContent()).isEqualTo("Test message");
        assertThat(result.getValue()).isNotNull();
    }

    @Test
    void messageManagerDeleteMessages() {
        MessageManager manager = new MessageManager(sqlMessageStore());
        manager.add(MessageAddRequest.builder()
                .userId("user1")
                .scopeId("scope1")
                .content("Message to delete")
                .role("user")
                .sessionId("session1")
                .timestamp(BASE_TIME)
                .build()).join();

        Boolean success = manager.deleteByUserAndScope("user1", "scope1").join();
        List<Map.Entry<BaseMessage, ZonedDateTime>> messages =
                manager.get("user1", "scope1", "session1", 10).join();

        assertThat(success).isTrue();
        assertThat(messages).isEmpty();
    }

    @Test
    void longTermMemoryAddMessagesAndGetRecent() throws ReflectiveOperationException {
        ConfiguredMemory configured = configuredMemory();

        configured.memory.addMessages(
                List.of(new UserMessage("Hello from LTM test"), new AssistantMessage("Hi from LTM test")),
                new AgentMemoryConfig(),
                "user_ltm",
                "scope_ltm",
                "session_ltm",
                BASE_TIME,
                false,
                2).join();
        List<BaseMessage> recent = configured.memory.getRecentMessages(
                "user_ltm", "scope_ltm", "session_ltm", 10).join();

        assertThat(recent).extracting(BaseMessage::getContent)
                .containsExactly("Hello from LTM test", "Hi from LTM test");
    }

    @Test
    void longTermMemoryGetMessageById() throws ReflectiveOperationException {
        ConfiguredMemory configured = configuredMemory();
        configured.memory.addMessages(
                List.of(new UserMessage("Find me by ID")),
                new AgentMemoryConfig(),
                "user_ltm2",
                "scope_ltm2",
                "session_ltm2",
                BASE_TIME,
                false,
                2).join();
        String messageId = configured.store.messageIds.getFirst();

        Map.Entry<BaseMessage, ZonedDateTime> retrieved = configured.memory.getMessageById(messageId).join();

        assertThat(retrieved.getKey().getContent()).isEqualTo("Find me by ID");
        assertThat(retrieved.getValue()).isNotNull();
    }

    @Test
    void longTermMemoryDeleteMessages() throws ReflectiveOperationException {
        ConfiguredMemory configured = configuredMemory();
        configured.memory.addMessages(
                List.of(new UserMessage("To be deleted")),
                new AgentMemoryConfig(),
                "user_ltm3",
                "scope_ltm3",
                "session_ltm3",
                BASE_TIME,
                false,
                2).join();

        configured.memory.deleteMessagesByUserAndScope("user_ltm3", "scope_ltm3").join();
        List<BaseMessage> recent = configured.memory.getRecentMessages(
                "user_ltm3", "scope_ltm3", "session_ltm3", 10).join();

        assertThat(recent).isEmpty();
    }

    private static SqlMessageStore sqlMessageStore() {
        return new SqlMessageStore(new RecordingSqlDbStore());
    }

    private static Map<String, Object> messageAdd(BaseMessage message,
                                                  String userId,
                                                  String scopeId,
                                                  String sessionId,
                                                  ZonedDateTime timestamp) {
        return linkedMap(
                "message", message,
                "user_id", userId,
                "scope_id", scopeId,
                "session_id", sessionId,
                "timestamp", timestamp);
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private static ConfiguredMemory configuredMemory() throws ReflectiveOperationException {
        LongTermMemory memory = new LongTermMemory();
        InMemoryMessageStore store = new InMemoryMessageStore();
        setField(memory, "sysMemConfig", new MemoryEngineConfig());
        setField(memory, "kvStore", new InMemoryKVStore());
        setField(memory, "messageManager", new MessageManager(store));
        setField(memory, "scopeUserMappingManager", new RecordingScopeUserMappingManager());
        setField(memory, "generator", new NoopGenerator());
        setField(memory, "writeManager", new NoopWriteManager());
        setField(memory, "baseLlm", fakeModel());
        return new ConfiguredMemory(memory, store);
    }

    private static void setField(LongTermMemory memory, String name, Object value) throws ReflectiveOperationException {
        Field field = LongTermMemory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(memory, value);
    }

    private static Model fakeModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("[]")));
    }

    private record ConfiguredMemory(LongTermMemory memory, InMemoryMessageStore store) {
    }

    private static final class RecordingSqlDbStore extends SqlDbStore {
        private final List<Map<String, Object>> rows = new ArrayList<>();

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
            rows.add(new LinkedHashMap<>(data));
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> conditionGet(
                String table,
                Map<String, ?> conditions,
                List<String> columns) {
            return CompletableFuture.completedFuture(rows.stream()
                    .filter(row -> matches(row, conditions))
                    .map(LinkedHashMap::new)
                    .map(map -> (Map<String, Object>) map)
                    .toList());
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> getWithSort(
                String table,
                Map<String, Object> filters,
                String sortBy,
                String order,
                int limit) {
            Comparator<Map<String, Object>> comparator =
                    Comparator.comparing(row -> (ZonedDateTime) row.get("timestamp"));
            if ("DESC".equalsIgnoreCase(order)) {
                comparator = comparator.reversed();
            }
            return CompletableFuture.completedFuture(rows.stream()
                    .filter(row -> matches(row, filters))
                    .sorted(comparator)
                    .limit(limit)
                    .map(LinkedHashMap::new)
                    .map(map -> (Map<String, Object>) map)
                    .toList());
        }

        @Override
        public CompletableFuture<Boolean> update(String table, Map<String, ?> conditions, Map<String, Object> data) {
            for (Map<String, Object> row : rows) {
                if (matches(row, conditions)) {
                    row.putAll(data);
                }
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> delete(String table, Map<String, ?> conditions) {
            rows.removeIf(row -> matches(row, conditions));
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exist(String table, Map<String, Object> conditions) {
            return CompletableFuture.completedFuture(rows.stream().anyMatch(row -> matches(row, conditions)));
        }

        private static boolean matches(Map<String, Object> row, Map<String, ?> conditions) {
            if (conditions == null || conditions.isEmpty()) {
                return true;
            }
            for (Map.Entry<String, ?> entry : conditions.entrySet()) {
                Object expected = entry.getValue();
                if (expected instanceof List<?> values) {
                    if (!values.contains(row.get(entry.getKey()))) {
                        return false;
                    }
                } else if (!expected.equals(row.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class InMemoryMessageStore extends BaseMessageStore {
        private final List<Map.Entry<BaseMessage, MessageMetadata>> rows = new ArrayList<>();
        private final List<String> messageIds = new ArrayList<>();

        @Override
        public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
            String id = "msg-" + (rows.size() + 1);
            BaseMessage message = (BaseMessage) messageAdd.get("message");
            MessageMetadata metadata = new MessageMetadata(
                    id,
                    (String) messageAdd.get("user_id"),
                    (String) messageAdd.get("scope_id"),
                    (String) messageAdd.get("session_id"),
                    (ZonedDateTime) messageAdd.get("timestamp"),
                    message.getRole());
            rows.add(new AbstractMap.SimpleImmutableEntry<>(message, metadata));
            messageIds.add(id);
            return CompletableFuture.completedFuture(id);
        }

        @Override
        public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> messageAdd : messageAdds) {
                ids.add(addMessage(messageAdd).join());
            }
            return CompletableFuture.completedFuture(ids);
        }

        @Override
        public CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId) {
            return rows.stream()
                    .filter(row -> messageId.equals(row.getValue().getMessageId()))
                    .findFirst()
                    .map(CompletableFuture::completedFuture)
                    .orElseGet(() -> CompletableFuture.completedFuture(null));
        }

        @Override
        public CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
                Map<String, Object> messageFilter,
                int limit,
                String orderBy,
                String orderDirection) {
            List<Map.Entry<BaseMessage, MessageMetadata>> filtered = rows.stream()
                    .filter(row -> matches(row.getValue(), messageFilter))
                    .toList();
            List<Map.Entry<BaseMessage, MessageMetadata>> descending = new ArrayList<>(filtered);
            descending.sort(Comparator.comparing(
                    (Map.Entry<BaseMessage, MessageMetadata> row) -> row.getValue().getTimestamp()).reversed());
            return CompletableFuture.completedFuture(descending.subList(0, Math.min(limit, descending.size())));
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
            int before = rows.size();
            rows.removeIf(row -> matches(row.getValue(), messageFilter));
            return CompletableFuture.completedFuture(before - rows.size());
        }

        @Override
        public CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter) {
            return CompletableFuture.completedFuture((int) rows.stream()
                    .filter(row -> matches(row.getValue(), messageFilter))
                    .count());
        }

        @Override
        public CompletableFuture<Integer> getSchemaVersion() {
            return CompletableFuture.completedFuture(1);
        }

        @Override
        public CompletableFuture<Void> setSchemaVersion(int version) {
            return CompletableFuture.completedFuture(null);
        }

        private static boolean matches(MessageMetadata metadata, Map<String, Object> filter) {
            if (filter == null || filter.isEmpty()) {
                return true;
            }
            return matchesValue(metadata.getUserId(), filter.get("user_id"))
                    && matchesValue(metadata.getScopeId(), filter.get("scope_id"))
                    && matchesValue(metadata.getSessionId(), filter.get("session_id"));
        }

        private static boolean matchesValue(String actual, Object expected) {
            return expected == null || expected.equals(actual);
        }
    }

    private static final class NoopGenerator extends Generator {
        private NoopGenerator() {
            super(new DataIdManager());
        }

        @Override
        public CompletionStage<Map<String, List<BaseMemoryUnit>>> genAllMemory(Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class NoopWriteManager extends WriteManager {
        private NoopWriteManager() {
            super(Map.of(), null);
        }

        @Override
        public CompletionStage<List<BaseMemoryUnit>> addMemories(
                String userId,
                String scopeId,
                Map<String, List<BaseMemoryUnit>> memories,
                Model llm) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class RecordingScopeUserMappingManager extends ScopeUserMappingManager {
        private RecordingScopeUserMappingManager() {
            super(null);
        }

        @Override
        public CompletableFuture<Void> add(String userId, String scopeId) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
