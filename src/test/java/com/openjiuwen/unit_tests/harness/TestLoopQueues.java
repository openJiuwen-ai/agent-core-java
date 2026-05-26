/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.task_loop.LoopQueues;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for loop queues.
 */
class TestLoopQueues {

    @Test
    @Tag("level0")
    @DisplayName("Loop queues manage events correctly")
    void testLoopQueuesManageEvents() {
        LoopQueues queues = new LoopQueues();
        assertNotNull(queues, "LoopQueues should be constructable");
        
        // Test steering queue
        queues.pushSteer("steer message");
        assertEquals(1, queues.getSteering().size(), "Steering queue should have 1 message");
        
        // Test follow_up queue
        queues.pushFollowUp("follow_up message");
        assertEquals(1, queues.getFollowUp().size(), "Follow_up queue should have 1 message");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Loop queues drain correctly")
    void testLoopQueuesDrain() {
        LoopQueues queues = new LoopQueues();
        queues.pushSteer("msg1");
        queues.pushSteer("msg2");
        
        var drained = queues.drainSteering();
        assertEquals(2, drained.size(), "Drained steering should have 2 messages");
        assertEquals(0, queues.getSteering().size(), "Steering queue should be empty after drain");
    }
}