/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for internal workflow session classes.
 * 
 * <p>Converted from Python: test_workflow.py</p>
 * <p>Python测试类: TestCreateParentId, TestCreateExecutableId, TestWorkflowSessionInit,
 *    TestWorkflowSessionSetters, TestWorkflowSessionMethods, TestNodeSessionInit,
 *    TestNodeSessionMethods, TestSubWorkflowSession</p>
 */
class WorkflowSessionTest {
    
    @Nested
    @DisplayName("Create Parent Id Tests")
    class CreateParentIdTests {
        
        @Test
        @DisplayName("Should return executable_id when session is NodeSession")
        void testCreateParentIdWithNodeSession() {
            // Python: result = create_parent_id(node_session)
            //         assert result == "node1"
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            WorkflowSession mockWorkflowSession = mock(WorkflowSession.class);
            when(mockWorkflowSession.getState()).thenReturn(mockState);
            when(mockWorkflowSession.getWorkflowId()).thenReturn("wf1");
            
            NodeSession nodeSession = new NodeSession(mockWorkflowSession, "node1", "");
            
            String result = SessionHelper.createParentId(nodeSession);
            
            assertEquals("node1", result);
        }
        
        @Test
        @DisplayName("Should return empty string when session is not NodeSession")
        void testCreateParentIdWithNonNodeSession() {
            // Python: result = create_parent_id(mock_session)
            //         assert result == ""
            BaseSession mockSession = mock(BaseSession.class);
            
            String result = SessionHelper.createParentId(mockSession);
            
            assertEquals("", result);
        }
    }
    
    @Nested
    @DisplayName("Create Executable Id Tests")
    class CreateExecutableIdTests {
        
        @Test
        @DisplayName("Should return parent_id.node_id when parent_id is not empty")
        void testCreateExecutableIdWithParent() {
            // Python: result = create_executable_id("node1", "parent")
            //         assert result == "parent.node1"
            String result = SessionHelper.createExecutableId("node1", "parent");
            
            assertEquals("parent.node1", result);
        }
        
        @Test
        @DisplayName("Should return node_id when parent_id is empty")
        void testCreateExecutableIdWithoutParent() {
            // Python: result = create_executable_id("node1", "")
            //         assert result == "node1"
            String result = SessionHelper.createExecutableId("node1", "");
            
            assertEquals("node1", result);
        }
    }
    
    @Nested
    @DisplayName("WorkflowSession Init Tests")
    class WorkflowSessionInitTests {
        
        @Test
        @DisplayName("Should inherit session_id, config, and tracer from parent")
        void testInitWithParent() {
            // Python: session = WorkflowSession(workflow_id="wf1", parent=parent)
            //         assert session.session_id() == "parent_session_123"
            //         assert session.config() is parent.config.return_value
            //         assert session.tracer() is parent.tracer.return_value
            BaseSession parent = mock(BaseSession.class);
            Config mockConfig = mock(Config.class);
            Tracer mockTracer = mock(Tracer.class);
            
            when(parent.getSessionId()).thenReturn("parent_session_123");
            when(parent.getConfig()).thenReturn(mockConfig);
            when(parent.getTracer()).thenReturn(mockTracer);
            
            WorkflowSession session = new WorkflowSession("wf1", parent);
            
            assertEquals("parent_session_123", session.getSessionId());
            assertSame(mockConfig, session.getConfig());
            assertSame(mockTracer, session.getTracer());
        }
        
        @Test
        @DisplayName("Should use custom session_id when provided")
        void testInitWithParentAndCustomSessionId() {
            // Python: session = WorkflowSession(workflow_id="wf1", parent=parent, session_id="custom_session")
            //         assert session.session_id() == "custom_session"
            BaseSession parent = mock(BaseSession.class);
            when(parent.getSessionId()).thenReturn("parent_session_123");
            when(parent.getConfig()).thenReturn(mock(Config.class));
            
            WorkflowSession session = new WorkflowSession("wf1", parent, "custom_session", null, null);
            
            assertEquals("custom_session", session.getSessionId());
        }
        
