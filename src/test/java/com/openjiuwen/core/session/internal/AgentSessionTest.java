/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.state.AgentStateCollection;
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
 * Unit tests for internal agent session classes.
 * 
 * <p>Converted from Python: test_agent.py</p>
 * <p>Python测试类: TestStaticAgentSessionInit, TestStaticAgentSessionMethods,
 *    TestAgentSessionInit, TestAgentSessionMethods, TestAgentSessionComponents</p>
 */
class AgentSessionTest {
    
    @Nested
    @DisplayName("StaticAgentSession Init Tests")
    class StaticAgentSessionInitTests {
        
        @Test
        @DisplayName("Should create default config when not provided")
        void testInitWithDefaultConfig() {
            // Python: session = StaticAgentSession()
            //         assert session.config() is not None
            //         assert isinstance(session.config(), Config)
            StaticAgentSession session = new StaticAgentSession();
            
            assertNotNull(session.getConfig());
            assertInstanceOf(Config.class, session.getConfig());
        }
        
        @Test
        @DisplayName("Should use provided config")
        void testInitWithCustomConfig() {
            // Python: custom_config = Config()
            //         session = StaticAgentSession(config=custom_config)
            //         assert session.config() is custom_config
            Config customConfig = new DefaultConfig();
            
            StaticAgentSession session = new StaticAgentSession(customConfig);
            
            assertSame(customConfig, session.getConfig());
        }
        
        @Test
        @DisplayName("Should return default checkpointer")
        void testCheckpointerReturnsDefault() {
            // Python: session = StaticAgentSession()
            //         checkpointer = session.checkpointer()
            //         assert checkpointer is not None
            StaticAgentSession session = new StaticAgentSession();
            
            Checkpointer checkpointer = session.getCheckpointer();
            
            assertNotNull(checkpointer);
        }
    }
    
    @Nested
    @DisplayName("StaticAgentSession Methods Tests")
    class StaticAgentSessionMethodsTests {
        
