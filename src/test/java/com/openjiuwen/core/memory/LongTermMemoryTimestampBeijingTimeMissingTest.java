/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.MessageManager;
import com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager;
import com.openjiuwen.core.memory.process.extract.Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's timestamp tests in
 * {@code tests/unit_tests/core/memory/test_timestamp_beijing_time.py}.</p>
 */
class LongTermMemoryTimestampBeijingTimeMissingTest {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LongTermMemory memory;
    private RecordingGenerator generator;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        memory = new LongTermMemory();
        generator = new RecordingGenerator();
        setField("sysMemConfig", new MemoryEngineConfig());
        setField("kvStore", new InMemoryKVStore());
        setField("messageManager", new MessageManager(new InMemoryMessageStore()));
        setField("scopeUserMappingManager", new RecordingScopeUserMappingManager());
        setField("generator", generator);
        setField("writeManager", new RecordingWriteManager());
        setField("baseLlm", fakeModel());
    }

    @Test
    void testAddMessagesTimestampBeijingTime() {
        LocalDateTime beforeCall = LocalDateTime.now();

        addMessages(null);

        assertThat(generator.capturedTimestamp).isNotNull();
        LocalDateTime parsedTimestamp = LocalDateTime.parse(generator.capturedTimestamp, TIMESTAMP_FORMAT);
        LocalDateTime afterCall = LocalDateTime.now();
        long beforeDiffSeconds = Math.abs(Duration.between(beforeCall, parsedTimestamp).toSeconds());
        long afterDiffSeconds = Math.abs(Duration.between(parsedTimestamp, afterCall).toSeconds());
        assertThat(Math.min(beforeDiffSeconds, afterDiffSeconds)).isLessThan(60);
    }

    @Test
    void testAddMessagesWithCustomTimestamp() {
        ZonedDateTime customTimestamp = ZonedDateTime.parse("2023-01-01T12:00:00Z");

        addMessages(customTimestamp);

        assertThat(generator.capturedTimestamp).isEqualTo("2023-01-01 12:00:00");
    }

    private void addMessages(ZonedDateTime timestamp) {
        memory.addMessages(
                List.of(new UserMessage("test")),
                AgentMemoryConfig.builder().enableLongTermMem(true).build(),
                "test_user",
                "test_scope",
                "test_session",
                timestamp,
                true,
                2
        ).join();
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        Field field = LongTermMemory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(memory, value);
    }

    private static Model fakeModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("[]")));
    }

    private static final class RecordingGenerator extends Generator {
        private String capturedTimestamp;

        private RecordingGenerator() {
            super(new DataIdManager());
        }

        @Override
        public CompletionStage<Map<String, List<BaseMemoryUnit>>> genAllMemory(Map<String, Object> kwargs) {
            capturedTimestamp = (String) kwargs.get("timestamp");
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class RecordingWriteManager extends WriteManager {
        private RecordingWriteManager() {
            super(Map.of(), null);
        }

        @Override
        public CompletionStage<List<BaseMemoryUnit>> addMemories(
                String userId,
                String scopeId,
                Map<String, List<BaseMemoryUnit>> memories,
                Model llm
        ) {
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

    private static final class InMemoryMessageStore extends BaseMessageStore {
        private final List<Map.Entry<BaseMessage, MessageMetadata>> rows = new ArrayList<>();

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
                    message.getRole()
            );
            rows.add(new AbstractMap.SimpleImmutableEntry<>(message, metadata));
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
                String orderDirection
        ) {
            List<Map.Entry<BaseMessage, MessageMetadata>> filtered = rows.stream()
                    .filter(row -> matches(row.getValue(), messageFilter))
                    .toList();
            List<Map.Entry<BaseMessage, MessageMetadata>> descending = new ArrayList<>(filtered);
            java.util.Collections.reverse(descending);
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
}
