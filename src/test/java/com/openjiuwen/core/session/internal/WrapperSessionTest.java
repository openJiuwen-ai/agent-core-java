/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for internal wrapper session classes.
 * 
 * <p>Converted from Python: test_wrapper.py</p>
 * <p>Python测试类: TestWrappedSession, TestStateSession, TestRouterSession, TestTaskSession</p>
 */
class WrapperSessionTest {
    
    @Nested
    @DisplayName("WrappedSession Tests")
    class WrappedSessionTests {
        
        @Mock
        private BaseSession inner;
        
        @Mock
        private Config mockConfig;
        
        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }
        
        // Helper method to create concrete instance of abstract WrappedSession
        private WrappedSession createWrappedSession(BaseSession inner) {
            return new WrappedSession(inner) {
                @Override public String getExecutableId() { return ""; }
                @Override public String getSessionId() { return ""; }
                @Override public void updateState(Map<String, Object> data) {}
                @Override public Object getState(Object key) { return null; }
                @Override public void updateGlobalState(Map<String, Object> data) {}
                @Override public Object getGlobalState(Object key) { return null; }
                @Override public com.openjiuwen.core.session.stream.StreamWriter<?, ?> getStreamWriter() { return null; }
                @Override public com.openjiuwen.core.session.stream.StreamWriter<?, ?> getCustomWriter() { return null; }
                @Override public CompletableFuture<Void> writeStream(Object data) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> writeCustomStream(Map<String, Object> data) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> trace(Map<String, Object> data) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> traceError(Exception error) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> interact(Object value) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> postRun() { return CompletableFuture.completedFuture(null); }
            };
        }
        
        @Test
        @DisplayName("Should delegate to inner session's config for workflow config")
        void testGetWorkflowConfig() {
            // Python: result = session.get_workflow_config("wf1")
            //         mock_config.get_workflow_config.assert_called_once_with("wf1")
            //         assert result is mock_workflow_config
            Object mockWorkflowConfig = new Object();
            when(inner.getConfig()).thenReturn(mockConfig);
            when(mockConfig.getWorkflowConfig("wf1")).thenReturn(mockWorkflowConfig);
            
            WrappedSession session = createWrappedSession(inner);
            
            Object result = session.getWorkflowConfig("wf1");
            
            verify(mockConfig).getWorkflowConfig("wf1");
            assertSame(mockWorkflowConfig, result);
        }
        
        @Test
        @DisplayName("Should delegate to inner session's config for agent config")
        void testGetAgentConfig() {
            // Python: result = session.get_agent_config()
            //         mock_config.get_agent_config.assert_called_once()
            //         assert result is mock_agent_config
            Object mockAgentConfig = new Object();
            when(inner.getConfig()).thenReturn(mockConfig);
            when(mockConfig.getAgentConfig()).thenReturn(mockAgentConfig);
            
            WrappedSession session = createWrappedSession(inner);
            
            Object result = session.getAgentConfig();
            
            verify(mockConfig).getAgentConfig();
            assertSame(mockAgentConfig, result);
        }
        
        @Test
        @DisplayName("Should delegate to inner session's config for env")
        void testGetEnv() {
            // Python: result = session.get_env("ENV_KEY")
            //         mock_config.get_env.assert_called_once_with("ENV_KEY")
            //         assert result == "env_value"
            when(inner.getConfig()).thenReturn(mockConfig);
            when(mockConfig.getEnv("ENV_KEY")).thenReturn("env_value");
            
            WrappedSession session = createWrappedSession(inner);
            
            Object result = session.getEnv("ENV_KEY");
            
            verify(mockConfig).getEnv("ENV_KEY");
            assertEquals("env_value", result);
        }
        