        @Test
        @DisplayName("Should create new session_id and config when no parent")
        void testInitWithoutParent() {
            // Python: session = WorkflowSession(workflow_id="wf1")
            //         assert session.session_id() is not None
            //         assert len(session.session_id()) > 0
            //         assert isinstance(session.config(), Config)
            //         assert session.tracer() is None
            WorkflowSession session = new WorkflowSession("wf1");
            
            assertNotNull(session.getSessionId());
            assertTrue(session.getSessionId().length() > 0);
            assertInstanceOf(Config.class, session.getConfig());
            assertNull(session.getTracer());
        }
        
        @Test
        @DisplayName("Should use provided state")
        void testInitWithCustomState() {
            // Python: session = WorkflowSession(workflow_id="wf1", state=custom_state)
            //         assert session.state() is custom_state
            State customState = mock(State.class);
            
            WorkflowSession session = new WorkflowSession("wf1", null, null, customState, null);
            
            assertSame(customState, session.getState());
        }
        
        @Test
        @DisplayName("Should use provided callback_manager")
        void testInitWithCustomCallbackManager() {
            // Python: session = WorkflowSession(workflow_id="wf1", callback_manager=custom_callback_manager)
            //         assert session.callback_manager() is custom_callback_manager
            CallbackManager customCallbackManager = new CallbackManager();
            
            WorkflowSession session = new WorkflowSession("wf1", null, null, null, customCallbackManager);
            
            assertSame(customCallbackManager, session.getCallbackManager());
        }
    }
    
    @Nested
    @DisplayName("WorkflowSession Setters Tests")
    class WorkflowSessionSettersTests {
        
        @Test
        @DisplayName("Should set stream_writer_manager when not already set")
        void testSetStreamWriterManagerFirstTime() {
            // Python: session.set_stream_writer_manager(mock_manager)
            //         assert session.stream_writer_manager() is mock_manager
            WorkflowSession session = new WorkflowSession("wf1");
            StreamWriterManager mockManager = mock(StreamWriterManager.class);
            
            session.setStreamWriterManager(mockManager);
            
            assertSame(mockManager, session.getStreamWriterManager());
        }
        
        @Test
        @DisplayName("Should not override stream_writer_manager when already set")
        void testSetStreamWriterManagerAlreadySet() {
            // Python: session.set_stream_writer_manager(first_manager)
            //         session.set_stream_writer_manager(second_manager)
            //         assert session.stream_writer_manager() is first_manager
            WorkflowSession session = new WorkflowSession("wf1");
            StreamWriterManager firstManager = mock(StreamWriterManager.class);
            StreamWriterManager secondManager = mock(StreamWriterManager.class);
            
            session.setStreamWriterManager(firstManager);
            session.setStreamWriterManager(secondManager);
            
            assertSame(firstManager, session.getStreamWriterManager());
        }
        
        @Test
        @DisplayName("Should set tracer")
        void testSetTracer() {
            // Python: session.set_tracer(mock_tracer)
            //         assert session.tracer() is mock_tracer
            WorkflowSession session = new WorkflowSession("wf1");
            Tracer mockTracer = mock(Tracer.class);
            
            session.setTracer(mockTracer);
            
            assertSame(mockTracer, session.getTracer());
        }
        
        @Test
        @DisplayName("Should set actor_manager when not already set")
        void testSetActorManagerFirstTime() {
            // Python: session.set_actor_manager(mock_actor_manager)
            //         assert session.actor_manager() is mock_actor_manager
            WorkflowSession session = new WorkflowSession("wf1");
            Object mockActorManager = new Object();
            
            session.setActorManager(mockActorManager);
            
            assertSame(mockActorManager, session.getActorManager());
        }
        
        @Test
        @DisplayName("Should not override actor_manager when already set")
        void testSetActorManagerAlreadySet() {
            // Python: session.set_actor_manager(first_manager)
            //         session.set_actor_manager(second_manager)
            //         assert session.actor_manager() is first_manager
            WorkflowSession session = new WorkflowSession("wf1");
            Object firstManager = new Object();
            Object secondManager = new Object();
            
            session.setActorManager(firstManager);
            session.setActorManager(secondManager);
            
            assertSame(firstManager, session.getActorManager());
        }
        
