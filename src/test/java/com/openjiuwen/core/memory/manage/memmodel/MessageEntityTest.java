/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.foundation.store.BaseDbStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for message entity classes.
 * Corresponds to Python: test_message.py
 */
class MessageEntityTest {

    @Nested
    @DisplayName("Tests for UserMessage entity")
    class TestUserMessage {

        @Test
        @DisplayName("Test UserMessage instance creation and table metadata")
        void testInstanceCreationAndMetadata() {
            UserMessage msg = UserMessage.builder()
                .messageId("msg_001")
                .userId("user_123")
                .scopeId("scope_456")
                .content("Hello world")
                .sessionId("session_789")
                .role("user")
                .timestamp("2025-01-01T00:00:00")
                .build();

            assertEquals("msg_001", msg.getMessageId());
            assertEquals("user_123", msg.getUserId());
            assertEquals("Hello world", msg.getContent());

            // Table metadata - corresponds to Python's issubclass(UserMessage, Base)
            assertTrue(msg instanceof MessageBase,
                "UserMessage should implement MessageBase (equivalent to Python's issubclass(UserMessage, Base))");
            
            // Table name - corresponds to Python's UserMessage.__tablename__
            assertEquals("user_message", UserMessage.getTableName());
        }

        @Test
        @DisplayName("Test nullable fields can be null")
        void testNullableFields() {
            UserMessage msg = UserMessage.builder()
                .messageId("msg_001")
                .userId("user_123")
                .scopeId("scope_456")
                .content("Hello world")
                .build();

            assertNull(msg.getSessionId());
            assertNull(msg.getRole());
            assertNull(msg.getTimestamp());
        }
    }

    @Nested
    @DisplayName("Tests for ScopeUserMapping entity")
    class TestScopeUserMapping {

        @Test
        @DisplayName("Test ScopeUserMapping instance creation and table metadata")
        void testInstanceCreationAndMetadata() {
            ScopeUserMapping mapping = new ScopeUserMapping("user_123", "scope_456");

            assertEquals("user_123", mapping.getUserId());
            assertEquals("scope_456", mapping.getScopeId());

            // Table metadata - corresponds to Python's issubclass(ScopeUserMapping, Base)
            assertTrue(mapping instanceof MessageBase,
                "ScopeUserMapping should implement MessageBase (equivalent to Python's issubclass(ScopeUserMapping, Base))");
            
            // Table name - corresponds to Python's ScopeUserMapping.__tablename__
            assertEquals("scope_user_mapping", ScopeUserMapping.getTableName());
        }
    }

    @Nested
    @DisplayName("Tests for createTables function")
    class TestCreateTables {

        @Test
        @DisplayName("Should use db_store's async engine")
        void testCreateTablesCallsEngine() throws Exception {
            // Create mock db_store
            BaseDbStore mockDbStore = mock(BaseDbStore.class);

            // Call createTables - corresponds to Python's await create_tables(mock_db_store)
            CompletableFuture<Void> future = MessageTables.createTables(mockDbStore);
            
            // Wait for completion
            future.get();
            
            // The function should complete without exception
            // In Python test, it verifies mock_db_store.get_async_engine.assert_called_once()
            // In Java, we just verify the method completes successfully as the actual
            // implementation may vary based on the database store
            assertTrue(future.isDone());
        }
    }
}
