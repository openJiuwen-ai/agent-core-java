/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.pregel.BarrierChannel;
import com.openjiuwen.core.graph.pregel.BarrierMessage;
import com.openjiuwen.core.graph.pregel.ChannelManager;
import com.openjiuwen.core.graph.pregel.TriggerChannel;
import com.openjiuwen.core.graph.pregel.TriggerMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ChannelManager.
 * <p>
 * Mirrors Python's {@code test_channel.py} from
 * {@code tests/unit_tests/core/graph/test_channel.py}.
 */
@DisplayName("Channel Tests")
class TestChannel {

    @Nested
    @DisplayName("ChannelManager Tests")
    class TestChannelManager {

        @Test
        @Tag("level0")
        @DisplayName("trigger channel reset")
        void testTriggerChannelReset() {
            TriggerChannel chStart = new TriggerChannel("start");
            TriggerChannel chA = new TriggerChannel("a");
            ChannelManager manager = new ChannelManager(List.of(chStart, chA));

            assertEquals(List.of(), manager.getReadyNodes());

            manager.bufferMessage(new TriggerMessage("__start__", "start"));
            manager.flush();

            assertTrue(manager.getReadyNodes().contains("start"));
            assertTrue(chStart.isReady());

            manager.consume("start");

            assertFalse(manager.getReadyNodes().contains("start"));
            assertFalse(chStart.isReady());

            manager.bufferMessage(new TriggerMessage("start", "a"));
            manager.flush();

            List<String> readyStep1 = manager.getReadyNodes();
            assertTrue(readyStep1.contains("a"));
            assertFalse(readyStep1.contains("start"));

            manager.consume("a");
            assertFalse(manager.getReadyNodes().contains("a"));

            manager.flush();

            List<String> readyStep2 = manager.getReadyNodes();
            assertFalse(readyStep2.contains("a"));
            assertFalse(readyStep2.contains("start"));
            assertEquals(0, readyStep2.size());
        }

        @Test
        @Tag("level0")
        @DisplayName("barrier lifecycle")
        void testBarrierLifecycle() {
            String barrierTargetNode = "collect";
            BarrierChannel barrierCh = new BarrierChannel(barrierTargetNode, Set.of("A", "B"));
            String barrierKey = barrierCh.getKey();
            ChannelManager manager = new ChannelManager(List.of(barrierCh));

            assertEquals(List.of(), manager.getReadyNodes());
            assertFalse(barrierCh.isReady());

            BarrierMessage msgA = new BarrierMessage("A", barrierKey);
            manager.bufferMessage(msgA);
            manager.flush();

            assertFalse(barrierCh.isReady());
            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));

            manager.bufferMessage(new BarrierMessage("B", barrierKey));
            manager.flush();

            assertTrue(barrierCh.isReady());
            assertTrue(manager.getReadyNodes().contains(barrierTargetNode));

            manager.consume(barrierTargetNode);

            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));
            assertFalse(barrierCh.isReady());
            assertEquals(List.of(), barrierCh.snapshot());

            manager.bufferMessage(msgA);
            manager.flush();

            assertFalse(barrierCh.isReady());
            assertFalse(manager.getReadyNodes().contains(barrierTargetNode));
        }

        @Test
        @Tag("level0")
        @DisplayName("barrier duplicate signals")
        void testBarrierDuplicateSignals() {
            BarrierChannel barrierCh = new BarrierChannel("collect", Set.of("A", "B"));
            ChannelManager manager = new ChannelManager(List.of(barrierCh));
            String key = barrierCh.getKey();

            manager.bufferMessage(new BarrierMessage("A", key));
            manager.bufferMessage(new BarrierMessage("A", key));
            manager.flush();

            assertEquals(1, ((List<?>) barrierCh.snapshot()).size());
            assertFalse(barrierCh.isReady());
            assertFalse(manager.getReadyNodes().contains("collect"));
        }
    }
}
