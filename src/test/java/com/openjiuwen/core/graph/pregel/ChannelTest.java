/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChannelManager}, {@link TriggerChannel}, and {@link BarrierChannel}.
 * <p>
 * Ported from Python's {@code test_channel.py :: TestChannelManager}.
 */
class ChannelTest {

    // ---------- TriggerChannel reset ----------

    @Nested
    @DisplayName("TriggerChannel reset after consume")
    class TriggerChannelResetTests {

        @Test
        @DisplayName("TriggerChannel correctly resets Ready state after consume")
        void testTriggerChannelReset() {
            // Setup: Create Manager and Channels
            TriggerChannel chStart = new TriggerChannel("start");
            TriggerChannel chA = new TriggerChannel("a");

            ChannelManager manager = new ChannelManager(List.of(chStart, chA));

            // Initially no ready nodes
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
            assertFalse(readyStep1.contains("start")); // If start is still here, infinite loop

            // Consume a
            manager.consume("a");
            assertFalse(manager.getReadyNodes().contains("a"));

            // a produces no new messages; simulate empty flush
            manager.flush();

            List<String> readyStep2 = manager.getReadyNodes();
            assertFalse(readyStep2.contains("a"));
            assertFalse(readyStep2.contains("start"));
            assertEquals(0, readyStep2.size());
        }
    }

    // ---------- BarrierChannel lifecycle ----------

    @Nested
    @DisplayName("BarrierChannel lifecycle")
    class BarrierChannelLifecycleTests {

        @Test
        @DisplayName("Waiting -> Partial -> All arrived (Ready) -> Consumed (Reset) -> Waiting again")
        void testBarrierLifecycle() {
            String barrierTargetNode = "collect";
            Set<String> expectedSenders = Set.of("A", "B");

            BarrierChannel barrierCh = new BarrierChannel(barrierTargetNode, expectedSenders);
            String barrierKey = barrierCh.getKey();

            ChannelManager manager = new ChannelManager(List.of(barrierCh));

            // Initial state check
            assertTrue(manager.getReadyNodes().isEmpty());
            assertFalse(barrierCh.isReady());

            // Partial arrival: Only "A" sends message
            BarrierMessage msgA = new BarrierMessage("A", barrierKey);
            manager.bufferMessage(msgA);
            manager.flush();

            // Barrier should not be Ready (B hasn't arrived)
            assertFalse(barrierCh.isReady());
            List<String> readyNodes = manager.getReadyNodes();
            assertFalse(readyNodes.contains(barrierTargetNode));

            // Full arrival: "B" sends message
            BarrierMessage msgB = new BarrierMessage("B", barrierKey);
            manager.bufferMessage(msgB);
            manager.flush();

            // Now complete, should be Ready
            assertTrue(barrierCh.isReady());
            List<String> readyNodesFull = manager.getReadyNodes();
            assertTrue(readyNodesFull.contains(barrierTargetNode));

            // Consume: Execute node logic, consume channel
            manager.consume(barrierTargetNode);

            // Manager's Ready list should be empty; barrier internal state cleared
            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));
            assertFalse(barrierCh.isReady());

            // Re-trigger: Send "A" again (simulate next cycle)
            manager.bufferMessage(msgA);
            manager.flush();

