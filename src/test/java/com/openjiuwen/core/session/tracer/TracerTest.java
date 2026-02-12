/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.stream.StreamEmitter;
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
 * Unit tests for Tracer class.
 * 
 * <p>Converted from Python: test_tracer.py</p>
 * <p>Python测试类: TestTracerInit, TestTracerInitMethod, TestTracerRegisterWorkflowSpanManager,
 *    TestTracerGetWorkflowSpan, TestTracerTrigger, TestTracerPopWorkflowSpan</p>
 */
class TracerTest {
    
    private CallbackManager callbackManager;
    private StreamWriterManager streamWriterManager;
    private Tracer tracer;
    
    @BeforeEach
    void setUp() {
        callbackManager = new CallbackManager();
        StreamEmitter emitter = new StreamEmitter();
        streamWriterManager = new StreamWriterManager(emitter);
        tracer = new Tracer();
        tracer.init(streamWriterManager, callbackManager);
    }
    
    @Nested
    @DisplayName("Tracer Init Tests")
    class TracerInitTests {
        
        @Test
        @DisplayName("Should create unique trace_id")
        void testInitCreatesTraceId() {
            // Python: assert tracer._trace_id is not None
            //         assert len(tracer._trace_id) > 0
            Tracer t = new Tracer();
            
            assertNotNull(t.getTraceId());
            assertTrue(t.getTraceId().length() > 0);
        }
        
        @Test
        @DisplayName("Should create agent span manager")
        void testInitCreatesAgentSpanManager() {
            // Python: assert tracer.tracer_agent_span_manager is not None
            //         assert isinstance(tracer.tracer_agent_span_manager, SpanManager)
            Tracer t = new Tracer();
            
            assertNotNull(t.getTracerAgentSpanManager());
            assertInstanceOf(SpanManager.class, t.getTracerAgentSpanManager());
        }
        
