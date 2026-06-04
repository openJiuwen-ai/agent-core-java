/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.graph;

import com.openjiuwen.core.graph.pregel.BarrierChannel;
import com.openjiuwen.core.graph.pregel.BarrierMessage;
import com.openjiuwen.core.graph.pregel.ChannelManager;
import com.openjiuwen.core.graph.pregel.TriggerChannel;
import com.openjiuwen.core.graph.pregel.TriggerMessage;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Channel and ChannelManager.
 * 
 * <p>Mirrors Python's tests/unit_tests/core/graph/test_channel.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/core/graph/test_channel.py
 * 
 * Tests trigger channels and barrier channels for graph-based workflows.
 */
class TestChannel {

    @Test
    @DisplayName("Test trigger channel reset")
    void testTriggerChannelReset() {
        TriggerChannel chStart = new TriggerChannel("start");
        TriggerChannel chA = new TriggerChannel("a");
        ChannelManager manager = new ChannelManager(List.of(chStart, chA));

        assertTrue(manager.getReadyNodes().isEmpty());

        manager.bufferMessage(new TriggerMessage("__start__", "start"));
        manager.flush();

        assertTrue(manager.getReadyNodes().contains("start"));
        assertTrue(chStart.isReady());

        manager.consume("start");

        assertFalse(manager.getReadyNodes().contains("start"));
        assertFalse(chStart.isReady());

        manager.bufferMessage(new TriggerMessage("start", "a"));
        manager.flush();

        assertTrue(manager.getReadyNodes().contains("a"));
        assertFalse(manager.getReadyNodes().contains("start"));

        manager.consume("a");
        assertFalse(manager.getReadyNodes().contains("a"));

        manager.flush();
        assertTrue(manager.getReadyNodes().isEmpty());
    }

    @Test
    @DisplayName("Test barrier lifecycle")
    void testBarrierLifecycle() {
        BarrierChannel barrier = new BarrierChannel("collect", Set.of("A", "B"));
        ChannelManager manager = new ChannelManager(List.of(barrier));

        assertTrue(manager.getReadyNodes().isEmpty());
        assertFalse(barrier.isReady());

        BarrierMessage msgA = new BarrierMessage("A", barrier.getKey());
        manager.bufferMessage(msgA);
        manager.flush();

        assertEquals(List.of("A"), barrier.snapshot());
        assertFalse(barrier.isReady());
        assertFalse(manager.getReadyNodes().contains("collect"));

        BarrierMessage msgB = new BarrierMessage("B", barrier.getKey());
        manager.bufferMessage(msgB);
        manager.flush();

        assertTrue(((List<?>) barrier.snapshot()).containsAll(List.of("A", "B")));
        assertTrue(barrier.isReady());
        assertTrue(manager.getReadyNodes().contains("collect"));

        manager.consume("collect");

        assertFalse(manager.getReadyNodes().contains("collect"));
        assertEquals(List.of(), barrier.snapshot());
        assertFalse(barrier.isReady());

        manager.bufferMessage(msgA);
        manager.flush();

        assertEquals(List.of("A"), barrier.snapshot());
        assertFalse(barrier.isReady());
        assertFalse(manager.getReadyNodes().contains("collect"));
    }

    @Test
    @DisplayName("Test barrier duplicate signals")
    void testBarrierDuplicateSignals() {
        BarrierChannel barrier = new BarrierChannel("collect", Set.of("A", "B"));
        ChannelManager manager = new ChannelManager(List.of(barrier));
        String key = barrier.getKey();

        manager.bufferMessage(new BarrierMessage("A", key));
        manager.bufferMessage(new BarrierMessage("A", key));
        manager.flush();

        assertEquals(List.of("A"), barrier.snapshot());
        assertFalse(barrier.isReady());
    }

    @Test
    @DisplayName("Test trigger channel snapshot restore")
    void testTriggerChannelSnapshotRestore() {
        TriggerChannel trigger = new TriggerChannel("start");
        trigger.accept(new TriggerMessage("__start__", "start"));
        trigger.accept(new TriggerMessage("node", "start"));

        assertTrue(trigger.isReady());
        assertEquals(2, ((List<?>) trigger.snapshot()).size());

        TriggerChannel restored = new TriggerChannel("start");
        restored.restore(trigger.snapshot());

        assertTrue(restored.isReady());
        assertEquals(2, ((List<?>) restored.snapshot()).size());

        restored.consume();
        assertFalse(restored.isReady());
    }

    @Test
    @DisplayName("Test barrier channel snapshot restore marks manager ready")
    void testBarrierChannelSnapshotRestoreMarksManagerReady() {
        BarrierChannel barrier = new BarrierChannel("collect", Set.of("A", "B"));
        barrier.restore(List.of("A", "B"));

        ChannelManager manager = new ChannelManager(List.of(barrier));

        assertTrue(barrier.isReady());
        assertIterableEquals(List.of("collect"), manager.getReadyNodes());
    }

    @Test
    @DisplayName("Test flush fails when target channel missing")
    void testFlushFailsWhenTargetChannelMissing() {
        ChannelManager manager = new ChannelManager(List.of(new TriggerChannel("start")));

        manager.bufferMessage(new TriggerMessage("start", "missing"));

        IllegalStateException error = assertThrows(IllegalStateException.class, manager::flush);
        assertTrue(error.getMessage().contains("Channel not found"));
    }

    @Test
    @DisplayName("Test snapshot ignores end node state")
    void testSnapshotIgnoresEndNodeState() {
        TriggerChannel end = new TriggerChannel("__end__");
        end.accept(new TriggerMessage("worker", "__end__"));

        ChannelManager manager = new ChannelManager(List.of(end));

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = manager.snapshot();
        assertFalse(snapshot.containsKey("__end__"));
    }
}
