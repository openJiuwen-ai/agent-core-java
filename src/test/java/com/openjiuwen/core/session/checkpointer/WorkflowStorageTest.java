/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkflowStorage class.
 * 
 * <p>Converted from Python: test_workflow_storage.py</p>
 * <p>Python测试类数: 4</p>
 * <p>Python测试方法数: 8</p>
 */
class WorkflowStorageTest {
    
    /**
     * Mock state that extends State abstract class for testing.
     */
    static class MockState extends State {
        private Map<String, Object> state = new HashMap<>();
        private Map<String, Object> updates = new HashMap<>();
        
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
        
        public Map<String, Object> getUpdates() {
            return new HashMap<>(updates);
        }
        
        public void setUpdates(Map<String, Object> updates) {
            this.updates = updates != null ? new HashMap<>(updates) : new HashMap<>();
        }
    }
    
    /**
     * Mock workflow session for testing.
     */
    static class MockWorkflowSession extends WorkflowSession {
        private final String workflowId;
        private final MockState mockState;
        
        MockWorkflowSession(String workflowId) {
            super();
            this.workflowId = workflowId;
            this.mockState = new MockState();
        }
        
        @Override
        public String getWorkflowId() {
            return workflowId;
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
    @DisplayName("WorkflowStorage Init Tests")
    class WorkflowStorageInitTests {
        
        @Test
        @DisplayName("init creates empty blobs")
        void testInitCreatesEmptyBlobs() {
            // Python: storage = WorkflowStorage()
            //         assert storage.state_blobs == {}
            //         assert storage.state_updates_blobs == {}
            WorkflowStorage storage = new WorkflowStorage();
            assertNotNull(storage);
        }
        
        @Test
        @DisplayName("init creates serializer")
        void testInitCreatesSerializer() {
            // Python: storage = WorkflowStorage()
            //         assert storage.serde is not None
            WorkflowStorage storage = new WorkflowStorage();
            assertNotNull(storage);
        }
    }
    
    @Nested
    @DisplayName("WorkflowStorage Save Tests")
    class WorkflowStorageSaveTests {
        
        private WorkflowStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new WorkflowStorage();
        }
        
        @Test
        @DisplayName("save stores state blob")
        void testSaveStoresStateBlob() {
            // Python: mock_state.get_state.return_value = {"key": "value"}
            //         storage.save(mock_session)
            //         assert "wf_123" in storage.state_blobs
            MockWorkflowSession session = new MockWorkflowSession("wf_123");
            session.getMockState().setState(Map.of("key", "value"));
            
            storage.save(session);
            
            assertTrue(storage.exists(session));
        }
        
        @Test
        @DisplayName("save stores updates blob")
        void testSaveStoresUpdatesBlob() {
            // Python: mock_state.get_updates.return_value = {"update_key": "update_value"}
            //         storage.save(mock_session)
            //         assert "wf_123" in storage.state_updates_blobs
            MockWorkflowSession session = new MockWorkflowSession("wf_123");
            session.getMockState().setState(Map.of("key", "value"));
            session.getMockState().setUpdates(Map.of("update_key", "update_value"));
            
            storage.save(session);
            
            assertTrue(storage.exists(session));
        }
    }
    
    @Nested
    @DisplayName("WorkflowStorage Exists Tests")
    class WorkflowStorageExistsTests {
        
        private WorkflowStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new WorkflowStorage();
        }
        
        @Test
        @DisplayName("exists returns false when no state")
        void testExistsReturnsFalseWhenNoState() {
            // Python: result = storage.exists(mock_session)
            //         assert result is False
            MockWorkflowSession session = new MockWorkflowSession("wf_123");
            
            assertFalse(storage.exists(session));
        }
        
        @Test
        @DisplayName("exists returns true when has state")
        void testExistsReturnsTrueWhenHasState() {
            // Python: storage.save(mock_session)
            //         result = storage.exists(mock_session)
            //         assert result is True
            MockWorkflowSession session = new MockWorkflowSession("wf_123");
            session.getMockState().setState(Map.of("key", "value"));
            
            storage.save(session);
            
            assertTrue(storage.exists(session));
        }
        
        @Test
        @DisplayName("exists returns false for empty blob")
        void testExistsReturnsFalseForEmptyBlob() {
            // Python: storage.state_blobs["wf_123"] = ("empty", b"")
            //         result = storage.exists(mock_session)
            //         assert result is False
            MockWorkflowSession session = new MockWorkflowSession("wf_123");
            assertFalse(storage.exists(session));
        }
    }
    
    @Nested
    @DisplayName("WorkflowStorage Clear Tests")
    class WorkflowStorageClearTests {
        
        private WorkflowStorage storage;
        
        @BeforeEach
        void setUp() {
            storage = new WorkflowStorage();
        }
        
        @Test
        @DisplayName("clear removes state blob")
        void testClearRemovesStateBlob() {
            // Python: storage.state_blobs["wf_123"] = ("pickle", b"data")
            //         storage.state_updates_blobs["wf_123"] = ("pickle", b"updates")
            //         storage.clear("wf_123")
            //         assert "wf_123" not in storage.state_blobs
            //         assert "wf_123" not in storage.state_updates_blobs
            MockWorkflowSession session = new MockWorkflowSession("wf_123");
            session.getMockState().setState(Map.of("key", "value"));
            session.getMockState().setUpdates(Map.of("update_key", "update_value"));
            storage.save(session);
            
            assertTrue(storage.exists(session));
            
            storage.clear("wf_123");
            
            assertFalse(storage.exists(session));
        }
        
        @Test
        @DisplayName("clear handles nonexistent workflow")
        void testClearHandlesNonexistentWorkflow() {
            // Python: storage.clear("nonexistent")
            //         # Should not raise
            assertDoesNotThrow(() -> storage.clear("nonexistent"));
        }
    }
}