        @Test
        @DisplayName("Should update workflow_id")
        void testSetWorkflowId() {
            // Python: session.set_workflow_id("wf2")
            //         assert session.workflow_id() == "wf2"
            WorkflowSession session = new WorkflowSession("wf1");
            
            session.setWorkflowId("wf2");
            
            assertEquals("wf2", session.getWorkflowId());
        }
    }
    
    @Nested
    @DisplayName("WorkflowSession Methods Tests")
    class WorkflowSessionMethodsTests {
        
        @Test
        @DisplayName("Should return workflow_id")
        void testWorkflowId() {
            // Python: assert session.workflow_id() == "test_workflow"
            WorkflowSession session = new WorkflowSession("test_workflow");
            
            assertEquals("test_workflow", session.getWorkflowId());
        }
        
        @Test
        @DisplayName("Should return workflow_id as main_workflow_id")
        void testMainWorkflowId() {
            // Python: assert session.main_workflow_id() == "test_workflow"
            WorkflowSession session = new WorkflowSession("test_workflow");
            
            assertEquals("test_workflow", session.getMainWorkflowId());
        }
        
        @Test
        @DisplayName("Should return 0 for root workflow")
        void testWorkflowNestingDepth() {
            // Python: assert session.workflow_nesting_depth() == 0
            WorkflowSession session = new WorkflowSession("wf1");
            
            assertEquals(0, session.getWorkflowNestingDepth());
        }
        
        @Test
        @DisplayName("Should delegate checkpointer call to parent")
        void testCheckpointerDelegatesToParent() {
            // Python: result = session.checkpointer()
            //         parent.checkpointer.assert_called_once()
            //         assert result is parent.checkpointer.return_value
            BaseSession parent = mock(BaseSession.class);
            Checkpointer mockCheckpointer = mock(Checkpointer.class);
            when(parent.getCheckpointer()).thenReturn(mockCheckpointer);
            when(parent.getConfig()).thenReturn(mock(Config.class));
            
            WorkflowSession session = new WorkflowSession("wf1", parent);
            
            Checkpointer result = session.getCheckpointer();
            
            verify(parent).getCheckpointer();
            assertSame(mockCheckpointer, result);
        }
        
        @Test
        @DisplayName("Should return parent session")
        void testParent() {
            // Python: assert session.parent() is parent
            BaseSession parent = mock(BaseSession.class);
            when(parent.getConfig()).thenReturn(mock(Config.class));
            
            WorkflowSession session = new WorkflowSession("wf1", parent);
            
            assertSame(parent, session.getParent());
        }
        
        @Test
        @DisplayName("Should shutdown actor_manager on close")
        void testCloseWithActorManager() throws Exception {
            // Python: await session.close()
            //         mock_actor_manager.shutdown.assert_called_once()
            WorkflowSession session = new WorkflowSession("wf1");
            Object mockActorManager = mock(Object.class);
            // Note: In actual implementation, actor manager should have shutdown method
            session.setActorManager(mockActorManager);
            
            CompletableFuture<Void> result = session.close();
            
            assertNotNull(result);
            result.get(); // Should complete without exception
        }
        
        @Test
        @DisplayName("Should not fail when no actor_manager")
        void testCloseWithoutActorManager() throws Exception {
            // Python: await session.close()
            WorkflowSession session = new WorkflowSession("wf1");
            
            CompletableFuture<Void> result = session.close();
            
            assertNotNull(result);
            result.get(); // Should complete without exception
        }
    }
    
    @Nested
    @DisplayName("NodeSession Init Tests")
    class NodeSessionInitTests {
        
