/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.pregel.TriggerMessage;
import com.openjiuwen.core.graph.pregel.BarrierMessage;
import com.openjiuwen.core.graph.pregel.ChannelManager;
import com.openjiuwen.core.graph.pregel.TriggerChannel;
import com.openjiuwen.core.graph.pregel.BarrierChannel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Channel and ChannelManager.
 * Mirrors Python's tests/unit_tests/core/graph/test_channel.py
 */
class TestChannelManager {

    @Nested
    @DisplayName("TriggerChannel tests")
    class TriggerChannelTests {

        @Test
        @DisplayName("test trigger channel reset after consume")
        void testTriggerChannelReset() {
            // Setup: Create Manager and Channels
            TriggerChannel chStart = new TriggerChannel("start");
            TriggerChannel chA = new TriggerChannel("a");

            ChannelManager manager = new ChannelManager(Arrays.asList(chStart, chA));

            assertTrue(manager.getReadyNodes().isEmpty());

            // Activate start
            manager.bufferMessage(new TriggerMessage("__start__", "start"));
            manager.flush();

            List<String> ready = manager.getReadyNodes();
            assertTrue(ready.contains("start"));
            assertTrue(chStart.isReady());

            // Consume start
            manager.consume("start");

            List<String> readyAfterConsume = manager.getReadyNodes();
            assertFalse(readyAfterConsume.contains("start"));
            assertFalse(chStart.isReady()); // Key check: TriggerChannel must be empty

            // start produces message to a
            manager.bufferMessage(new TriggerMessage("start", "a"));
            // This flush should not activate start, only a
            manager.flush();

            List<String> readyStep1 = manager.getReadyNodes();
            assertTrue(readyStep1.contains("a"));
            assertFalse(readyStep1.contains("start")); // If start is still here, it's an infinite loop

            // Consume a
            manager.consume("a");
            assertFalse(manager.getReadyNodes().contains("a"));

            // a produces message to a1 (no a1 channel in this example, simulate empty flush)
            manager.flush();

            List<String> readyStep2 = manager.getReadyNodes();
            // Should have no ready nodes
            assertFalse(readyStep2.contains("a"));
            assertFalse(readyStep2.contains("start"));
            assertTrue(readyStep2.isEmpty());
        }
    }

    @Nested
    @DisplayName("BarrierChannel tests")
    class BarrierChannelTests {

        @Test
        @DisplayName("test barrier lifecycle: Waiting -> Partial -> Ready -> Consumed -> Waiting")
        void testBarrierLifecycle() {
            // Setup: Create a barrier waiting for "A" and "B", belonging to node "collect"
            String barrierTargetNode = "collect";
            Set<String> expectedSenders = new HashSet<>(Arrays.asList("A", "B"));

            BarrierChannel barrierCh = new BarrierChannel(barrierTargetNode, expectedSenders);
            String barrierKey = barrierCh.getKey();

            ChannelManager manager = new ChannelManager(Arrays.asList(barrierCh));

            // Initial state check
            assertTrue(manager.getReadyNodes().isEmpty());
            assertFalse(barrierCh.isReady());

            // Partial arrival: Only "A" sends message
            BarrierMessage msgA = new BarrierMessage(barrierKey, "A");
            manager.bufferMessage(msgA);
            manager.flush();

            // Check:
            // 1. Barrier received A
            assertTrue(barrierCh.getReceived().contains("A"));
            // 2. But "B" hasn't arrived, so barrier should not be Ready
            assertFalse(barrierCh.isReady());
            // 3. Manager should not consider "collect" node Ready
            List<String> readyNodes = manager.getReadyNodes();
            assertFalse(readyNodes.contains(barrierTargetNode));

            // Full arrival: "B" sends message
            BarrierMessage msgB = new BarrierMessage(barrierKey, "B");
            manager.bufferMessage(msgB);
            manager.flush();

            // Check:
            // 1. Barrier received B
            assertTrue(barrierCh.getReceived().contains("B"));
            // 2. Now complete, should be Ready
            assertTrue(barrierCh.isReady());
            // 3. Manager should consider "collect" Ready
            List<String> readyNodesFull = manager.getReadyNodes();
            assertTrue(readyNodesFull.contains(barrierTargetNode));

            // Consume: Execute node logic, consume channel
            manager.consume(barrierTargetNode);

            // Check:
            // 1. Manager's Ready list should be empty
            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));
            // 2. Barrier internal state should be cleared
            assertTrue(barrierCh.getReceived().isEmpty());
            assertFalse(barrierCh.isReady());

            // Re-trigger: Send "A" again (simulate next cycle)
            manager.bufferMessage(msgA);
            manager.flush();

            // Check:
            // 1. State should be partially arrived, not Ready
            assertTrue(barrierCh.getReceived().contains("A"));
            assertFalse(barrierCh.isReady());
            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));
        }

        @Test
        @DisplayName("test barrier duplicate signals are idempotent")
        void testBarrierDuplicateSignals() {
            BarrierChannel barrierCh = new BarrierChannel("collect", new HashSet<>(Arrays.asList("A", "B")));
            ChannelManager manager = new ChannelManager(Arrays.asList(barrierCh));
            String key = barrierCh.getKey();

            // A sends twice
            manager.bufferMessage(new BarrierMessage(key, "A"));
            manager.bufferMessage(new BarrierMessage(key, "A"));
            manager.flush();

            assertEquals(1, barrierCh.getReceived().size()); // Set should deduplicate
            assertFalse(barrierCh.isReady());
        }
    }
}
