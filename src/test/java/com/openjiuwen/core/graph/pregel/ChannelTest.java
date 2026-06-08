package com.openjiuwen.core.graph.pregel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChannelManager}, {@link TriggerChannel}, and {@link BarrierChannel}.
 *
 * <p>Mirrors Python's {@code test_channel.py} in
 * {@code tests/unit_tests/core/graph/pregel}.</p>
 */
class ChannelTest {

    @Nested
    @DisplayName("TriggerChannel reset after consume")
    class TriggerChannelResetTests {

        @Test
        @DisplayName("TriggerChannel correctly resets ready state after consume")
        void testTriggerChannelReset() {
            TriggerChannel start = new TriggerChannel("start");
            TriggerChannel a = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(start, a));

            assertTrue(manager.getReadyNodes().isEmpty());

            manager.bufferMessage(new TriggerMessage("__start__", "start"));
            manager.flush();

            assertTrue(manager.getReadyNodes().contains("start"));
            assertTrue(start.isReady());

            manager.consume("start");
            assertFalse(manager.getReadyNodes().contains("start"));
            assertFalse(start.isReady());

            manager.bufferMessage(new TriggerMessage("start", "a"));
            manager.flush();

            assertTrue(manager.getReadyNodes().contains("a"));
            assertFalse(manager.getReadyNodes().contains("start"));

            manager.consume("a");
            manager.flush();
            assertTrue(manager.getReadyNodes().isEmpty());
        }
    }

    @Nested
    @DisplayName("BarrierChannel lifecycle")
    class BarrierChannelLifecycleTests {

        @Test
        @DisplayName("AND-of-singletons remains backward compatible")
        void testBarrierLifecycle() {
            BarrierChannel barrier = new BarrierChannel("collect", Set.of("A", "B"));
            ChannelManager manager = new ChannelManager(List.of(barrier));
            String key = barrier.getKey();

            manager.bufferMessage(new BarrierMessage("A", key));
            manager.flush();
            assertFalse(barrier.isReady());
            assertFalse(manager.getReadyNodes().contains("collect"));

            manager.bufferMessage(new BarrierMessage("B", key));
            manager.flush();
            assertTrue(barrier.isReady());
            assertTrue(manager.getReadyNodes().contains("collect"));

            manager.consume("collect");
            assertFalse(barrier.isReady());
            assertFalse(manager.getReadyNodes().contains("collect"));
        }

        @Test
        @DisplayName("CNF barrier accepts one sender from each OR-group")
        void testBarrierCnfLifecycle() {
            BarrierChannel barrier = new BarrierChannel("collect", List.of(Set.of("A", "B"), Set.of("C")));
            ChannelManager manager = new ChannelManager(List.of(barrier));
            String key = barrier.getKey();

            assertEquals("barrier:(A|B)&C->collect", key);

            manager.bufferMessage(new BarrierMessage("A", key));
            manager.flush();
            assertFalse(barrier.isReady());

            manager.bufferMessage(new BarrierMessage("C", key));
            manager.flush();
            assertTrue(barrier.isReady());
            assertTrue(manager.getReadyNodes().contains("collect"));
        }

        @Test
        @DisplayName("Duplicate signals from same sender are idempotent")
        void testBarrierDuplicateSignals() {
            BarrierChannel barrier = new BarrierChannel("collect", Set.of("A", "B"));
            ChannelManager manager = new ChannelManager(List.of(barrier));
            String key = barrier.getKey();

            manager.bufferMessage(new BarrierMessage("A", key));
            manager.bufferMessage(new BarrierMessage("A", key));
            manager.flush();

            assertFalse(barrier.isReady());
        }
    }

    @Nested
    @DisplayName("Channel snapshot and restore")
    class SnapshotRestoreTests {

        @Test
        @DisplayName("TriggerChannel snapshot and restore")
        void testTriggerChannelSnapshotRestore() {
            TriggerChannel channel = new TriggerChannel("test");
            channel.accept(new TriggerMessage("sender1", "test"));
            assertTrue(channel.isReady());

            Object snapshot = channel.snapshot();

            TriggerChannel restored = new TriggerChannel("test");
            restored.restore(snapshot);
            assertTrue(restored.isReady());
        }

        @Test
        @DisplayName("BarrierChannel snapshot and restore partial state")
        void testBarrierChannelSnapshotRestore() {
            BarrierChannel channel = new BarrierChannel("target", List.of(Set.of("A", "B"), Set.of("C")));
            channel.accept(new BarrierMessage("A", channel.getKey()));
            assertFalse(channel.isReady());

            Object snapshot = channel.snapshot();

            BarrierChannel restored = new BarrierChannel("target", List.of(Set.of("A", "B"), Set.of("C")));
            restored.restore(snapshot);
            assertFalse(restored.isReady());

            restored.accept(new BarrierMessage("C", restored.getKey()));
            assertTrue(restored.isReady());
        }

        @Test
        @DisplayName("ChannelManager snapshot and restore")
        void testChannelManagerSnapshotRestore() {
            TriggerChannel channel = new TriggerChannel("node1");
            ChannelManager manager = new ChannelManager(List.of(channel));

            manager.bufferMessage(new TriggerMessage("start", "node1"));
            manager.flush();

            Map<String, Object> snapshot = manager.snapshot();
            manager.consume("node1");

            TriggerChannel restoredChannel = new TriggerChannel("node1");
            ChannelManager restoredManager = new ChannelManager(List.of(restoredChannel));
            restoredManager.restore(snapshot);
            assertTrue(restoredManager.getReadyNodes().contains("node1"));
        }
    }

    @Nested
    @DisplayName("ChannelManager buffer operations")
    class BufferTests {

        @Test
        @DisplayName("isEmpty reflects buffer state")
        void testIsEmpty() {
            TriggerChannel channel = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(channel));
            assertTrue(manager.isEmpty());

            manager.bufferMessage(new TriggerMessage("start", "a"));
            assertFalse(manager.isEmpty());

            manager.flush();
            assertTrue(manager.isEmpty());
        }

        @Test
        @DisplayName("getBuffer returns buffered messages before flush")
        void testGetBuffer() {
            TriggerChannel channel = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(channel));

            manager.bufferMessage(new TriggerMessage("x", "a"));
            assertEquals(1, manager.getBuffer().size());

            manager.flush();
            assertEquals(0, manager.getBuffer().size());
        }

        @Test
        @DisplayName("flush to non-existent channel throws")
        void testFlushNonExistentChannel() {
            TriggerChannel channel = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(channel));

            manager.bufferMessage(new TriggerMessage("x", "missing"));
            assertThrows(IllegalArgumentException.class, manager::flush);
        }
    }
}
