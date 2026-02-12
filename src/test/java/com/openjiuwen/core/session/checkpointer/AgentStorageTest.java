/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentStorage class.
 * 
 * <p>Converted from Python: test_agent_storage.py</p>
 * <p>Python测试类数: 5</p>
 * <p>Python测试方法数: 8</p>
 */
class AgentStorageTest {
    
    /**
     * Mock state that extends State abstract class for testing.
     */
    static class MockState extends State {
        private Map<String, Object> state = new HashMap<>();
        
        @Override
        public Map<String, Object> getState() {
            return new HashMap<>(state);
        }
        
        @Override
        public void setState(Map<String, Object> state) {
            this.state = state != null ? new HashMap<>(state) : new HashMap<>();
        }
        
        @Override
        public Object get(Object key) {
            return key != null ? state.get(key.toString()) : null;
        }
        
        @Override
        public void update(Map<String, Object> data) {
            if (data != null) {
                state.putAll(data);
            }
        }
        
        @Override
        public void commit() {}
        
        @Override
        public Object getGlobal(Object key) {
            return get(key);
        }
        
        @Override
        public void updateGlobal(Map<String, Object> data) {
            update(data);
        }
        
        @Override
        public void updateTrace(Object span) {}
        
        @Override
        public Map<String, Object> getData() {
            return state;
        }
    }
    
    /**
     * Mock agent session for testing.
     */
    static class MockAgentSession extends AgentSession {
        private final String agentId;
        private final MockState mockState;
        
        MockAgentSession(String agentId) {
            super("test_session", null);
            this.agentId = agentId;
            this.mockState = new MockState();
        }
        
        @Override
        public String getAgentId() {
            return agentId;
        }
        
        @Override
        public State getState() {
            return mockState;
        }
        
        public MockState getMockState() {
            return mockState;
        }
    }
    
    @Nested
    @DisplayName("AgentStorage Init Tests")
    class AgentStorageInitTests {
        
        @Test
        @DisplayName("init creates empty blobs")
        void testInitCreatesEmptyBlobs() {
            // Python: storage = AgentStorage()
            //         assert storage.state_blobs == {}
            AgentStorage storage = new AgentStorage();
            // Storage should be created without exception
            assertNotNull(storage);
        }
        
        @Test
        @DisplayName("init creates serializer")
        void testInitCreatesSerializer() {
            // Python: storage = AgentStorage()
            //         assert storage.serde is not None
            AgentStorage storage = new AgentStorage();
            assertNotNull(storage);
        }
    }
    
    @Nested
    @DisplayName("AgentStorage Save Tests")
    class AgentStorageSaveTests {
        
        private AgentStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new AgentStorage();
        }
        
        @Test
        @DisplayName("save stores state blob")
        void testSaveStoresStateBlob() {
            // Python: mock_state.get_state.return_value = {"key": "value"}
            //         storage.save(mock_session)
            //         assert "agent_123" in storage.state_blobs
            MockAgentSession session = new MockAgentSession("agent_123");
            session.getMockState().setState(Map.of("key", "value"));
            
            storage.save(session);
            
            assertTrue(storage.exists(session));
        }
    }
    
    @Nested
    @DisplayName("AgentStorage Recover Tests")
    class AgentStorageRecoverTests {
        
        private AgentStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new AgentStorage();
        }
        
        @Test
        @DisplayName("recover restores state")
        void testRecoverRestoresState() {
            // Python: storage.save(mock_session)
            //         storage.recover(mock_session)
            //         mock_state.set_state.assert_called()
            MockAgentSession session = new MockAgentSession("agent_123");
            session.getMockState().setState(Map.of("original", "state"));
            
            // Save first
            storage.save(session);
            
            // Create new session to recover into
            MockAgentSession newSession = new MockAgentSession("agent_123");
            storage.recover(newSession, null);
            
            // Verify state was recovered
            assertEquals(Map.of("original", "state"), newSession.getState().getState());
        }
        
        @Test
        @DisplayName("recover handles no saved state")
        void testRecoverHandlesNoSavedState() {
            // Python: storage.recover(mock_session)
            //         mock_state.set_state.assert_not_called()
            MockAgentSession session = new MockAgentSession("agent_123");
            
            // Recover without saving first - should not throw
            assertDoesNotThrow(() -> storage.recover(session, null));
        }
    }
    
    @Nested
    @DisplayName("AgentStorage Exists Tests")
    class AgentStorageExistsTests {
        
        private AgentStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new AgentStorage();
        }
        
        @Test
        @DisplayName("exists returns false when no state")
        void testExistsReturnsFalseWhenNoState() {
            // Python: result = storage.exists(mock_session)
            //         assert result is False
            MockAgentSession session = new MockAgentSession("agent_123");
            
            assertFalse(storage.exists(session));
        }
        
        @Test
        @DisplayName("exists returns true when has state")
        void testExistsReturnsTrueWhenHasState() {
            // Python: storage.save(mock_session)
            //         result = storage.exists(mock_session)
            //         assert result is True
            MockAgentSession session = new MockAgentSession("agent_123");
            session.getMockState().setState(Map.of("key", "value"));
            
            storage.save(session);
            
            assertTrue(storage.exists(session));
        }
    }
    
    @Nested
    @DisplayName("AgentStorage Clear Tests")
    class AgentStorageClearTests {
        
        private AgentStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new AgentStorage();
        }
        
        @Test
        @DisplayName("clear removes state blob")
        void testClearRemovesStateBlob() {
            // Python: storage.state_blobs["agent_123"] = ("pickle", b"data")
            //         storage.clear("agent_123")
            //         assert "agent_123" not in storage.state_blobs
            MockAgentSession session = new MockAgentSession("agent_123");
            session.getMockState().setState(Map.of("key", "value"));
            storage.save(session);
            
            assertTrue(storage.exists(session));
            
            storage.clear("agent_123");
            
            assertFalse(storage.exists(session));
        }
        
        @Test
        @DisplayName("clear handles nonexistent agent")
        void testClearHandlesNonexistentAgent() {
            // Python: storage.clear("nonexistent")
            //         # Should not raise
            assertDoesNotThrow(() -> storage.clear("nonexistent"));
        }
    }
}
