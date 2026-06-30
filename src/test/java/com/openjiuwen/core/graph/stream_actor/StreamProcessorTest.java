/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.workflow.component.ComponentAbility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StreamProcessor}.
 */
class StreamProcessorTest {

    @Test
    @DisplayName("generator routes stream chunks to schema iterators and ends cleanly")
    void testGeneratorRoutesChunks() throws Exception {
        StreamProcessor processor = new StreamProcessor("collector", List.of("producer-STREAM"), 1);
        List<Object> callbacks = new CopyOnWriteArrayList<>();

        Map<String, Object> generated = processor.generator(
                Map.of(
                        "value", "${producer.value}",
                        "nested", Map.of("other", "${producer.other}"),
                        "static", 1),
                callbacks::add);

        @SuppressWarnings("unchecked")
        Iterator<Object> valueIterator = (Iterator<Object>) generated.get("value");
        @SuppressWarnings("unchecked")
        Iterator<Object> otherIterator = (Iterator<Object>) ((Map<String, Object>) generated.get("nested")).get("other");

        CompletableFuture<Void> runner = CompletableFuture.runAsync(() -> processor.run(ComponentAbility.STREAM));

        processor.receive(new StreamPayload(
                Map.of("producer", Map.of("value", "alpha", "other", 2)),
                ComponentAbility.STREAM));

        assertTrue(valueIterator.hasNext());
        assertEquals("alpha", valueIterator.next());
        assertTrue(otherIterator.hasNext());
        assertEquals(2, otherIterator.next());
        assertEquals(1, generated.get("static"));
        assertEquals(List.of(Map.of("value", "alpha"), Map.of("nested.other", 2)), callbacks);

        processor.receive(new StreamPayload(Map.of("producer", "END_STREAM"), ComponentAbility.STREAM));

        assertFalse(valueIterator.hasNext());
        assertFalse(otherIterator.hasNext());
        runner.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("helper methods identify end messages and source ownership")
    void testHelperMethods() {
        Map<String, Object> endMessage = Map.of("producer", "END_STREAM");

        assertTrue(StreamProcessor.isEndMessage(endMessage));
        assertEquals("producer", StreamProcessor.getProducerId(endMessage));
        assertTrue(StreamProcessor.isValueFromSource("producer.output", "producer"));
        assertFalse(StreamProcessor.isValueFromSource("another.output", "producer"));
    }
}
