/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class BaseMessageStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void metadataUsesPythonFieldNamesDuringJsonRoundTrip() throws Exception {
        MessageMetadata metadata = new MessageMetadata(
                "msg-1",
                "user-1",
                "scope-1",
                "session-1",
                ZonedDateTime.parse("2026-06-08T04:00:00Z"),
                "assistant"
        );

        String json = MAPPER.writeValueAsString(metadata);
        MessageMetadata restored = MAPPER.readValue(json, MessageMetadata.class);

        assertThat(json).contains("message_id", "user_id", "scope_id", "session_id", "message_type");
        assertThat(restored).isEqualTo(metadata);
    }

    @Test
    void subclassExposesAsyncMessageStoreContract() {
        BaseMessageStore store = new BaseMessageStore() {
            @Override
            public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
                return CompletableFuture.completedFuture("msg-1");
            }

            @Override
            public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
                return CompletableFuture.completedFuture(List.of("msg-1", "msg-2"));
            }

            @Override
            public CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId) {
                return CompletableFuture.completedFuture(new AbstractMap.SimpleEntry<>(
                        new BaseMessage("user", "hello"),
                        new MessageMetadata("msg-1", "user-1", "scope-1", "session-1", null, "user")
                ));
            }

            @Override
            public CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
                    Map<String, Object> messageFilter,
                    int limit,
                    String orderBy,
                    String orderDirection
            ) {
                return CompletableFuture.completedFuture(List.of(getMessageById("msg-1").join()));
            }

            @Override
            public CompletableFuture<Boolean> updateMessage(String messageId, Object content) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> deleteMessageById(String messageId) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter) {
                return CompletableFuture.completedFuture(1);
            }

            @Override
            public CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter) {
                return CompletableFuture.completedFuture(2);
            }

            @Override
            public CompletableFuture<Integer> getSchemaVersion() {
                return CompletableFuture.completedFuture(3);
            }

            @Override
            public CompletableFuture<Void> setSchemaVersion(int version) {
                return CompletableFuture.completedFuture(null);
            }
        };

        Map.Entry<BaseMessage, MessageMetadata> entry = store.getMessageById("msg-1").join();

        assertThat(store.addMessage(Map.of()).join()).isEqualTo("msg-1");
        assertThat(store.addMessages(List.of(Map.of(), Map.of())).join()).containsExactly("msg-1", "msg-2");
        assertThat(entry.getKey().getContent()).isEqualTo("hello");
        assertThat(entry.getValue().getMessageId()).isEqualTo("msg-1");
        assertThat(store.getMessages(Map.of("session_id", "session-1"), 10, "timestamp", "desc").join()).hasSize(1);
        assertThat(store.updateMessage("msg-1", "updated").join()).isTrue();
        assertThat(store.deleteMessageById("msg-1").join()).isTrue();
        assertThat(store.deleteMessages(Map.of()).join()).isEqualTo(1);
        assertThat(store.countMessages(Map.of()).join()).isEqualTo(2);
        assertThat(store.getSchemaVersion().join()).isEqualTo(3);
    }
}
