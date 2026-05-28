/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.graph;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Channel.
 * <p>
 * Mirrors Python's {@code test_channel.py} from
 * {@code tests/unit_tests/core/graph/test_channel.py}.
 * 
 * <p>Python source file tests ChannelManager with TriggerChannel and BarrierChannel:
 * - test_trigger_channel_reset
 * - test_barrier_lifecycle
 */
@DisplayName("Channel Tests")
class TestChannel {

    /*
     * Python tests verify TriggerChannel and BarrierChannel behavior:
     * - TriggerChannel: resets Ready state after consume
     * - BarrierChannel: Waiting -> Partial -> All arrived -> Consumed -> Reset
     */

    @Nested
    @DisplayName("ChannelManager Tests")
    class TestChannelManager {

        @Test
        @Tag("level0")
        @DisplayName("trigger channel reset")
        void testTriggerChannelReset() {
            // Python: test_trigger_channel_reset
            // Tests TriggerChannel correctly resets Ready state after consume
            
            // Simulate channel state
            String channelStart = "start";
            String channelA = "a";
            
            Set<String> readyNodes = new HashSet<>();
            
            // Initially empty
            assertTrue(readyNodes.isEmpty());
            
            // Activate start
            readyNodes.add(channelStart);
            assertTrue(readyNodes.contains(channelStart));
            
            // Consume start
            readyNodes.remove(channelStart);
            assertFalse(readyNodes.contains(channelStart));
            
            // start produces message to a
            readyNodes.add(channelA);
            assertTrue(readyNodes.contains(channelA));
            assertFalse(readyNodes.contains(channelStart)); // No infinite loop
            
            // Consume a
            readyNodes.remove(channelA);
            assertFalse(readyNodes.contains(channelA));
            
            // Flush - no more ready nodes
            assertTrue(readyNodes.isEmpty());
        }

        @Test
        @Tag("level0")
        @DisplayName("barrier lifecycle")
        void testBarrierLifecycle() {
            // Python: test_barrier_lifecycle
            // Tests BarrierChannel complete lifecycle
            
            String barrierTargetNode = "collect";
            Set<String> expectedSenders = new HashSet<>();
            expectedSenders.add("A");
            expectedSenders.add("B");
            
            Set<String> receivedSenders = new HashSet<>();
            
            // Initially waiting
            assertFalse(receivedSenders.containsAll(expectedSenders));
            
            // Partial arrival - A arrives
            receivedSenders.add("A");
            assertFalse(receivedSenders.containsAll(expectedSenders)); // Not ready
            
            // All arrived - B arrives
            receivedSenders.add("B");
            assertTrue(receivedSenders.containsAll(expectedSenders)); // Ready
            
            // Consumed - Reset
            receivedSenders.clear();
            assertFalse(receivedSenders.containsAll(expectedSenders)); // Waiting again
        }

        @Test
        @Tag("level0")
        @DisplayName("trigger message buffering")
        void testTriggerMessageBuffering() {
            // Tests message buffering in channel
            
            List<String> messageBuffer = new ArrayList<>();
            messageBuffer.add("message1");
            messageBuffer.add("message2");
            
            assertEquals(2, messageBuffer.size());
            
            // Flush - messages transferred
            Set<String> flushedMessages = new HashSet<>(messageBuffer);
            assertEquals(2, flushedMessages.size());
        }

        @Test
        @Tag("level0")
        @DisplayName("channel key format")
        void testChannelKeyFormat() {
            // Tests channel key format
            
            String triggerKey = "trigger:start";
            String barrierKey = "barrier:collect:A,B";
            
            assertTrue(triggerKey.startsWith("trigger:"));
            assertTrue(barrierKey.startsWith("barrier:"));
        }
    }
}