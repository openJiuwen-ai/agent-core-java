/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for session_id handling in memory functionality.
 * Mirrors Python's tests/unit_tests/core/memory/test_session_id_in_memory.py
 * 
 * Note: Full LLMAgent integration tests are complex and require mocking.
 * This test focuses on LongTermMemory.addMessages session_id handling.
 */
class TestSessionIdInMemory {

    @BeforeEach
    void setUp() {
        LongTermMemory.resetInstance();
    }

    @AfterEach
    void tearDown() {
        LongTermMemory.resetInstance();
    }

    @Nested
    @DisplayName("SessionIdInMemory tests")
    class SessionIdTests {

        @Test
        @DisplayName("test add messages with session id")
        void testAddMessagesWithSessionId() {
            // Test that addMessages accepts session_id parameter.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();
            
            // Register stores first (required for memory operations)
            mem.registerStore(kvStore, null, null, null);

            // Create test messages
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello, how are you?"));
            messages.add(new AssistantMessage("Test response"));

            String testSessionId = "test_session_123";
            String testUserId = "test_user_456";
            String testScopeId = "test_scope_id";

            AgentMemoryConfig agentConfig = new AgentMemoryConfig();
            agentConfig.setEnableLongTermMem(false); // Disable to simplify test

            // Call addMessages with session_id
            // Note: This test validates that session_id is accepted as parameter
            mem.addMessages(messages, agentConfig, testUserId, testScopeId, 
                    testSessionId, OffsetDateTime.now(), false, 0);

            // No exception thrown means session_id was accepted
        }

        @Test
        @DisplayName("test session id parameter is accepted")
        void testSessionIdParameterAccepted() {
            // Test that session_id parameter exists in addMessages signature.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();
            
            mem.registerStore(kvStore, null, null, null);

            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("What's the weather today?"));
            messages.add(new AssistantMessage("The weather is sunny."));

            String testSessionId = "test_stream_session_789";

            AgentMemoryConfig agentConfig = new AgentMemoryConfig();
            agentConfig.setEnableLongTermMem(false);

            // Call with different session_id
            mem.addMessages(messages, agentConfig, "user_789", "scope_789", 
                    testSessionId, OffsetDateTime.now(), false, 0);

            // Test passed - session_id parameter is accepted
        }

        @Test
        @DisplayName("test empty session id")
        void testEmptySessionId() {
            // Test that empty session_id is handled.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();
            
            mem.registerStore(kvStore, null, null, null);

            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Test message"));

            AgentMemoryConfig agentConfig = new AgentMemoryConfig();
            agentConfig.setEnableLongTermMem(false);

            // Call with empty session_id
            mem.addMessages(messages, agentConfig, "user", "scope", 
                    "", OffsetDateTime.now(), false, 0);

            // No exception thrown means empty session_id is handled
        }

        @Test
        @DisplayName("test null session id")
        void testNullSessionId() {
            // Test that null session_id is handled.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();
            
            mem.registerStore(kvStore, null, null, null);

            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Test message"));

            AgentMemoryConfig agentConfig = new AgentMemoryConfig();
            agentConfig.setEnableLongTermMem(false);

            // Call with null session_id
            mem.addMessages(messages, agentConfig, "user", "scope", 
                    null, OffsetDateTime.now(), false, 0);

            // No exception thrown means null session_id is handled
        }
    }
}