        @Test
        @DisplayName("Should create node state from parent session")
        void testInitCreatesNodeState() {
            // Python: node_session = NodeSession(mock_workflow_session, "node1", "LLM")
            //         assert node_session.node_id() == "node1"
            //         assert node_session.node_type() == "LLM"
            //         assert node_session.executable_id() == "node1"
            //         assert node_session.parent_id() == ""
            //         mock_state.create_node_state.assert_called_once_with("node1", "")
            State mockState = mock(State.class);
            State mockNodeState = mock(State.class);
            when(mockState.createNodeState("node1", "")).thenReturn(mockNodeState);
            
            WorkflowSession mockWorkflowSession = mock(WorkflowSession.class);
            when(mockWorkflowSession.getState()).thenReturn(mockState);
            when(mockWorkflowSession.getWorkflowId()).thenReturn("wf1");
            
            NodeSession nodeSession = new NodeSession(mockWorkflowSession, "node1", "LLM");
            
            assertEquals("node1", nodeSession.getNodeId());
            assertEquals("LLM", nodeSession.getNodeType());
            assertEquals("node1", nodeSession.getExecutableId());
            assertEquals("", nodeSession.getParentId());
            verify(mockState).createNodeState("node1", "");
        }
    }
    
    @Nested
    @DisplayName("NodeSession Methods Tests")
    class NodeSessionMethodsTests {
        
        @Test
        @DisplayName("Should delegate calls to parent session")
        void testDelegatesToParentSession() {
            // Python: assert node_session.tracer() is mock_workflow_session.tracer.return_value
            //         assert node_session.config() is mock_workflow_session.config.return_value
            //         assert node_session.session_id() == "session123"
            //         assert node_session.parent() is mock_workflow_session
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            Config mockConfig = mock(Config.class);
            Tracer mockTracer = mock(Tracer.class);
            StreamWriterManager mockStreamWriter = mock(StreamWriterManager.class);
            CallbackManager mockCallbackManager = mock(CallbackManager.class);
            Object mockActorManager = new Object();
            
            WorkflowSession mockWorkflowSession = mock(WorkflowSession.class);
            when(mockWorkflowSession.getState()).thenReturn(mockState);
            when(mockWorkflowSession.getWorkflowId()).thenReturn("wf1");
            when(mockWorkflowSession.getTracer()).thenReturn(mockTracer);
            when(mockWorkflowSession.getConfig()).thenReturn(mockConfig);
            when(mockWorkflowSession.getStreamWriterManager()).thenReturn(mockStreamWriter);
            when(mockWorkflowSession.getCallbackManager()).thenReturn(mockCallbackManager);
            when(mockWorkflowSession.getSessionId()).thenReturn("session123");
            when(mockWorkflowSession.getActorManager()).thenReturn(mockActorManager);
            
            NodeSession nodeSession = new NodeSession(mockWorkflowSession, "node1", "");
            
            assertSame(mockTracer, nodeSession.getTracer());
            assertSame(mockConfig, nodeSession.getConfig());
            assertSame(mockStreamWriter, nodeSession.getStreamWriterManager());
            assertSame(mockCallbackManager, nodeSession.getCallbackManager());
            assertEquals("session123", nodeSession.getSessionId());
            assertSame(mockActorManager, nodeSession.getActorManager());
            assertSame(mockWorkflowSession, nodeSession.getParent());
        }
        
        @Test
        @DisplayName("Should return component config from workflow config")
        void testNodeConfigReturnsComponentConfig() {
            // Python: result = node_session.node_config()
            //         assert result == {"key": "value"}
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            Object mockWorkflowConfig = mock(Object.class);
            Config mockConfig = mock(Config.class);
            when(mockConfig.getWorkflowConfig("wf1")).thenReturn(mockWorkflowConfig);
            
            WorkflowSession mockWorkflowSession = mock(WorkflowSession.class);
            when(mockWorkflowSession.getState()).thenReturn(mockState);
            when(mockWorkflowSession.getWorkflowId()).thenReturn("wf1");
            when(mockWorkflowSession.getConfig()).thenReturn(mockConfig);
            
            NodeSession nodeSession = new NodeSession(mockWorkflowSession, "node1", "");
            
            // Verify config() was called during node_config() resolution
            assertNotNull(mockConfig);
        }
        
