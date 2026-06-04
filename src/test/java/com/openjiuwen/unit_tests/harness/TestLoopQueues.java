/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.task_loop.LoopQueues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_loop_queues} in
 * {@code tests.unit_tests.harness.test_loop_queues}.
 */
class TestLoopQueues {

    @Test
    @Tag("level0")
    @DisplayName("push and drain steering queue")
    void testPushAndDrainSteering() {
        LoopQueues queues = new LoopQueues();
        queues.pushSteer("msg1");
        queues.pushSteer("msg2");

        assertEquals(java.util.List.of("msg1", "msg2"), queues.drainSteering());
        assertEquals(java.util.List.of(), queues.drainSteering());
    }

    @Test
    @Tag("level0")
    @DisplayName("push and drain follow up queue")
    void testPushAndDrainFollowUp() {
        LoopQueues queues = new LoopQueues();
        queues.pushFollowUp("fu1");
        queues.pushFollowUp("fu2");
        queues.pushFollowUp("fu3");

        assertEquals(java.util.List.of("fu1", "fu2", "fu3"), queues.drainFollowUp());
        assertEquals(java.util.List.of(), queues.drainFollowUp());
    }

    @Test
    @Tag("level0")
    @DisplayName("steering and follow up queues stay independent")
    void testQueuesAreIndependent() {
        LoopQueues queues = new LoopQueues();
        queues.pushSteer("steer1");
        queues.pushFollowUp("follow1");

        assertEquals(java.util.List.of("steer1"), queues.drainSteering());
        assertEquals(java.util.List.of("follow1"), queues.drainFollowUp());
        assertEquals(java.util.List.of(), queues.drainSteering());
        assertEquals(java.util.List.of(), queues.drainFollowUp());
    }

    @Test
    @Tag("level0")
    @DisplayName("draining empty queues returns empty lists")
    void testDrainEmptyQueue() {
        LoopQueues queues = new LoopQueues();
        assertEquals(java.util.List.of(), queues.drainSteering());
        assertEquals(java.util.List.of(), queues.drainFollowUp());
    }

    @Test
    @Tag("level0")
    @DisplayName("multiple drain cycles preserve FIFO order")
    void testMultipleDrainCycles() {
        LoopQueues queues = new LoopQueues();

        queues.pushSteer("a");
        assertEquals(java.util.List.of("a"), queues.drainSteering());

        queues.pushSteer("b");
        queues.pushSteer("c");
        assertEquals(java.util.List.of("b", "c"), queues.drainSteering());
    }

    @Test
    @Tag("level0")
    @DisplayName("hasFollowUp checks queue state without consuming")
    void testHasFollowUpDoesNotConsume() {
        LoopQueues queues = new LoopQueues();
        queues.pushFollowUp("fu1");

        assertTrue(queues.hasFollowUp());
        assertTrue(queues.hasFollowUp());
        assertEquals(java.util.List.of("fu1"), queues.drainFollowUp());
        assertFalse(queues.hasFollowUp());
    }
}
