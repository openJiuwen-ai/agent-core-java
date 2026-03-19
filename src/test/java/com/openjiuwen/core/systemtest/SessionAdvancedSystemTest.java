/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.session.callback.BaseHandler;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.tracer.Tracer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced Session system tests covering gaps identified in CHECK doc:
 * InMemoryCheckpointer, StreamEmitter/StreamWriterManager, CallbackManager,
 * Tracer, InteractiveInput, AgentInterrupt.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class SessionAdvancedSystemTest {

    @Nested
    @DisplayName("InMemoryCheckpointer Tests")
    class CheckpointerTests {

        @Test
        @DisplayName("InMemoryCheckpointer creation")
        void testCheckpointerCreation() {
            InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
            assertNotNull(checkpointer);
            assertNotNull(checkpointer.graphStore(), "graphStore should be available");
        }

        @Test
        @DisplayName("Checkpointer sessionExists returns false for unknown session")
        void testSessionNotExists() {
            InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
            assertFalse(checkpointer.sessionExists("nonexistent_session"));
        }

        @Test
        @DisplayName("Checkpointer release does not throw for unknown session")
        void testReleaseUnknownSession() {
            InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
            assertDoesNotThrow(() -> checkpointer.release("nonexistent_session"));
        }

        @Test
        @DisplayName("Checkpointer.buildKey joins parts")
        void testBuildKey() {
            String key = Checkpointer.buildKey("session", "workflow", "node");
            assertNotNull(key);
            assertTrue(key.contains("session"));
            assertTrue(key.contains("workflow"));
            assertTrue(key.contains("node"));
            System.out.println("[Checkpointer] Key: " + key);
        }

        @Test
        @DisplayName("Checkpointer namespace constants")
        void testNamespaceConstants() {
            assertEquals("agent", Checkpointer.SESSION_NAMESPACE_AGENT);
            assertEquals("workflow", Checkpointer.SESSION_NAMESPACE_WORKFLOW);
            assertEquals("workflow-graph", Checkpointer.WORKFLOW_NAMESPACE_GRAPH);
        }
    }

    @Nested
    @DisplayName("StreamEmitter Tests")
    class StreamEmitterTests {

        @Test
        @DisplayName("StreamEmitter emit and close lifecycle")
        void testEmitAndClose() {
            StreamEmitter emitter = new StreamEmitter();
            assertFalse(emitter.isClosed());

            emitter.emit("chunk_1");
            emitter.emit("chunk_2");
            emitter.close();
            assertTrue(emitter.isClosed());
        }

        @Test
        @DisplayName("StreamEmitter provides stream queue")
        void testStreamQueue() {
            StreamEmitter emitter = new StreamEmitter();
            assertNotNull(emitter.getStreamQueue(), "StreamQueue should be accessible");
        }
    }

    @Nested
    @DisplayName("StreamWriterManager Tests")
    class StreamWriterManagerTests {

        @Test
        @DisplayName("StreamWriterManager creation with modes")
        void testCreationWithModes() {
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager manager = StreamWriterManager.createManager(
                    emitter, List.of(StreamMode.OUTPUT, StreamMode.TRACE));

            assertNotNull(manager);
            assertNotNull(manager.getStreamEmitter());
        }

        @Test
        @DisplayName("StreamWriterManager provides typed writers")
        void testTypedWriters() {
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager manager = StreamWriterManager.createManager(
                    emitter, List.of(StreamMode.OUTPUT, StreamMode.TRACE, StreamMode.CUSTOM));

            StreamWriter<OutputSchema> outputWriter = manager.getOutputWriter();
            assertNotNull(outputWriter, "Output writer should be available");

            assertNotNull(manager.getTraceWriter(), "Trace writer should be available");
            assertNotNull(manager.getCustomWriter(), "Custom writer should be available");
        }

        @Test
        @DisplayName("StreamWriterManager collectStreamOutput")
        void testCollectStreamOutput() {
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager manager = StreamWriterManager.createManager(
                    emitter, List.of(StreamMode.OUTPUT));

            // Emit some data then close
            emitter.emit("data_1");
            emitter.emit("data_2");
            emitter.close();

            List<Object> collected = manager.collectStreamOutput();
            assertNotNull(collected);
            System.out.println("[StreamWriterManager] Collected: " + collected.size() + " items");
        }
    }

    @Nested
    @DisplayName("StreamMode Tests")
    class StreamModeTests {

        @Test
        @DisplayName("StreamMode enum values")
        void testStreamModeValues() {
            assertEquals("output", StreamMode.OUTPUT.getMode());
            assertEquals("trace", StreamMode.TRACE.getMode());
            assertEquals("custom", StreamMode.CUSTOM.getMode());
        }

        @Test
        @DisplayName("StreamMode has descriptions")
        void testStreamModeDescriptions() {
            assertNotNull(StreamMode.OUTPUT.getDesc());
            assertNotNull(StreamMode.TRACE.getDesc());
            assertFalse(StreamMode.OUTPUT.getDesc().isEmpty());
        }
    }

    @Nested
    @DisplayName("CallbackManager Tests")
    class CallbackManagerTests {

        static class TestHandler extends BaseHandler {
            private final AtomicBoolean invoked = new AtomicBoolean(false);

            TestHandler() {
                super(new Object());
            }

            @Override
            public String eventName() {
                return "test_handler";
            }

            @TriggerEvent
            public void onTestEvent(Map<String, Object> kwargs) {
                invoked.set(true);
            }

            boolean wasInvoked() {
                return invoked.get();
            }
        }

        @Test
        @DisplayName("CallbackManager register and retrieve handler")
        void testRegisterAndRetrieve() {
            CallbackManager manager = new CallbackManager();
            TestHandler handler = new TestHandler();

            manager.register(Map.of("test_handler", handler));

            BaseHandler retrieved = manager.getHandler("test_handler");
            assertNotNull(retrieved);
            assertTrue(retrieved instanceof TestHandler);
        }

        @Test
        @DisplayName("CallbackManager trigger event resolution works")
        void testTrigger() {
            CallbackManager manager = new CallbackManager();
            TestHandler handler = new TestHandler();
            manager.register(Map.of("test_handler", handler));

            // Verify handler's trigger events are discoverable
            List<String> events = handler.getTriggerEvents();
            assertFalse(events.isEmpty(), "TestHandler should have trigger events");
            assertTrue(events.contains("onTestEvent"), "Should find onTestEvent");

            // Verify handler is retrievable
            BaseHandler retrieved = manager.getHandler("test_handler");
            assertNotNull(retrieved);
            assertEquals("test_handler", retrieved.eventName());
        }
    }

    @Nested
    @DisplayName("Tracer Tests")
    class TracerTests {

        @Test
        @DisplayName("Tracer generates unique traceId")
        void testTracerCreation() {
            Tracer tracer = new Tracer();
            assertNotNull(tracer.getTraceId());
            assertFalse(tracer.getTraceId().isEmpty());
            System.out.println("[Tracer] TraceId: " + tracer.getTraceId());
        }

        @Test
        @DisplayName("Two tracers have different traceIds")
        void testTracerUniqueness() {
            Tracer t1 = new Tracer();
            Tracer t2 = new Tracer();
            assertFalse(t1.getTraceId().equals(t2.getTraceId()),
                    "Different tracers should have different IDs");
        }

        @Test
        @DisplayName("Tracer init with stream writer and callback managers")
        void testTracerInit() {
            Tracer tracer = new Tracer();
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager swm = StreamWriterManager.createManager(
                    emitter, List.of(StreamMode.TRACE));
            CallbackManager cbm = new CallbackManager();

            assertDoesNotThrow(() -> tracer.init(swm, cbm));
            assertNotNull(tracer.getTracerAgentSpanManager());
        }
    }

    @Nested
    @DisplayName("InteractiveInput Tests")
    class InteractiveInputTests {

        @Test
        @DisplayName("InteractiveInput default construction")
        void testDefaultConstruction() {
            InteractiveInput input = new InteractiveInput();
            assertNotNull(input);
            assertNotNull(input.getUserInputs());
        }

        @Test
        @DisplayName("InteractiveInput with raw inputs")
        void testRawInputs() {
            InteractiveInput input = new InteractiveInput("raw user message");
            assertEquals("raw user message", input.getRawInputs());
        }

        @Test
        @DisplayName("InteractiveInput update for specific node")
        void testUpdateNodeInput() {
            InteractiveInput input = new InteractiveInput();
            input.update("node_1", Map.of("key", "value"));

            Map<String, Object> userInputs = input.getUserInputs();
            assertNotNull(userInputs.get("node_1"));
            assertEquals(Map.of("key", "value"), userInputs.get("node_1"));
        }
    }

    @Nested
    @DisplayName("AgentInterrupt Tests")
    class AgentInterruptTests {

        @Test
        @DisplayName("AgentInterrupt is a RuntimeException")
        void testAgentInterruptException() {
            AgentInterrupt interrupt = new AgentInterrupt("paused for user input");
            assertTrue(interrupt instanceof RuntimeException);
            assertEquals("paused for user input", interrupt.getMessage());
        }

        @Test
        @DisplayName("AgentInterrupt can be thrown and caught")
        void testAgentInterruptThrow() {
            assertThrows(AgentInterrupt.class, () -> {
                throw new AgentInterrupt("test interrupt");
            });
        }

        @Test
        @DisplayName("AgentInterrupt default construction")
        void testAgentInterruptDefault() {
            AgentInterrupt interrupt = new AgentInterrupt();
            assertNotNull(interrupt);
        }
    }
}