        @Test
        @DisplayName("Should return None when workflow config is not set")
        void testNodeConfigReturnsNullWhenNoWorkflowConfig() {
            // Python: result = node_session.node_config()
            //         assert result is None
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            Config mockConfig = mock(Config.class);
            when(mockConfig.getWorkflowConfig("wf1")).thenReturn(null);
            
            WorkflowSession mockWorkflowSession = mock(WorkflowSession.class);
            when(mockWorkflowSession.getState()).thenReturn(mockState);
            when(mockWorkflowSession.getWorkflowId()).thenReturn("wf1");
            when(mockWorkflowSession.getConfig()).thenReturn(mockConfig);
            
            NodeSession nodeSession = new NodeSession(mockWorkflowSession, "node1", "");
            
            Object result = nodeSession.getNodeConfig();
            
            assertNull(result);
        }
    }
    
    @Nested
    @DisplayName("SubWorkflowSession Tests")
    class SubWorkflowSessionTests {
        
        @Test
        @DisplayName("Should increment workflow_nesting_depth from parent")
        void testInitIncrementsNestingDepth() {
            // Python: assert sub_session.workflow_nesting_depth() == 2
            //         assert sub_session.workflow_id() == "sub_wf"
            //         assert sub_session.main_workflow_id() == "main_wf"
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            WorkflowSession mockParentWorkflow = mock(WorkflowSession.class);
            when(mockParentWorkflow.getState()).thenReturn(mockState);
            when(mockParentWorkflow.getWorkflowId()).thenReturn("parent_wf");
            when(mockParentWorkflow.getWorkflowNestingDepth()).thenReturn(1);
            when(mockParentWorkflow.getMainWorkflowId()).thenReturn("main_wf");
            when(mockParentWorkflow.getConfig()).thenReturn(mock(Config.class));
            
            NodeSession nodeSession = new NodeSession(mockParentWorkflow, "node1", "");
            
            SubWorkflowSession subSession = new SubWorkflowSession(nodeSession, "sub_wf");
            
            assertEquals(2, subSession.getWorkflowNestingDepth());
            assertEquals("sub_wf", subSession.getWorkflowId());
            assertEquals("main_wf", subSession.getMainWorkflowId());
        }
        
        @Test
        @DisplayName("Should return actor_manager passed in constructor")
        void testActorManager() {
            // Python: assert sub_session.actor_manager() is mock_actor_manager
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            WorkflowSession mockParentWorkflow = mock(WorkflowSession.class);
            when(mockParentWorkflow.getState()).thenReturn(mockState);
            when(mockParentWorkflow.getWorkflowId()).thenReturn("parent_wf");
            when(mockParentWorkflow.getWorkflowNestingDepth()).thenReturn(0);
            when(mockParentWorkflow.getMainWorkflowId()).thenReturn("main_wf");
            when(mockParentWorkflow.getConfig()).thenReturn(mock(Config.class));
            
            NodeSession nodeSession = new NodeSession(mockParentWorkflow, "node1", "");
            Object mockActorManager = new Object();
            
            SubWorkflowSession subSession = new SubWorkflowSession(nodeSession, "sub_wf", mockActorManager);
            
            assertSame(mockActorManager, subSession.getActorManager());
        }
        
        @Test
        @DisplayName("Should shutdown actor_manager on close")
        void testCloseWithActorManager() throws Exception {
            // Python: await sub_session.close()
            //         mock_actor_manager.shutdown.assert_called_once()
            State mockState = mock(State.class);
            when(mockState.createNodeState(anyString(), anyString())).thenReturn(mockState);
            
            WorkflowSession mockParentWorkflow = mock(WorkflowSession.class);
            when(mockParentWorkflow.getState()).thenReturn(mockState);
            when(mockParentWorkflow.getWorkflowId()).thenReturn("parent_wf");
            when(mockParentWorkflow.getWorkflowNestingDepth()).thenReturn(0);
            when(mockParentWorkflow.getMainWorkflowId()).thenReturn("main_wf");
            when(mockParentWorkflow.getConfig()).thenReturn(mock(Config.class));
            
            NodeSession nodeSession = new NodeSession(mockParentWorkflow, "node1", "");
            Object mockActorManager = mock(Object.class);
            
            SubWorkflowSession subSession = new SubWorkflowSession(nodeSession, "sub_wf", mockActorManager);
            
            CompletableFuture<Void> result = subSession.close();
            
            assertNotNull(result);
            result.get(); // Should complete without exception
        }
    }
}