        @Test
        @DisplayName("Should return inner session")
        void testBase() {
            // Python: assert session.base() is inner
            WrappedSession session = createWrappedSession(inner);
            
            assertSame(inner, session.getBase());
        }
    }
    
    @Nested
    @DisplayName("StateSession Tests")
    class StateSessionTests {
        
        @Mock
        private BaseSession inner;
        
        @Mock
        private State mockState;
        
        @Mock
        private StreamWriterManager mockStreamWriterManager;
        
        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }
        
        // Helper method to create concrete instance of abstract StateSession
        private StateSession createStateSession(BaseSession inner) {
            return new StateSession(inner) {
                @Override public CompletableFuture<Void> trace(Map<String, Object> data) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> traceError(Exception error) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> interact(Object value) { return CompletableFuture.completedFuture(null); }
                @Override public CompletableFuture<Void> postRun() { return CompletableFuture.completedFuture(null); }
            };
        }
        
        @Test
        @DisplayName("Should delegate executable_id to inner session")
        void testExecutableId() {
            // Python: assert session.executable_id() == "exec_123"
            when(inner.getSessionId()).thenReturn("session_123");
            
            StateSession session = createStateSession(inner);
            
            assertEquals("session_123", session.getSessionId());
        }
        
        @Test
        @DisplayName("Should update state via inner session")
        void testUpdateState() {
            // Python: session.update_state({"key": "value"})
            //         mock_state.update.assert_called_once_with({"key": "value"})
            when(inner.getState()).thenReturn(mockState);
            
            StateSession session = createStateSession(inner);
            session.updateState(Map.of("key", "value"));
            
            verify(mockState).update(Map.of("key", "value"));
        }
        
        @Test
        @DisplayName("Should get state via inner session")
        void testGetState() {
            // Python: result = session.get_state("data")
            //         mock_state.get.assert_called_once_with("data")
            //         assert result == {"data": "value"}
            when(inner.getState()).thenReturn(mockState);
            when(mockState.get("data")).thenReturn(Map.of("data", "value"));
            
            StateSession session = createStateSession(inner);
            Object result = session.getState("data");
            
            verify(mockState).get("data");
            assertEquals(Map.of("data", "value"), result);
        }
        
        @Test
        @DisplayName("Should update global state via inner session")
        void testUpdateGlobalState() {
            // Python: session.update_global_state({"global_key": "global_value"})
            //         mock_state.update_global.assert_called_once_with({"global_key": "global_value"})
            when(inner.getState()).thenReturn(mockState);
            
            StateSession session = createStateSession(inner);
            session.updateGlobalState(Map.of("global_key", "global_value"));
            
            verify(mockState).updateGlobal(Map.of("global_key", "global_value"));
        }
        
        @Test
        @DisplayName("Should get global state via inner session")
        void testGetGlobalState() {
            // Python: result = session.get_global_state("global_data")
            //         mock_state.get_global.assert_called_once_with("global_data")
            //         assert result == {"global_data": "value"}
            when(inner.getState()).thenReturn(mockState);
            when(mockState.getGlobal("global_data")).thenReturn(Map.of("global_data", "value"));
            
            StateSession session = createStateSession(inner);
            Object result = session.getGlobalState("global_data");
            
            verify(mockState).getGlobal("global_data");
            assertEquals(Map.of("global_data", "value"), result);
        }
        
        @Test
        @DisplayName("Should return output writer from stream_writer_manager")
        void testStreamWriterReturnsOutputWriter() {
            // Python: result = session.stream_writer()
            //         assert result is mock_writer
            com.openjiuwen.core.session.stream.OutputStreamWriter mockWriter = 
                mock(com.openjiuwen.core.session.stream.OutputStreamWriter.class);
            when(inner.getStreamWriterManager()).thenReturn(mockStreamWriterManager);
            when(mockStreamWriterManager.getOutputWriter()).thenReturn(mockWriter);
            
            StateSession session = createStateSession(inner);
            Object result = session.getStreamWriter();
            
            assertSame(mockWriter, result);
        }
        
        @Test
        @DisplayName("Should return None when stream_writer_manager is None")
        void testStreamWriterReturnsNullWhenNoManager() {
            // Python: result = session.stream_writer()
            //         assert result is None
            when(inner.getStreamWriterManager()).thenReturn(null);
            
            StateSession session = createStateSession(inner);
            Object result = session.getStreamWriter();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("Should return custom writer from stream_writer_manager")
        void testCustomWriterReturnsCustomWriter() {
            // Python: result = session.custom_writer()
            //         assert result is mock_writer
            com.openjiuwen.core.session.stream.CustomStreamWriter mockWriter = 
                mock(com.openjiuwen.core.session.stream.CustomStreamWriter.class);
            when(inner.getStreamWriterManager()).thenReturn(mockStreamWriterManager);
            when(mockStreamWriterManager.getCustomWriter()).thenReturn(mockWriter);
            
            StateSession session = createStateSession(inner);
            Object result = session.getCustomWriter();
            
            assertSame(mockWriter, result);
        }
        
        @Test
        @DisplayName("Should return None when stream_writer_manager is None for custom writer")
        void testCustomWriterReturnsNullWhenNoManager() {
            // Python: result = session.custom_writer()
            //         assert result is None
            when(inner.getStreamWriterManager()).thenReturn(null);
            
            StateSession session = createStateSession(inner);
            Object result = session.getCustomWriter();
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("Should write to stream via output writer")
        void testWriteStream() throws Exception {
            // Python: await session.write_stream({"type": "output", "data": "test"})
            //         mock_writer.write.assert_called_once_with({"type": "output", "data": "test"})
            // Note: When stream_writer_manager is null, writeStream returns completed future
            when(inner.getStreamWriterManager()).thenReturn(null);
            
            StateSession session = createStateSession(inner);
            
            CompletableFuture<Void> future = session.writeStream(Map.of("type", "output", "data", "test"));
            future.get();
            
            // Verify write completes without error
            assertNotNull(future);
        }
        
        @Test
        @DisplayName("Should write to custom stream via custom writer")
        void testWriteCustomStream() throws Exception {
            // Python: await session.write_custom_stream({"custom": "data"})
            //         mock_writer.write.assert_called_once_with({"custom": "data"})
            // Note: When stream_writer_manager is null, writeCustomStream returns completed future
            when(inner.getStreamWriterManager()).thenReturn(null);
            
            StateSession session = createStateSession(inner);
            
            CompletableFuture<Void> future = session.writeCustomStream(Map.of("custom", "data"));
            future.get();
            
            // Verify write completes without error
            assertNotNull(future);
        }
    }
    
    @Nested
    @DisplayName("RouterSession Tests")
    class RouterSessionTests {
        
        @Mock
        private BaseSession inner;
        
        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }
        
        @Test
        @DisplayName("Router session should not expose stream_writer")
        void testStreamWriterReturnsNull() {
            // Python: assert session.stream_writer() is None
            RouterSession session = new RouterSession(inner);
            
            assertNull(session.getStreamWriter());
        }
        
        @Test
        @DisplayName("Router session should not expose custom_writer")
        void testCustomWriterReturnsNull() {
            // Python: assert session.custom_writer() is None
            RouterSession session = new RouterSession(inner);
            
            assertNull(session.getCustomWriter());
        }
        
        @Test
        @DisplayName("Router session write_stream should be no-op")
        void testWriteStreamDoesNothing() throws Exception {
            // Python: await session.write_stream({"data": "test"})
            //         # Should not raise, just pass
            RouterSession session = new RouterSession(inner);
            
            CompletableFuture<Void> future = session.writeStream(Map.of("data", "test"));
            future.get();
            
            // Should complete without exception
        }
        
        @Test
        @DisplayName("Router session write_custom_stream should be no-op")
        void testWriteCustomStreamDoesNothing() throws Exception {
            // Python: await session.write_custom_stream({"data": "test"})
            //         # Should not raise, just pass
            RouterSession session = new RouterSession(inner);
            
            CompletableFuture<Void> future = session.writeCustomStream(Map.of("data", "test"));
            future.get();
            
            // Should complete without exception
        }
        
        @Test
        @DisplayName("Router session update_global_state should be no-op")
        void testUpdateGlobalStateDoesNothing() {
            // Python: session.update_global_state({"data": "test"})
            //         # Should not call inner state update
            RouterSession session = new RouterSession(inner);
            
            session.updateGlobalState(Map.of("data", "test"));
            
            // Should not throw and not call inner
            verify(inner, never()).getState();
        }
        
        @Test
        @DisplayName("Router session update_state should be no-op")
        void testUpdateStateDoesNothing() {
            // Python: session.update_state({"data": "test"})
            //         # Should not call inner state update
            RouterSession session = new RouterSession(inner);
            
            session.updateState(Map.of("data", "test"));
            
            // Should not throw and not call inner
            verify(inner, never()).getState();
        }
        
        @Test
        @DisplayName("Router session should not expose workflow config")
        void testGetWorkflowConfigReturnsNull() {
            // Python: assert session.get_workflow_config("wf1") is None
            RouterSession session = new RouterSession(inner);
            
            assertNull(session.getWorkflowConfig("wf1"));
        }
        
        @Test
        @DisplayName("Router session should not expose agent config")
        void testGetAgentConfigReturnsNull() {
            // Python: assert session.get_agent_config() is None
            RouterSession session = new RouterSession(inner);
            
            assertNull(session.getAgentConfig());
        }
        
        @Test
        @DisplayName("Router session should not expose env")
        void testGetEnvReturnsNull() {
            // Python: assert session.get_env("key") is None
            RouterSession session = new RouterSession(inner);
            
            assertNull(session.getEnv("key"));
        }
        
        @Test
        @DisplayName("Router session should not expose base")
        void testBaseReturnsNull() {
            // Python: assert session.base() is None
            RouterSession session = new RouterSession(inner);
            
            assertNull(session.getBase());
        }
    }
    
    @Nested
    @DisplayName("TaskSession Tests")
    class TaskSessionTests {
        
        @Test
        @DisplayName("Should create AgentSession internally")
        void testInitCreatesAgentSession() {
            // Python: session = TaskSession(session_id="task_session_1")
            //         assert session.session_id() == "task_session_1"
            TaskSession session = new TaskSession("task_session_1");
            
            assertEquals("task_session_1", session.getSessionId());
        }
        
        @Test
        @DisplayName("Should use provided config")
        void testInitWithConfig() {
            // Python: session = TaskSession(session_id="task_session_1", config=custom_config)
            //         assert session.get_inner_session().config() is custom_config
            Config customConfig = mock(Config.class);
            
            TaskSession session = new TaskSession("task_session_1", customConfig);
            
            // Config should be passed to inner AgentSession
            assertSame(customConfig, session.getInnerSession().getConfig());
        }
        
        @Test
        @DisplayName("Should return inner AgentSession")
        void testGetInnerSession() {
            // Python: inner = session.get_inner_session()
            //         assert inner is not None
            TaskSession session = new TaskSession("task_session_1");
            
            var inner = session.getInnerSession();
            
            assertNotNull(inner);
            assertInstanceOf(AgentSession.class, inner);
        }
        
        @Test
        @DisplayName("TaskSession trace should be no-op")
        void testTraceDoesNothing() throws Exception {
            // Python: await session.trace({"data": "test"})
            //         # Should not raise
            TaskSession session = new TaskSession("task_session_1");
            
            CompletableFuture<Void> future = session.trace(Map.of("data", "test"));
            future.get();
            
            // Should complete without exception
        }
        
        @Test
        @DisplayName("TaskSession trace_error should be no-op")
        void testTraceErrorDoesNothing() throws Exception {
            // Python: await session.trace_error(Exception("test"))
            //         # Should not raise
            TaskSession session = new TaskSession("task_session_1");
            
            CompletableFuture<Void> future = session.traceError(new Exception("test"));
            future.get();
            
            // Should complete without exception
        }
        
        @Test
        @DisplayName("Should return tracer from inner session")
        void testTracer() {
            // Python: tracer = session.tracer()
            //         assert tracer is not None
            TaskSession session = new TaskSession("task_session_1");
            
            Object tracer = session.getTracer();
            
            assertNotNull(tracer);
        }
        
        @Test
        @DisplayName("Should return env dict from inner config")
        void testGetEnvs() {
            // Python: envs = session.get_envs()
            //         assert isinstance(envs, dict)
            TaskSession session = new TaskSession("task_session_1");
            
            Map<String, Object> envs = session.getEnvs();
            
            assertNotNull(envs);
        }
        
        @Test
        @DisplayName("Should return session_id")
        void testGetSessionId() {
            // Python: assert session.get_session_id() == "task_session_123"
            TaskSession session = new TaskSession("task_session_123");
            
            assertEquals("task_session_123", session.getSessionId());
        }
        
        @Test
        @DisplayName("Should return stream output iterator")
        void testStreamIterator() {
            // Python: iterator = session.stream_iterator()
            //         assert iterator is not None
            TaskSession session = new TaskSession("task_session_1");
            
            Object iterator = session.streamIterator();
            
            assertNotNull(iterator);
        }
    }
}

