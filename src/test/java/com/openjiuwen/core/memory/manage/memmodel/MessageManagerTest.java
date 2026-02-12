/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageManager.
 * Corresponds to Python: test_message_manager.py
 */
@DisplayName("MessageManager Tests")
class MessageManagerTest {

    private SqlDbStore mockSqlDbStore;
    private DataIdManager dataIdManager;
    private MessageManager messageManager;
    private MessageManager encryptedMessageManager;

    private static final byte[] NO_CRYPTO_KEY = new byte[0];
    private static final byte[] ENCRYPTED_CRYPTO_KEY = "1234567890abcdef1234567890abcdef".getBytes();

    private List<Map<String, Object>> storedData;

    @BeforeEach
    void setUp() {
        storedData = new ArrayList<>();

        mockSqlDbStore = mock(SqlDbStore.class);

        // Mock write
        when(mockSqlDbStore.write(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> data = invocation.getArgument(1);
            storedData.add(new HashMap<>(data));
            return CompletableFuture.completedFuture(true);
        });

        // Mock getWithSort
        when(mockSqlDbStore.getWithSort(anyString(), anyMap(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    Map<String, Object> filters = invocation.getArgument(1);
                    String order = invocation.getArgument(3);
                    int limit = invocation.getArgument(4);

                    List<Map<String, Object>> results = new ArrayList<>();
                    for (Map<String, Object> item : storedData) {
                        boolean match = true;
                        for (Map.Entry<String, Object> entry : filters.entrySet()) {
                            if (!Objects.equals(item.get(entry.getKey()), entry.getValue())) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            results.add(item);
                        }
                    }

                    // Sort by timestamp
                    results.sort((a, b) -> {
                        String ta = String.valueOf(a.get("timestamp"));
                        String tb = String.valueOf(b.get("timestamp"));
                        return "DESC".equals(order) ? tb.compareTo(ta) : ta.compareTo(tb);
                    });

                    return CompletableFuture.completedFuture(
                            results.subList(0, Math.min(limit, results.size()))
                    );
                });

        // Mock conditionGet
        when(mockSqlDbStore.conditionGet(anyString(), anyMap(), any()))
                .thenAnswer(invocation -> {
                    Map<String, List<Object>> conditions = invocation.getArgument(1);
                    List<Map<String, Object>> results = new ArrayList<>();
                    for (Map<String, Object> item : storedData) {
                        boolean match = true;
                        for (Map.Entry<String, List<Object>> entry : conditions.entrySet()) {
                            if (!entry.getValue().contains(item.get(entry.getKey()))) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            results.add(item);
                        }
                    }
                    return CompletableFuture.completedFuture(results);
                });

        dataIdManager = new DataIdManager();
        messageManager = new MessageManager(mockSqlDbStore, dataIdManager, NO_CRYPTO_KEY);
        encryptedMessageManager = new MessageManager(mockSqlDbStore, dataIdManager, ENCRYPTED_CRYPTO_KEY);
    }

    @Nested
    @DisplayName("TestMessageManagerAdd")
    class TestMessageManagerAdd {

        @Test
        @DisplayName("Test successful message addition")
        void testAddSuccess() throws Exception {
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .content("Hello world")
                    .role("user")
                    .build();

            String result = messageManager.add(req).get();

            assertNotNull(result);
            assertEquals(24, result.length()); // ID length
            verify(mockSqlDbStore).write(eq("user_message"), anyMap());
        }

        @Test
        @DisplayName("Test add with missing user_id raises error")
        void testAddMissingUserIdRaisesError() {
            MessageAddRequest req = MessageAddRequest.builder()
                    .scopeId("scope1")
                    .content("Hello")
                    .build();

            assertThrows(BaseError.class, () -> messageManager.add(req).get());
        }

        @Test
        @DisplayName("Test add with missing scope_id raises error")
        void testAddMissingScopeIdRaisesError() {
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .content("Hello")
                    .build();

            assertThrows(BaseError.class, () -> messageManager.add(req).get());
        }

        @Test
        @DisplayName("Test add with missing content raises error")
        void testAddMissingContentRaisesError() {
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .build();

            assertThrows(BaseError.class, () -> messageManager.add(req).get());
        }

        @Test
        @DisplayName("Test content is encrypted when crypto_key is set")
        void testAddEncryptsContent() throws Exception {
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .content("secret message")
                    .build();

            encryptedMessageManager.add(req).get();

            // Verify the stored content is not plaintext
            assertFalse(storedData.isEmpty());
            String storedContent = (String) storedData.get(0).get("content");
            assertNotEquals("secret message", storedContent);
        }
    }

    @Nested
    @DisplayName("TestMessageManagerGet")
    class TestMessageManagerGet {

        @Test
        @DisplayName("Test get returns messages in chronological order")
        void testGetReturnsMessages() throws Exception {
            // Add messages
            for (int i = 0; i < 3; i++) {
                MessageAddRequest req = MessageAddRequest.builder()
                        .userId("user1")
                        .scopeId("scope1")
                        .content("message " + i)
                        .role("user")
                        .build();
                messageManager.add(req).get();
            }

            List<MessageManager.MessageWithTimestamp> result = messageManager.get(
                    "user1", "scope1", null, 10
            ).get();

            assertEquals(3, result.size());
            // Each result is a tuple (BaseMessage, Instant)
            for (MessageManager.MessageWithTimestamp msg : result) {
                assertTrue(((String) msg.message().getContent()).startsWith("message"));
            }
        }

        @Test
        @DisplayName("Test get with message_len <= 0 raises error")
        void testGetInvalidMessageLenRaisesError() {
            assertThrows(BaseError.class, () ->
                    messageManager.get("user1", "scope1", null, 0).get()
            );
        }

        @Test
        @DisplayName("Test get with negative message_len raises error")
        void testGetNegativeMessageLenRaisesError() {
            assertThrows(BaseError.class, () ->
                    messageManager.get("user1", "scope1", null, -1).get()
            );
        }

        @Test
        @DisplayName("Test get respects message_len limit")
        void testGetLimitsResults() throws Exception {
            // Add 5 messages
            for (int i = 0; i < 5; i++) {
                MessageAddRequest req = MessageAddRequest.builder()
                        .userId("user1")
                        .scopeId("scope1")
                        .content("message " + i)
                        .role("user")
                        .build();
                messageManager.add(req).get();
            }

            List<MessageManager.MessageWithTimestamp> result = messageManager.get(
                    "user1", "scope1", null, 3
            ).get();

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Test get decrypts content when crypto_key is set")
        void testGetDecryptsContent() throws Exception {
            // Add encrypted message
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .content("secret content")
                    .build();
            encryptedMessageManager.add(req).get();

            List<MessageManager.MessageWithTimestamp> result = encryptedMessageManager.get(
                    "user1", "scope1", null, 10
            ).get();

            assertEquals(1, result.size());
            // Content should be decrypted
            assertEquals("secret content", result.get(0).message().getContent());
        }
    }

    @Nested
    @DisplayName("TestMessageManagerGetById")
    class TestMessageManagerGetById {

        @Test
        @DisplayName("Test get_by_id returns correct message")
        void testGetByIdSuccess() throws Exception {
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .content("specific message")
                    .role("assistant")
                    .build();
            String msgId = messageManager.add(req).get();

            Optional<MessageManager.MessageWithTimestamp> result = messageManager.getById(msgId).get();

            assertTrue(result.isPresent());
            assertEquals("specific message", result.get().message().getContent());
            assertEquals("assistant", result.get().message().getRole());
        }

        @Test
        @DisplayName("Test get_by_id returns None for nonexistent ID")
        void testGetByIdNotFoundReturnsNone() throws Exception {
            Optional<MessageManager.MessageWithTimestamp> result = messageManager.getById("nonexistent_id").get();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Test get_by_id decrypts content")
        void testGetByIdDecryptsContent() throws Exception {
            MessageAddRequest req = MessageAddRequest.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .content("encrypted content")
                    .build();
            String msgId = encryptedMessageManager.add(req).get();

            Optional<MessageManager.MessageWithTimestamp> result = encryptedMessageManager.getById(msgId).get();

            assertTrue(result.isPresent());
            assertEquals("encrypted content", result.get().message().getContent());
        }
    }
}

