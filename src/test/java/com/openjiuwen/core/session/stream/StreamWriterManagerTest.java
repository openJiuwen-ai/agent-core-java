/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/core/session/stream/manager.py}.
 */
class StreamWriterManagerTest {

    @Test
    void streamOutputWithCustomWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getCustomWriter().write(Map.of("name", "Alice", "age", 30));
        manager.getCustomWriter().write(Map.of("name", "Bob", "age", 25));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(2, received.size());
        assertInstanceOf(CustomSchema.class, received.get(0));
        assertEquals("Alice", ((CustomSchema) received.get(0)).get("name"));
        assertEquals(25, ((CustomSchema) received.get(1)).get("age"));
    }

    @Test
    void streamOutputWithOutputWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getOutputWriter().write(Map.of("type", "nodeA", "index", 1, "payload", "nodeA_stream"));
        manager.getOutputWriter().write(Map.of("type", "nodeB", "index", 2, "payload", "nodeB_stream"));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(2, received.size());
        OutputSchema first = assertInstanceOf(OutputSchema.class, received.get(0));
        assertEquals("nodeA", first.getType());
        assertEquals(1, first.getIndex());
        assertEquals("nodeA_stream", first.getPayload());
    }

    @Test
    void streamOutputWithTraceWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);

        manager.getTraceWriter().write(Map.of("type", "on_chain_start", "payload", "nodeA_start"));
        manager.getTraceWriter().write(Map.of("type", "on_chain_end", "payload", "nodeA_end"));
        emitter.close();

        List<Object> received = drain(manager);

        assertEquals(2, received.size());
        TraceSchema first = assertInstanceOf(TraceSchema.class, received.get(0));
        assertEquals("on_chain_start", first.getType());
        assertEquals("nodeA_start", first.getPayload());
    }

    @Test
    void streamOutputWithMockWriter() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        String mockMode = "mock";
        manager.addWriter(mockMode, new StreamWriter<>(emitter, MockSchema.class, MockSchema::fromMap));

        manager.getWriter(mockMode).write(Map.of("data", "nodeA_stream"));
        manager.getWriter(mockMode).write(Map.of("data", "nodeB_stream"));
        emitter.close();

        List<Object> received = drain(manager);

        assertTrue(manager.getEnabledModes().contains(mockMode));
        assertEquals(2, received.size());
        MockSchema first = assertInstanceOf(MockSchema.class, received.get(0));
        assertEquals("nodeA_stream", first.data());
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
