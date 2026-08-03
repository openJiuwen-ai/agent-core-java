/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python focused stream actor behavior in
 * {@code openjiuwen/core/graph/stream_actor/base.py}.
 */
class StreamProcessorTest {

    @Test
    @DisplayName("active source in an alternative group closes inactive source queues")
    void activeSourceInAlternativeGroupClosesInactiveSourceQueues() throws Exception {
        StreamProcessor processor = new StreamProcessor(
                "consumer",
                List.of(List.of("left-stream", "right-transform")),
                1.0d);
        List<Map<String, Object>> callbacks = new ArrayList<>();
        Map<String, Object> generated = processor.generator(
                Map.of("left", "${left.answer}", "right", "${right.answer}"),
                callbacks::add);
        Iterator<Object> left = iterator(generated, "left");
        Iterator<Object> right = iterator(generated, "right");

        Thread runner = new Thread(() -> processor.run(ComponentAbility.COLLECT));
        runner.start();
        processor.receive(new StreamPayload(Map.of("left", Map.of("answer", "L")), ComponentAbility.STREAM));

        assertTrue(left.hasNext());
        assertEquals("L", left.next());
        assertEquals(List.of(Map.of("left", "L")), callbacks);
        assertFalse(right.hasNext());

        processor.receive(new StreamPayload(Map.of("left", "END_left"), ComponentAbility.STREAM));
        runner.join(1000L);
        assertFalse(runner.isAlive());
    }

    @Test
    @DisplayName("source groups finish when any source in each group ends")
    void sourceGroupsFinishWhenAnySourceInEachGroupEnds() throws Exception {
        StreamProcessor processor = new StreamProcessor(
                "consumer",
                List.of(List.of("a-stream", "b-stream"), List.of("c-transform")),
                1.0d);
        Map<String, Object> generated = processor.generator(
                Map.of("a", "${a.value}", "c", "${c.value}"),
                null);
        Iterator<Object> a = iterator(generated, "a");
        Iterator<Object> c = iterator(generated, "c");

        Thread runner = new Thread(() -> processor.run(ComponentAbility.COLLECT));
        runner.start();
        processor.receive(new StreamPayload(Map.of("a", Map.of("value", "A")), ComponentAbility.STREAM));
        processor.receive(new StreamPayload(Map.of("a", "END_a"), ComponentAbility.STREAM));
        processor.receive(new StreamPayload(Map.of("c", Map.of("value", "C")), ComponentAbility.TRANSFORM));
        processor.receive(new StreamPayload(Map.of("c", "END_c"), ComponentAbility.TRANSFORM));

        assertTrue(a.hasNext());
        assertEquals("A", a.next());
        assertTrue(c.hasNext());
        assertEquals("C", c.next());
        assertFalse(a.hasNext());
        assertFalse(c.hasNext());
        runner.join(1000L);
        assertFalse(runner.isAlive());
    }

    @Test
    @DisplayName("undeclared source paths use generator timeout")
    void undeclaredSourcePathsUseGeneratorTimeout() {
        StreamProcessor processor = new StreamProcessor(
                "consumer",
                List.of(List.of("declared-stream")),
                0.05d);
        Map<String, Object> generated = processor.generator(Map.of("ghost", "${ghost.value}"), null);
        Iterator<Object> ghost = iterator(generated, "ghost");

        assertFalse(ghost.hasNext());
    }

    @Test
    @DisplayName("message validation mirrors Python producer id checks")
    void messageValidationMirrorsPythonProducerIdChecks() {
        assertEquals("node", StreamProcessor.getProducerId(Map.of("node", "END_node")));
        assertTrue(StreamProcessor.isEndMessage(Map.of("node", "END_node")));
        assertFalse(StreamProcessor.isEndMessage(Map.of("node", Map.of("value", "chunk"))));
        assertThrows(IllegalArgumentException.class, () -> StreamProcessor.getProducerId(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> StreamProcessor.isEndMessage("invalid"));
    }

    @SuppressWarnings("unchecked")
    private static Iterator<Object> iterator(Map<String, Object> generated, String key) {
        return (Iterator<Object>) generated.get(key);
    }
}
