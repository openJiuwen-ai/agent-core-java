/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.harness.schema.DeepLoopEvent;
import com.openjiuwen.harness.schema.DeepLoopEventType;
import com.openjiuwen.harness.schema.LoopEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_loop_event_schema} in
 * {@code tests.unit_tests.harness.test_loop_event_schema}.
 */
class TestLoopEventSchema {

    @Test
    @Tag("level0")
    @DisplayName("deep loop events are ordered by priority then sequence")
    void testDeepLoopEventPriorityOrder() {
        PriorityQueue<DeepLoopEvent> queue = new PriorityQueue<>();
        queue.add(new DeepLoopEvent(10, 2, null, null, DeepLoopEventType.FOLLOWUP, "follow-up", null, null));
        queue.add(new DeepLoopEvent(1, 1, null, null, DeepLoopEventType.STEER, "steer", null, null));
        queue.add(new DeepLoopEvent(10, 1, null, null, DeepLoopEventType.FOLLOWUP, "follow-up-2", null, null));

        assertEquals("steer", queue.poll().getContent());
        assertEquals("follow-up-2", queue.poll().getContent());
        assertEquals("follow-up", queue.poll().getContent());
    }

    @Test
    @Tag("level0")
    @DisplayName("task iteration inputs default optional fields to null or false")
    void testTaskIterationInputsDefaults() {
        DeepLoopEvent loopEvent = DeepLoopEvent.create(1, DeepLoopEventType.FOLLOWUP, "task");

        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(1);
        inputs.setLoopEvent(loopEvent);

        assertEquals(1, inputs.getIteration());
        assertSame(loopEvent, inputs.getLoopEvent());
        assertNull(inputs.getConversationId());
        assertNull(inputs.getResult());
        assertFalse(inputs.isFollowUp());
    }

    @Test
    @Tag("level0")
    @DisplayName("factory applies default priorities for abort steer and followup")
    void testCreateLoopEventDefaultPriorities() {
        DeepLoopEvent abortEvent = LoopEventFactory.createLoopEvent(1, DeepLoopEventType.ABORT, "stop");
        DeepLoopEvent steerEvent = LoopEventFactory.createLoopEvent(2, DeepLoopEventType.STEER, "guide");
        DeepLoopEvent followUpEvent = LoopEventFactory.createLoopEvent(3, DeepLoopEventType.FOLLOWUP, "next");

        assertTrue(abortEvent.getPriority() < steerEvent.getPriority());
        assertTrue(steerEvent.getPriority() < followUpEvent.getPriority());
    }
}