            // State should be partially arrived, not Ready
            assertFalse(barrierCh.isReady());
            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));
        }

        @Test
        @DisplayName("Duplicate signals from same sender are idempotent")
        void testBarrierDuplicateSignals() {
            BarrierChannel barrierCh = new BarrierChannel("collect", Set.of("A", "B"));
            ChannelManager manager = new ChannelManager(List.of(barrierCh));
            String key = barrierCh.getKey();

            // A sends twice
            manager.bufferMessage(new BarrierMessage("A", key));
            manager.bufferMessage(new BarrierMessage("A", key));
            manager.flush();

            // Set should deduplicate; barrier not ready
            assertFalse(barrierCh.isReady());
        }
    }

    // ---------- Additional channel tests ----------

    @Nested
    @DisplayName("Channel snapshot and restore")
    class SnapshotRestoreTests {

        @Test
        @DisplayName("TriggerChannel snapshot and restore")
        void testTriggerChannelSnapshotRestore() {
            TriggerChannel ch = new TriggerChannel("test");
            // Accept a message
            ch.accept(new TriggerMessage("sender1", "test"));
            assertTrue(ch.isReady());

            // Snapshot
            Object snap = ch.snapshot();
            assertNotNull(snap);

            // Create new channel and restore
            TriggerChannel ch2 = new TriggerChannel("test");
            assertFalse(ch2.isReady());
            ch2.restore(snap);
            assertTrue(ch2.isReady());
        }

        @Test
        @DisplayName("BarrierChannel snapshot and restore partial state")
        void testBarrierChannelSnapshotRestore() {
            BarrierChannel ch = new BarrierChannel("target", Set.of("A", "B", "C"));
            ch.accept(new BarrierMessage("A", ch.getKey()));
            ch.accept(new BarrierMessage("B", ch.getKey()));
            assertFalse(ch.isReady()); // C hasn't arrived

            // Snapshot
            Object snap = ch.snapshot();

            // New channel, restore
            BarrierChannel ch2 = new BarrierChannel("target", Set.of("A", "B", "C"));
            ch2.restore(snap);
            assertFalse(ch2.isReady()); // Still partial

            // Complete
            ch2.accept(new BarrierMessage("C", ch2.getKey()));
            assertTrue(ch2.isReady());
        }

        @Test
        @DisplayName("ChannelManager snapshot and restore")
        void testChannelManagerSnapshotRestore() {
            TriggerChannel ch = new TriggerChannel("node1");
            ChannelManager manager = new ChannelManager(List.of(ch));

            manager.bufferMessage(new TriggerMessage("start", "node1"));
            manager.flush();
            assertTrue(manager.getReadyNodes().contains("node1"));

            // Take snapshot
            var snap = manager.snapshot();
            assertNotNull(snap);

            // Consume original
            manager.consume("node1");
            assertFalse(manager.getReadyNodes().contains("node1"));

            // New manager, restore
            TriggerChannel ch2 = new TriggerChannel("node1");
            ChannelManager manager2 = new ChannelManager(List.of(ch2));
            manager2.restore(snap);
            assertTrue(manager2.getReadyNodes().contains("node1"));
        }
    }

    // ---------- ChannelManager buffer and flush ----------

    @Nested
    @DisplayName("ChannelManager buffer operations")
    class BufferTests {

        @Test
        @DisplayName("isEmpty reflects buffer state")
        void testIsEmpty() {
            TriggerChannel ch = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(ch));
            assertTrue(manager.isEmpty());

            manager.bufferMessage(new TriggerMessage("start", "a"));
            assertFalse(manager.isEmpty());

            manager.flush();
            assertTrue(manager.isEmpty()); // Buffer cleared after flush
        }

        @Test
        @DisplayName("getBuffer returns buffered messages before flush")
        void testGetBuffer() {
            TriggerChannel ch = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(ch));
            manager.bufferMessage(new TriggerMessage("x", "a"));
            assertEquals(1, manager.getBuffer().size());

            manager.flush();
            assertEquals(0, manager.getBuffer().size());
        }

        @Test
        @DisplayName("flush to non-existent channel throws")
        void testFlushNonExistentChannel() {
            TriggerChannel ch = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(ch));
            manager.bufferMessage(new TriggerMessage("x", "no_such_channel"));
            assertThrows(IllegalStateException.class, manager::flush);
        }
    }

    // ---------- Message tests ----------

    @Nested
    @DisplayName("Message types")
    class MessageTests {

        @Test
        @DisplayName("TriggerMessage carries sender and target")
        void testTriggerMessage() {
            TriggerMessage msg = new TriggerMessage("sender1", "target1");
            assertEquals("sender1", msg.getSender());
            assertEquals("target1", msg.getTarget());
            assertNull(msg.getPayload());
        }

        @Test
        @DisplayName("TriggerMessage with payload")
        void testTriggerMessageWithPayload() {
            TriggerMessage msg = new TriggerMessage("s", "t", "data");
            assertEquals("data", msg.getPayload());
        }

        @Test
        @DisplayName("BarrierMessage carries sender and target")
        void testBarrierMessage() {
            BarrierMessage msg = new BarrierMessage("sender1", "target1");
            assertEquals("sender1", msg.getSender());
            assertEquals("target1", msg.getTarget());
        }

        @Test
        @DisplayName("Message toString")
        void testMessageToString() {
            Message msg = new Message("s", "t", "p");
            assertNotNull(msg.toString());
        }
    }
}
