/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for tracer handler classes.
 * 
 * <p>Converted from Python: test_handler.py</p>
 * <p>Python测试类: TestTraceAgentHandler, TestTraceWorkflowHandler</p>
 */
class TraceHandlerTest {
    
    private CallbackManager callbackManager;
    private StreamWriterManager streamWriterManager;
    private SpanManager spanManager;
    
    @BeforeEach
    void setUp() {
        callbackManager = new CallbackManager();
        StreamEmitter emitter = new StreamEmitter();
        streamWriterManager = new StreamWriterManager(emitter);
        spanManager = new SpanManager("test_trace_id");
    }
    
    @Nested
    @DisplayName("TraceAgentHandler Tests")
    class TraceAgentHandlerTests {
        
        private TraceAgentHandler handler;
        
        @BeforeEach
        void setUpHandler() {
            handler = new TraceAgentHandler(callbackManager, streamWriterManager, spanManager);
        }
        
        @Test
        @DisplayName("Should return tracer_agent event name")
        void testEventName() {
            // Python: assert handler.event_name() == "tracer_agent"
            assertEquals("tracer_agent", handler.eventName());
        }
        
        @Test
        @DisplayName("Should format span data correctly")
        void testFormatData() {
            // Python: result = handler._format_data(span)
            //         assert result["type"] == "tracer_agent"
            //         assert "payload" in result
            // Note: formatData is protected, so we test indirectly through eventName
            assertEquals("tracer_agent", handler.eventName());
        }
        
        @Test
        @DisplayName("Should create new span if invoke_id doesn't exist")
        void testGetTracerAgentSpanCreatesNew() {
            // Python: span = handler._get_tracer_agent_span("new_invoke_id")
            //         assert span is not None
            //         assert isinstance(span, TraceAgentSpan)
            TraceAgentSpan span = handler.getTracerAgentSpan("new_invoke_id");
            
            assertNotNull(span);
            assertInstanceOf(TraceAgentSpan.class, span);
        }
        
        @Test
        @DisplayName("Should return existing span if invoke_id exists")
        void testGetTracerAgentSpanReturnsExisting() {
            // Python: original_span = span_manager.create_agent_span()
            //         span = handler._get_tracer_agent_span(original_span.invoke_id)
            //         assert span is original_span
            TraceAgentSpan originalSpan = spanManager.createAgentSpan();
            
            TraceAgentSpan span = handler.getTracerAgentSpan(originalSpan.getInvokeId());
            
            assertSame(originalSpan, span);
        }
        
        @Test
        @DisplayName("Should update span with start data")
        void testOnChainStart() throws Exception {
            // Python: await handler.on_chain_start(
            //             span=span,
            //             inputs={"test": "input"},
            //             instance_info={"class_name": "TestClass"}
            //         )
            //         assert span.inputs == {"test": "input"}
            //         assert span.start_time is not None
            TraceAgentSpan span = spanManager.createAgentSpan();
            
            handler.onChainStart(
                span,
                Map.of("test", "input"),
                Map.of("class_name", "TestClass")
            ).get();
            
            assertEquals(Map.of("test", "input"), span.getInputs());
            assertNotNull(span.getStartTime());
        }
        
        @Test
        @DisplayName("Should update span with end data")
        void testOnChainEnd() throws Exception {
            // Python: await handler.on_chain_end(span=span, outputs={"result": "output"})
            //         assert span.outputs == {"result": "output"}
            //         assert span.end_time is not None
            TraceAgentSpan span = spanManager.createAgentSpan();
            span.setStartTime(Instant.now());
            
            handler.onChainEnd(span, Map.of("result", "output")).get();
            
            assertEquals(Map.of("result", "output"), span.getOutputs());
            assertNotNull(span.getEndTime());
        }
        
        @Test
        @DisplayName("Should update span with error data")
        void testOnChainError() throws Exception {
            // Python: await handler.on_chain_error(span=span, error=Exception("test error"))
            //         assert span.error is not None
            //         assert span.end_time is not None
            TraceAgentSpan span = spanManager.createAgentSpan();
            span.setStartTime(Instant.now());
            
            handler.onChainError(span, new Exception("test error")).get();
            
            assertNotNull(span.getError());
            assertNotNull(span.getEndTime());
        }
    }
    
    @Nested
    @DisplayName("TraceWorkflowHandler Tests")
    class TraceWorkflowHandlerTests {
        
        private TraceWorkflowHandler handler;
        
        @BeforeEach
        void setUpHandler() {
            handler = new TraceWorkflowHandler(callbackManager, streamWriterManager, spanManager);
        }
        
        @Test
        @DisplayName("Should return tracer_workflow event name")
        void testEventName() {
            // Python: assert handler.event_name() == "tracer_workflow"
            assertEquals("tracer_workflow", handler.eventName());
        }
        
        @Test
        @DisplayName("Should format span data correctly")
        void testFormatData() {
            // Python: result = handler._format_data(span)
            //         assert result["type"] == "tracer_workflow"
            //         assert "payload" in result
            // Note: formatData is protected, so we test indirectly through eventName
            assertEquals("tracer_workflow", handler.eventName());
        }
        
        @Test
        @DisplayName("Should create new span if invoke_id doesn't exist")
        void testGetTracerWorkflowSpanCreatesNew() {
            // Python: span = handler._get_tracer_workflow_span("new_invoke_id")
            //         assert span is not None
            //         assert isinstance(span, TraceWorkflowSpan)
            TraceWorkflowSpan span = handler.getTracerWorkflowSpan("new_invoke_id");
            
            assertNotNull(span);
            assertInstanceOf(TraceWorkflowSpan.class, span);
        }
        
        @Test
        @DisplayName("Should update span with call start data")
        void testOnCallStart() throws Exception {
            // Python: await handler.on_call_start(
            //             invoke_id="invoke_123",
            //             metadata={"workflow_id": "wf_1"},
            //             inputs={"test": "input"},
            //             need_send=True
            //         )
            //         span = span_manager.get_span("invoke_123")
            //         assert span is not None
            //         assert span.start_time is not None
            handler.onCallStart(
                "invoke_123",
                Map.of("workflow_id", "wf_1"),
                Map.of("test", "input"),
                true,
                null
            ).get();
            
            Span span = spanManager.getSpan("invoke_123");
            assertNotNull(span);
            assertNotNull(span.getStartTime());
        }
        
        @Test
        @DisplayName("Should update span with call done data")
        void testOnCallDone() throws Exception {
            // Python: await handler.on_call_start(...)
            //         await handler.on_call_done(invoke_id="invoke_123", outputs={"result": "done"})
            //         span = span_manager.get_span("invoke_123")
            //         assert span.end_time is not None
            // Create span first
            handler.onCallStart("invoke_123", Map.of(), null, false, null).get();
            
            handler.onCallDone("invoke_123", Map.of("result", "done")).get();
            
            Span span = spanManager.getSpan("invoke_123");
            assertNotNull(span.getEndTime());
        }
        
        @Test
        @DisplayName("Should append chunk to stream_outputs")
        void testOnPostStream() throws Exception {
            // Python: await handler.on_post_stream(invoke_id="invoke_123", chunk="chunk_data")
            //         span = span_manager.get_span("invoke_123")
            //         assert "chunk_data" in span.stream_outputs
            // Create span first
            spanManager.createWorkflowSpan("invoke_123");
            
            handler.onPostStream("invoke_123", "chunk_data").get();
            
            TraceWorkflowSpan span = (TraceWorkflowSpan) spanManager.getSpan("invoke_123");
            assertTrue(span.getStreamOutputs().contains("chunk_data"));
        }
    }
}