        @Test
        @DisplayName("State method returns None (not implemented)")
        void testStateReturnsNone() {
            // Python: session = StaticAgentSession()
            //         result = session.state()
            //         assert result is None
            StaticAgentSession session = new StaticAgentSession();
            
            State result = session.getState();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("Tracer method returns None (not implemented)")
        void testTracerReturnsNone() {
            // Python: session = StaticAgentSession()
            //         result = session.tracer()
            //         assert result is None
            StaticAgentSession session = new StaticAgentSession();
            
            Tracer result = session.getTracer();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("stream_writer_manager method returns None (not implemented)")
        void testStreamWriterManagerReturnsNone() {
            // Python: session = StaticAgentSession()
            //         result = session.stream_writer_manager()
            //         assert result is None
            StaticAgentSession session = new StaticAgentSession();
            
            StreamWriterManager result = session.getStreamWriterManager();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("callback_manager method returns None (not implemented)")
        void testCallbackManagerReturnsNone() {
            // Python: session = StaticAgentSession()
            //         result = session.callback_manager()
            //         assert result is None
            StaticAgentSession session = new StaticAgentSession();
            
            CallbackManager result = session.getCallbackManager();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("session_id method returns None (not implemented)")
        void testSessionIdReturnsNone() {
            // Python: session = StaticAgentSession()
            //         result = session.session_id()
            //         assert result is None
            StaticAgentSession session = new StaticAgentSession();
            
            String result = session.getSessionId();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("Should create new AgentSession with pre_agent_execute")
        void testCreateAgentSession() throws Exception {
            // Python: static_session = StaticAgentSession()
            //         static_session._checkpointer = mock_checkpointer
            //         agent_session = await static_session.create_agent_session("session123", inputs={"key": "value"})
            //         assert isinstance(agent_session, AgentSession)
            //         assert agent_session.session_id() == "session123"
            //         mock_checkpointer.pre_agent_execute.assert_called_once()
            Checkpointer mockCheckpointer = mock(Checkpointer.class);
            when(mockCheckpointer.preAgentExecute(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            StaticAgentSession staticSession = new StaticAgentSession();
            // Use reflection to set private checkpointer field (aligns with Python test)
            var field = StaticAgentSession.class.getDeclaredField("checkpointer");
            field.setAccessible(true);
            field.set(staticSession, mockCheckpointer);
            
            var result = staticSession.createAgentSession(
                "session123", java.util.Map.of("key", "value")).get();
            
            assertInstanceOf(AgentSession.class, result);
            assertEquals("session123", result.getSessionId());
            verify(mockCheckpointer).preAgentExecute(any(), any());
        }
    }
    
    @Nested
    @DisplayName("AgentSession Init Tests")
    class AgentSessionInitTests {
        
        @Test
        @DisplayName("Should initialize with basic parameters")
        void testInitBasic() {
            // Python: session = AgentSession(session_id="test_session")
            //         assert session.session_id() == "test_session"
            //         assert isinstance(session.state(), StateCollection)
            //         assert isinstance(session.stream_writer_manager(), StreamWriterManager)
            //         assert isinstance(session.callback_manager(), CallbackManager)
            //         assert session.tracer() is not None
            AgentSession session = new AgentSession("test_session");
            
            assertEquals("test_session", session.getSessionId());
            assertInstanceOf(AgentStateCollection.class, session.getState());
            assertInstanceOf(StreamWriterManager.class, session.getStreamWriterManager());
            assertInstanceOf(CallbackManager.class, session.getCallbackManager());
            assertNotNull(session.getTracer());
        }
        
        @Test
        @DisplayName("Should use provided config")
        void testInitWithConfig() {
            // Python: custom_config = Config()
            //         session = AgentSession(session_id="test_session", config=custom_config)
            //         assert session.config() is custom_config
            Config customConfig = new DefaultConfig();
            
            AgentSession session = new AgentSession("test_session", customConfig);
            
            assertSame(customConfig, session.getConfig());
        }
        
        @Test
        @DisplayName("Should use provided checkpointer")
        void testInitWithCheckpointer() {
            // Python: session = AgentSession(session_id="test_session", checkpointer=mock_checkpointer)
            //         assert session.checkpointer() is mock_checkpointer
            Checkpointer mockCheckpointer = mock(Checkpointer.class);
            
            AgentSession session = new AgentSession("test_session", null, mockCheckpointer);
            
            assertSame(mockCheckpointer, session.getCheckpointer());
        }
        
        @Test
        @DisplayName("Should create default checkpointer when not provided")
        void testInitCreatesDefaultCheckpointer() {
            // Python: session = AgentSession(session_id="test_session")
            //         assert session.checkpointer() is not None
            AgentSession session = new AgentSession("test_session");
            
            assertNotNull(session.getCheckpointer());
        }
    }
    
    @Nested
    @DisplayName("AgentSession Methods Tests")
    class AgentSessionMethodsTests {
        
        @Test
        @DisplayName("Should return agent span created during init")
        void testSpanReturnsAgentSpan() {
            // Python: session = AgentSession(session_id="test_session")
            //         span = session.span()
            //         assert span is not None
            AgentSession session = new AgentSession("test_session");
            
            var span = session.getSpan();
            
            // Span should be created from tracer
            assertNotNull(span);
        }
        
        @Test
        @DisplayName("Should create WorkflowSession from AgentSession")
        void testCreateWorkflowSession() {
            // Python: session = AgentSession(session_id="test_session")
            //         workflow_session = session.create_workflow_session()
            //         assert workflow_session is not None
            //         assert workflow_session.session_id() == "test_session"
            //         assert workflow_session.parent() is session
            AgentSession session = new AgentSession("test_session");
            
            WorkflowSession workflowSession = session.createWorkflowSession();
            
            assertNotNull(workflowSession);
            assertEquals("test_session", workflowSession.getSessionId());
            assertSame(session, workflowSession.getParent());
        }
        
        @Test
        @DisplayName("Should return agent_id from agent_config")
        void testAgentIdFromConfig() {
            // Python: mock_agent_config.id = "agent_123"
            //         result = session.agent_id()
            //         assert result == "agent_123"
            Config mockConfig = mock(Config.class);
            Object mockAgentConfig = new Object() {
                public String getId() { return "agent_123"; }
            };
            when(mockConfig.getAgentConfig()).thenReturn(mockAgentConfig);
            
            AgentSession session = new AgentSession("test_session", mockConfig);
            
            String result = session.getAgentId();
            
            assertEquals("agent_123", result);
        }
        
        @Test
        @DisplayName("Should return agent_id from card when config is None")
        void testAgentIdFromCard() {
            // Python: mock_card.id = "card_agent_456"
            //         result = session.agent_id()
            //         assert result == "card_agent_456"
            Config mockConfig = mock(Config.class);
            when(mockConfig.getAgentConfig()).thenReturn(null);
            Object mockCard = new Object() {
                public String getId() { return "card_agent_456"; }
            };
            
            AgentSession session = new AgentSession("test_session", mockConfig, null, mockCard);
            
            String result = session.getAgentId();
            
            assertEquals("card_agent_456", result);
        }
    }
    
    @Nested
    @DisplayName("AgentSession Components Tests")
    class AgentSessionComponentsTests {
        
        @Test
        @DisplayName("Should return stream_writer_manager")
        void testStreamWriterManager() {
            // Python: session = AgentSession(session_id="test_session")
            //         manager = session.stream_writer_manager()
            //         assert isinstance(manager, StreamWriterManager)
            AgentSession session = new AgentSession("test_session");
            
            StreamWriterManager manager = session.getStreamWriterManager();
            
            assertInstanceOf(StreamWriterManager.class, manager);
        }
        
        @Test
        @DisplayName("Should return callback_manager")
        void testCallbackManager() {
            // Python: session = AgentSession(session_id="test_session")
            //         manager = session.callback_manager()
            //         assert isinstance(manager, CallbackManager)
            AgentSession session = new AgentSession("test_session");
            
            CallbackManager manager = session.getCallbackManager();
            
            assertInstanceOf(CallbackManager.class, manager);
        }
        
        @Test
        @DisplayName("Should return tracer")
        void testTracer() {
            // Python: session = AgentSession(session_id="test_session")
            //         tracer = session.tracer()
            //         assert tracer is not None
            AgentSession session = new AgentSession("test_session");
            
            Tracer tracer = session.getTracer();
            
            assertNotNull(tracer);
        }
        
        @Test
        @DisplayName("Should return state collection")
        void testState() {
            // Python: session = AgentSession(session_id="test_session")
            //         state = session.state()
            //         assert isinstance(state, StateCollection)
            AgentSession session = new AgentSession("test_session");
            
            State state = session.getState();
            
            assertInstanceOf(AgentStateCollection.class, state);
        }
    }
}