        @Test
        @DisplayName("Should create empty workflow span manager dict")
        void testInitCreatesEmptyWorkflowSpanDict() {
            // Python: assert tracer.tracer_workflow_span_manager_dict == {}
            Tracer t = new Tracer();
            
            assertNotNull(t.getTracerWorkflowSpanManagerDict());
            assertTrue(t.getTracerWorkflowSpanManagerDict().isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Tracer Init Method Tests")
    class TracerInitMethodTests {
        
        @Test
        @DisplayName("Should register agent and workflow handlers")
        void testInitRegistersHandlers() {
            // Python: tracer.init(stream_writer_manager, callback_manager)
            //         assert "" in tracer.tracer_workflow_span_manager_dict
            Tracer t = new Tracer();
            
            t.init(streamWriterManager, callbackManager);
            
            assertTrue(t.getTracerWorkflowSpanManagerDict().containsKey(""));
        }
        
        @Test
        @DisplayName("Should store stream_writer_manager and callback_manager")
        void testInitStoresManagers() {
            // Python: assert tracer._callback_manager is callback_manager
            //         assert tracer._stream_writer_manager is stream_writer_manager
            Tracer t = new Tracer();
            
            t.init(streamWriterManager, callbackManager);
            
            assertSame(callbackManager, t.getCallbackManager());
            assertSame(streamWriterManager, t.getStreamWriterManager());
        }
    }
    
    @Nested
    @DisplayName("Tracer RegisterWorkflowSpanManager Tests")
    class TracerRegisterWorkflowSpanManagerTests {
        
        @Test
        @DisplayName("Should create and register new span manager for parent_node_id")
        void testRegisterWorkflowSpanManager() {
            // Python: tracer.register_workflow_span_manager(parent_node_id)
            //         assert parent_node_id in tracer.tracer_workflow_span_manager_dict
            //         assert isinstance(tracer.tracer_workflow_span_manager_dict[parent_node_id], SpanManager)
            String parentNodeId = "node_123";
            
            tracer.registerWorkflowSpanManager(parentNodeId);
            
            assertTrue(tracer.getTracerWorkflowSpanManagerDict().containsKey(parentNodeId));
            assertInstanceOf(SpanManager.class, tracer.getTracerWorkflowSpanManagerDict().get(parentNodeId));
        }
    }
    
    @Nested
    @DisplayName("Tracer GetWorkflowSpan Tests")
    class TracerGetWorkflowSpanTests {
        
        @Test
        @DisplayName("Should return None for unknown parent_node_id")
        void testGetWorkflowSpanReturnsNullForUnknownParent() {
            // Python: result = tracer.get_workflow_span("invoke_123", "unknown_parent")
            //         assert result is None
            Object result = tracer.getWorkflowSpan("invoke_123", "unknown_parent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("Should return span from registered manager")
        void testGetWorkflowSpanReturnsSpan() {
            // Python: tracer.tracer_workflow_span_manager_dict[""].create_workflow_span("invoke_123")
            //         result = tracer.get_workflow_span("invoke_123", "")
            //         assert result is not None
            tracer.getTracerWorkflowSpanManagerDict().get("").createWorkflowSpan("invoke_123");
            
            Object result = tracer.getWorkflowSpan("invoke_123", "");
            
            assertNotNull(result);
        }
    }
    
    @Nested
    @DisplayName("Tracer Trigger Tests")
    class TracerTriggerTests {
        
        @Test
        @DisplayName("Should call callback_manager.trigger with correct params")
        void testTriggerCallsCallbackManager() throws Exception {
            // Python: await tracer.trigger("tracer_agent", "on_chain_start", data="test")
            //         tracer._callback_manager.trigger.assert_called_once()
            CallbackManager mockCallbackManager = mock(CallbackManager.class);
            when(mockCallbackManager.trigger(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            Tracer t = new Tracer();
            t.init(streamWriterManager, mockCallbackManager);
            
            t.trigger("tracer_agent", "on_chain_start", Map.of("data", "test")).get();
            
            verify(mockCallbackManager, atLeastOnce()).trigger(anyString(), anyString(), any());
        }
        
        @Test
        @DisplayName("Should append parent_node_id to handler_class_name")
        void testTriggerAppendsParentNodeId() throws Exception {
            // Python: await tracer.trigger("tracer_workflow", "on_call_start", parent_node_id="node_123")
            //         assert "node_123" in call_args[0][0]
            CallbackManager mockCallbackManager = mock(CallbackManager.class);
            when(mockCallbackManager.trigger(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            Tracer t = new Tracer();
            t.init(streamWriterManager, mockCallbackManager);
            
            t.trigger("tracer_workflow", "on_call_start", Map.of("parent_node_id", "node_123")).get();
            
            // Verify that trigger was called with handler name containing node_123
            verify(mockCallbackManager, atLeastOnce()).trigger(contains("node_123"), anyString(), any());
        }
    }
    
    @Nested
    @DisplayName("Tracer PopWorkflowSpan Tests")
    class TracerPopWorkflowSpanTests {
        
        @Test
        @DisplayName("Should remove span from manager")
        void testPopWorkflowSpanRemovesSpan() {
            // Python: tracer.tracer_workflow_span_manager_dict[""].create_workflow_span("invoke_123")
            //         tracer.pop_workflow_span("invoke_123", "")
            //         result = tracer.get_workflow_span("invoke_123", "")
            //         assert result is None
            tracer.getTracerWorkflowSpanManagerDict().get("").createWorkflowSpan("invoke_123");
            
            tracer.popWorkflowSpan("invoke_123", "");
            
            Object result = tracer.getWorkflowSpan("invoke_123", "");
            assertNull(result);
        }
        
        @Test
        @DisplayName("Should not fail for unknown parent_node_id")
        void testPopWorkflowSpanHandlesUnknownParent() {
            // Python: tracer.pop_workflow_span("invoke_123", "unknown_parent")
            //         # Should not raise
            assertDoesNotThrow(() -> tracer.popWorkflowSpan("invoke_123", "unknown_parent"));
        }
    }
}

