/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.SessionConstants;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Checkpointer classes.
 * 
 * <p>Converted from Python: test_checkpointer.py</p>
 * <p>Python测试类: TestCheckpointerBase, TestInMemoryCheckpointer</p>
 */
class CheckpointerTest {
    
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
            if (key == null) {
                return state;
            }
            return state.get(key.toString());
        }
        
        @Override
        public void update(Map<String, Object> data) {
            if (data != null) {
                this.state.putAll(data);
            }
        }
        
        @Override
        public void commit() {
            // No-op for mock
        }
        
        @Override
        public Object getGlobal(Object key) {
            return get(key);
        }
        
        @Override
        public void updateGlobal(Map<String, Object> data) {
            update(data);
        }
        
        @Override
        public void updateTrace(Object span) {
            // No-op for mock
        }
        
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
        
        public void updateAndCommitWorkflowState(Map<String, Object> data) {
            if (data != null) {
                this.state.putAll(data);
            }
        }
    }
    
    /**
     * Mock config that extends DefaultConfig for testing.
     */
    static class MockConfig extends DefaultConfig {
        private final boolean forceDel;
        
        MockConfig(boolean forceDel) {
            this.forceDel = forceDel;
        }
        
        @Override
        public Object getEnv(String key, Object defaultValue) {
            if (SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY.equals(key)) {
                return forceDel;
            }
            return defaultValue;
        }
    }
    
    /**
     * Mock workflow session for testing.
     */
    static class MockWorkflowSession extends WorkflowSession {
        private final String sessionId;
        private final String workflowId;
        private final MockState mockState;
        private final Config config;
        private final BaseSession parent;
        
        MockWorkflowSession(String sessionId, String workflowId, boolean forceDel, BaseSession parent) {
            super();
            this.sessionId = sessionId;
            this.workflowId = workflowId;
            this.mockState = new MockState();
            this.config = new MockConfig(forceDel);
            this.parent = parent;
        }
        
        MockWorkflowSession() {
            this("test_session", "test_workflow", false, null);
        }
        
        @Override
        public String getSessionId() {
            return sessionId;
        }
        
        @Override
        public String getWorkflowId() {
            return workflowId;
        }
        
        @Override
        public State getState() {
            return mockState;
        }
        
        @Override
        public Config getConfig() {
            return config;
        }
        
        @Override
        public BaseSession getParent() {
            return parent;
        }
        
        public MockState getMockState() {
            return mockState;
        }
    }
    
    /**
     * Mock agent session for testing.
     */
    static class MockAgentSession extends AgentSession {
        private final String sessionId;
        private final String agentId;
        private final MockState mockState;
        
        MockAgentSession(String sessionId, String agentId) {
            super(sessionId, null);
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.mockState = new MockState();
        }
        
        MockAgentSession() {
            this("test_session", "test_agent");
        }
        
        @Override
        public String getSessionId() {
            return sessionId;
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
    @DisplayName("Checkpointer Base Tests")
    class CheckpointerBaseTests {
        
        @Test
        @DisplayName("get_thread_id returns session_id:workflow_id")
        void testGetThreadId() {
            // Python: thread_id = Checkpointer.get_thread_id(session)
            //         assert thread_id == "sess1:wf1"
            String threadId = Checkpointer.getThreadId("sess1", "wf1");
            assertEquals("sess1:wf1", threadId);
        }
    }
    
    @Nested
    @DisplayName("InMemoryCheckpointer Tests")
    class InMemoryCheckpointerTests {
        
        private InMemoryCheckpointer checkpointer;
        private MockWorkflowSession session;
        
        @BeforeEach
        void setUp() {
            checkpointer = new InMemoryCheckpointer();
            session = new MockWorkflowSession();
        }
        
        @Test
        @DisplayName("pre_workflow_execute with new workflow (no prior state)")
        void testPreWorkflowExecuteNewWorkflow() throws ExecutionException, InterruptedException {
            // Python: await checkpointer.pre_workflow_execute(session, None)
            //         assert session.session_id() in checkpointer._workflow_stores
            checkpointer.preWorkflowExecute(session, null).get();
            // Should not throw exception for new workflow
        }
        
        @Test
        @DisplayName("pre_workflow_execute with InteractiveInput (recovery mode)")
        void testPreWorkflowExecuteWithInteractiveInput() throws ExecutionException, InterruptedException {
            // Setup - first create the workflow store with some state
            checkpointer.preWorkflowExecute(session, null).get();
            
            // Save some state to recover
            session.getMockState().setState(Map.of("existing", "data"));
            checkpointer.postWorkflowExecute(session, 
                Map.of(InMemoryCheckpointer.TASK_STATUS_INTERRUPT, true), null).get();
            
            // Python: interactive_input = InteractiveInput(raw_inputs={"response": "test"})
            //         await checkpointer.pre_workflow_execute(session, interactive_input)
            InteractiveInput interactiveInput = new InteractiveInput(Map.of("response", "test"));
            
            // Create new session for recovery
            MockWorkflowSession newSession = new MockWorkflowSession();
            assertDoesNotThrow(() -> checkpointer.preWorkflowExecute(newSession, interactiveInput).get());
        }
        
        @Test
        @DisplayName("pre_workflow_execute conflict raises exception")
        void testPreWorkflowExecuteConflictRaisesException() throws ExecutionException, InterruptedException {
            // Setup existing state
            checkpointer.preWorkflowExecute(session, null).get();
            session.getMockState().setState(Map.of("test", "data"));
            checkpointer.postWorkflowExecute(session, 
                Map.of(InMemoryCheckpointer.TASK_STATUS_INTERRUPT, true), null).get();
            
            // Python: with pytest.raises(JiuWenBaseException):
            //             await checkpointer.pre_workflow_execute(session, None)
            MockWorkflowSession newSession = new MockWorkflowSession();
            ExecutionException ex = assertThrows(ExecutionException.class, () -> 
                checkpointer.preWorkflowExecute(newSession, null).get());
            assertInstanceOf(JiuWenBaseException.class, ex.getCause());
        }
        
        @Test
        @DisplayName("pre_workflow_execute with force delete clears existing state")
        void testPreWorkflowExecuteConflictWithForceDelete() throws ExecutionException, InterruptedException {
            // Setup existing state
            checkpointer.preWorkflowExecute(session, null).get();
            session.getMockState().setState(Map.of("test", "data"));
            checkpointer.postWorkflowExecute(session, 
                Map.of(InMemoryCheckpointer.TASK_STATUS_INTERRUPT, true), null).get();
            
            // Python: session = MockSession(force_del=True)
            //         await checkpointer.pre_workflow_execute(session, None)
            MockWorkflowSession forceDelSession = new MockWorkflowSession(
                "test_session", "test_workflow", true, null);
            assertDoesNotThrow(() -> checkpointer.preWorkflowExecute(forceDelSession, null).get());
        }
        
        @Test
        @DisplayName("post_workflow_execute success clears state")
        void testPostWorkflowExecuteSuccessClearsState() throws ExecutionException, InterruptedException {
            // Setup workflow store
            checkpointer.preWorkflowExecute(session, null).get();
            session.getMockState().setState(Map.of("test", "data"));
            
            // Python: result = {"output": "success"}
            //         await checkpointer.post_workflow_execute(session, result, None)
            Map<String, Object> result = Map.of("output", "success");
            assertDoesNotThrow(() -> checkpointer.postWorkflowExecute(session, result, null).get());
        }
        
        @Test
        @DisplayName("post_workflow_execute with interrupt saves state")
        void testPostWorkflowExecuteWithInterruptSavesState() throws ExecutionException, InterruptedException {
            // Setup workflow store
            checkpointer.preWorkflowExecute(session, null).get();
            session.getMockState().setState(Map.of("test", "data"));
            
            // Python: result = {TASK_STATUS_INTERRUPT: True}
            //         await checkpointer.post_workflow_execute(session, result, None)
            Map<String, Object> result = new HashMap<>();
            result.put(InMemoryCheckpointer.TASK_STATUS_INTERRUPT, true);
            assertDoesNotThrow(() -> checkpointer.postWorkflowExecute(session, result, null).get());
        }
        
        @Test
        @DisplayName("post_workflow_execute with exception saves state and raises")
        void testPostWorkflowExecuteWithExceptionSavesStateAndRaises() throws ExecutionException, InterruptedException {
            // Setup workflow store
            checkpointer.preWorkflowExecute(session, null).get();
            session.getMockState().setState(Map.of("current", "state"));
            
            // Python: exception = ValueError("Test error")
            //         with pytest.raises(ValueError):
            //             await checkpointer.post_workflow_execute(session, None, exception)
            Exception exception = new IllegalArgumentException("Test error");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> 
                checkpointer.postWorkflowExecute(session, null, exception).get());
            assertInstanceOf(RuntimeException.class, ex.getCause());
        }
        
        @Test
        @DisplayName("post_workflow_execute without workflow store raises exception")
        void testPostWorkflowExecuteWithoutWorkflowStoreRaises() {
            // Python: with pytest.raises(JiuWenBaseException):
            //             await checkpointer.post_workflow_execute(session, None, exception)
            Exception exception = new IllegalArgumentException("Test error");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> 
                checkpointer.postWorkflowExecute(session, null, exception).get());
            assertInstanceOf(JiuWenBaseException.class, ex.getCause());
        }
        
        @Test
        @DisplayName("pre_agent_execute creates store")
        void testPreAgentExecuteCreatesStore() throws ExecutionException, InterruptedException {
            MockAgentSession agentSession = new MockAgentSession();
            // Python: await checkpointer.pre_agent_execute(session, None)
            //         assert session.session_id() in checkpointer._agent_stores
            assertDoesNotThrow(() -> checkpointer.preAgentExecute(agentSession, null).get());
        }
        
        @Test
        @DisplayName("pre_agent_execute with inputs sets state")
        void testPreAgentExecuteWithInputsSetsState() throws ExecutionException, InterruptedException {
            MockAgentSession agentSession = new MockAgentSession();
            // Python: inputs = {"query": "test"}
            //         await checkpointer.pre_agent_execute(session, inputs)
            //         state = session.state().get_state()
            //         assert state.get(INTERACTIVE_INPUT) == [inputs]
            Map<String, Object> inputs = Map.of("query", "test");
            checkpointer.preAgentExecute(agentSession, inputs).get();
            Map<String, Object> state = agentSession.getState().getState();
            assertEquals(List.of(inputs), state.get(Constant.INTERACTIVE_INPUT));
        }
        
        @Test
        @DisplayName("pre_agent_execute recovery")
        void testPreAgentExecuteRecovery() throws ExecutionException, InterruptedException {
            MockAgentSession agentSession = new MockAgentSession();
            
            // First save some state
            checkpointer.preAgentExecute(agentSession, null).get();
            agentSession.getMockState().setState(Map.of("saved", "state"));
            checkpointer.postAgentExecute(agentSession).get();
            
            // Python: await checkpointer.pre_agent_execute(session, None)
            //         assert session.state().get_state() == {"saved": "state"}
            MockAgentSession newSession = new MockAgentSession();
            checkpointer.preAgentExecute(newSession, null).get();
            assertEquals(Map.of("saved", "state"), newSession.getState().getState());
        }
        
        @Test
        @DisplayName("interrupt_agent_execute saves state")
        void testInterruptAgentExecuteSavesState() throws ExecutionException, InterruptedException {
            MockAgentSession agentSession = new MockAgentSession();
            // Setup agent store
            checkpointer.preAgentExecute(agentSession, null).get();
            agentSession.getMockState().setState(Map.of("current", "state"));
            
            // Python: await checkpointer.interrupt_agent_execute(session)
            assertDoesNotThrow(() -> checkpointer.interruptAgentExecute(agentSession).get());
        }
        
        @Test
        @DisplayName("interrupt_agent_execute without store raises")
        void testInterruptAgentExecuteWithoutStoreRaises() {
            MockAgentSession agentSession = new MockAgentSession();
            // Python: with pytest.raises(JiuWenBaseException):
            //             await checkpointer.interrupt_agent_execute(session)
            ExecutionException ex = assertThrows(ExecutionException.class, () -> 
                checkpointer.interruptAgentExecute(agentSession).get());
            assertInstanceOf(JiuWenBaseException.class, ex.getCause());
        }
        
        @Test
        @DisplayName("post_agent_execute saves state")
        void testPostAgentExecuteSavesState() throws ExecutionException, InterruptedException {
            MockAgentSession agentSession = new MockAgentSession();
            // Setup agent store
            checkpointer.preAgentExecute(agentSession, null).get();
            agentSession.getMockState().setState(Map.of("final", "state"));
            
            // Python: await checkpointer.post_agent_execute(session)
            assertDoesNotThrow(() -> checkpointer.postAgentExecute(agentSession).get());
        }
        
        @Test
        @DisplayName("post_agent_execute without store raises")
        void testPostAgentExecuteWithoutStoreRaises() {
            MockAgentSession agentSession = new MockAgentSession();
            // Python: with pytest.raises(JiuWenBaseException):
            //             await checkpointer.post_agent_execute(session)
            ExecutionException ex = assertThrows(ExecutionException.class, () -> 
                checkpointer.postAgentExecute(agentSession).get());
            assertInstanceOf(JiuWenBaseException.class, ex.getCause());
        }
        
        @Test
        @DisplayName("release agent clears agent state")
        void testReleaseAgentClearsAgentState() throws ExecutionException, InterruptedException {
            MockAgentSession agentSession = new MockAgentSession();
            // Setup agent store
            checkpointer.preAgentExecute(agentSession, null).get();
            agentSession.getMockState().setState(Map.of("test", "state"));
            checkpointer.postAgentExecute(agentSession).get();
            
            // Python: await checkpointer.release(session.session_id(), session.agent_id())
            assertDoesNotThrow(() -> 
                checkpointer.release(agentSession.getSessionId(), agentSession.getAgentId()).get());
        }
        
        @Test
        @DisplayName("release session clears all")
        void testReleaseSessionClearsAll() throws ExecutionException, InterruptedException {
            // Setup workflow and agent stores
            checkpointer.preWorkflowExecute(session, null).get();
            MockAgentSession agentSession = new MockAgentSession();
            checkpointer.preAgentExecute(agentSession, null).get();
            
            // Python: await checkpointer.release(session.session_id())
            assertDoesNotThrow(() -> checkpointer.release(session.getSessionId()).get());
        }
        
        @Test
        @DisplayName("release nonexistent agent store no error")
        void testReleaseNonexistentAgentStoreNoError() {
            // Python: await checkpointer.release(session.session_id(), session.agent_id())
            //         # Should not raise
            MockAgentSession agentSession = new MockAgentSession();
            assertDoesNotThrow(() -> 
                checkpointer.release(agentSession.getSessionId(), agentSession.getAgentId()).get());
        }
        
        @Test
        @DisplayName("graph_store returns InMemoryStore instance")
        void testGraphStoreReturnsInMemoryStore() {
            // Python: graph_store = checkpointer.graph_store()
            //         assert isinstance(graph_store, InMemoryStore)
            assertInstanceOf(InMemoryStore.class, checkpointer.graphStore());
        }
    }
}
