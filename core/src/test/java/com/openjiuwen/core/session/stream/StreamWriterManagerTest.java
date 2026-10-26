/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.session.AgentGroupSession;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.WorkflowSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/core/session/stream/manager.py}.
 *
 * <p>Mirrors Python's {@code TestStreamOutput} in
 * {@code tests/unit_tests/core/session/stream/test_stream_output.py}.</p>
 */
class StreamWriterManagerTest {

    @Test
    void sessionFacadesExposeBufferedOutputBeforeFailure() {
        AgentSession parent = new AgentSession("failed-workflow-parent", null, null);
        List<AgentSessionApi> sessions = List.of(new AgentSession("failed-agent", null, null),
                new AgentGroupSession("failed-group"), new WorkflowSession(parent.getInner(), "failed-workflow"));
        for (AgentSessionApi session : sessions) {
            session.writeStream(new OutputSchema("test", 0, session.getSessionId()));
            IllegalStateException failure = new IllegalStateException(session.getSessionId());

            Iterator<Object> iterator = session.streamIterator(failure);

            OutputSchema output = assertInstanceOf(OutputSchema.class, iterator.next());
            assertEquals(session.getSessionId(), output.getPayload());
            assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    @Timeout(2)
    void failedProducerDrainsFullQueueBeforeOriginalFailure() {
        StreamEmitter emitter = new StreamEmitter(new AsyncStreamQueue(2));
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        emitter.emit("second");
        IllegalStateException failure = new IllegalStateException("producer failed");

        Iterator<Object> iterator = manager.streamIterator(failure);

        assertTrue(emitter.isClosed());
        assertTrue(emitter.getStreamQueue().isClosed());
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertEquals("first", iterator.next());
        assertEquals("second", iterator.next());
        assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
        assertFalse(iterator.hasNext());
        assertThrows(java.util.NoSuchElementException.class, iterator::next);
    }

    @Test
    void failedProducerWithNoOutputReportsFailureOnce() {
        StreamWriterManager manager = new StreamWriterManager(new StreamEmitter());
        IllegalStateException failure = new IllegalStateException("no output");

        Iterator<Object> iterator = manager.streamIterator(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, iterator::next));
        assertFalse(iterator.hasNext());
    }

    @Test
    void queuedEndFrameDoesNotHideFailureOrLeakIntoOutput() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("chunk");
        emitter.close();
        IllegalStateException failure = new IllegalStateException("after close");

        Iterator<Object> iterator = manager.streamIterator(failure);

        assertEquals("chunk", iterator.next());
        assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
        assertFalse(iterator.hasNext());
    }

    @Test
    void streamOutputWithCustomWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getCustomWriter().write(Map.of("name", "Alice", "age", 30));
        manager.getCustomWriter().write(Map.of("name", "Bob", "age", 25));
        manager.getCustomWriter().write(Map.of("name", "Charlie", "age", 35));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(3, received.size());
        assertInstanceOf(CustomSchema.class, received.get(0));
        assertEquals("Alice", ((CustomSchema) received.get(0)).get("name"));
        assertEquals(25, ((CustomSchema) received.get(1)).get("age"));
        assertEquals("Charlie", ((CustomSchema) received.get(2)).get("name"));
    }

    @Test
    void streamOutputWithOutputWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getOutputWriter().write(Map.of("type", "nodeA", "index", 1, "payload", "nodeA_stream"));
        manager.getOutputWriter().write(Map.of("type", "nodeB", "index", 1, "payload", "nodeB_stream"));
        manager.getOutputWriter().write(Map.of("type", "nodeC", "index", 1, "payload", "nodeC_stream"));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(3, received.size());
        OutputSchema first = assertInstanceOf(OutputSchema.class, received.get(0));
        assertEquals("nodeA", first.getType());
        assertEquals(1, first.getIndex());
        assertEquals("nodeA_stream", first.getPayload());
        OutputSchema third = assertInstanceOf(OutputSchema.class, received.get(2));
        assertEquals("nodeC", third.getType());
        assertEquals("nodeC_stream", third.getPayload());
    }

    @Test
    void streamOutputWithTraceWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getTraceWriter().write(Map.of("type", "on_chain_start", "payload", "nodeA_start"));
        manager.getTraceWriter().write(Map.of("type", "on_chain_end", "payload", "nodeA_end"));
        manager.getTraceWriter().write(Map.of("type", "on_chain_error", "payload", "nodeA_error"));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(3, received.size());
        TraceSchema first = assertInstanceOf(TraceSchema.class, received.get(0));
        assertEquals("on_chain_start", first.getType());
        assertEquals("nodeA_start", first.getPayload());
        TraceSchema third = assertInstanceOf(TraceSchema.class, received.get(2));
        assertEquals("on_chain_error", third.getType());
        assertEquals("nodeA_error", third.getPayload());
    }

    @Test
    void streamOutputWithMockWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        String mockMode = "mock";
        manager.addWriter(mockMode, new StreamWriter<>(emitter, MockSchema.class, MockSchema::fromMap));

        manager.getWriter(mockMode).write(Map.of("data", "nodeA_stream"));
        manager.getWriter(mockMode).write(Map.of("data", "nodeB_stream"));
        manager.getWriter(mockMode).write(Map.of("data", "nodeC_stream"));
        emitter.close();

        List<Object> received = drain(manager);

        assertTrue(manager.getEnabledModes().contains(mockMode));
        assertEquals(3, received.size());
        MockSchema first = assertInstanceOf(MockSchema.class, received.get(0));
        assertEquals("nodeA_stream", first.data());
        MockSchema third = assertInstanceOf(MockSchema.class, received.get(2));
        assertEquals("nodeC_stream", third.data());
    }

    private static List<Object> drain(StreamWriterManager manager) {
        Iterator<Object> iterator = manager.streamIterator();
        java.util.ArrayList<Object> items = new java.util.ArrayList<>();
        while (iterator.hasNext()) {
            items.add(iterator.next());
        }
        return items;
    }

    private record MockSchema(String data) {
        private static MockSchema fromMap(Map<String, Object> value) {
            return new MockSchema(String.valueOf(value.get("data")));
        }
    }
}
