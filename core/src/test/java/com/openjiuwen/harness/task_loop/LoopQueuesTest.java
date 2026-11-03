/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_loop_queues.py} in
 * {@code tests/unit_tests/harness/test_loop_queues.py}.
 */
class LoopQueuesTest {

    @Test
    void pushAndDrainSteering() {
        LoopQueues queues = new LoopQueues();
        queues.pushSteer("msg1");
        queues.pushSteer("msg2");

        assertEquals(List.of("msg1", "msg2"), queues.drainSteering());
        assertEquals(List.of(), queues.drainSteering());
    }

    @Test
    void pushAndDrainFollowUp() {
        LoopQueues queues = new LoopQueues();
        queues.pushFollowUp("fu1");
        queues.pushFollowUp("fu2");
        queues.pushFollowUp("fu3");

        assertEquals(List.of("fu1", "fu2", "fu3"), queues.drainFollowUp());
        assertEquals(List.of(), queues.drainFollowUp());
    }

    @Test
    void queuesAreIndependent() {
        LoopQueues queues = new LoopQueues();
        queues.pushSteer("steer1");
        queues.pushFollowUp("follow1");

        assertEquals(List.of("steer1"), queues.drainSteering());
        assertEquals(List.of("follow1"), queues.drainFollowUp());
        assertEquals(List.of(), queues.drainSteering());
        assertEquals(List.of(), queues.drainFollowUp());
    }

    @Test
    void drainEmptyQueue() {
        LoopQueues queues = new LoopQueues();

        assertEquals(List.of(), queues.drainSteering());
        assertEquals(List.of(), queues.drainFollowUp());
    }

    @Test
    void multipleDrainCycles() {
        LoopQueues queues = new LoopQueues();

        queues.pushSteer("a");
        assertEquals(List.of("a"), queues.drainSteering());

        queues.pushSteer("b");
        queues.pushSteer("c");
        assertEquals(List.of("b", "c"), queues.drainSteering());
    }

    @Test
    void hasFollowUpDoesNotConsume() {
        LoopQueues queues = new LoopQueues();
        queues.pushFollowUp("fu1");

        assertTrue(queues.hasFollowUp());
        assertTrue(queues.hasFollowUp());
        assertEquals(List.of("fu1"), queues.drainFollowUp());
        assertFalse(queues.hasFollowUp());
    }
}
