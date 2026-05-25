/* *  Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved. */
package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelBuilder;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.pregel.PregelNode;
import com.openjiuwen.core.graph.pregel.BarrierChannel;
import com.openjiuwen.core.graph.pregel.TriggerChannel;
import com.openjiuwen.core.graph.pregel.BarrierRouter;
import com.openjiuwen.core.graph.pregel.ConditionalRouter;
import com.openjiuwen.core.graph.pregel.StaticRouter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Pregel graph execution.
 * Mirrors Python's tests/unit_tests/core/graph/test_pregel.py
 */
class TestPregel {

    @Nested
    @DisplayName("PregelBuilder tests")
    class PregelBuilderTests {

        @Test
        @DisplayName("test builder creates basic graph")
        void testBuilderCreatesBasicGraph() {
            PregelBuilder builder = new PregelBuilder();
            
            builder.addNode("start", () -> "pass");
            builder.addNode("a", () -> "pass");
            builder.addNode("end", () -> "pass");
            
            builder.addEdge("start", List.of("a"));
            builder.addEdge("a", List.of("end"));
            
            assertNotNull(builder.getNodes());
            assertNotNull(builder.getChannels());
            assertTrue(builder.getNodes().containsKey("start"));
            assertTrue(builder.getNodes().containsKey("a"));
            assertTrue(builder.getNodes().containsKey("end"));
        }

        @Test
        @DisplayName("test builder creates barrier synchronization")
        void testBuilderCreatesBarrierSync() {
            PregelBuilder builder = new PregelBuilder();
            
            builder.addNode("start", () -> "pass");
            builder.addNode("a", () -> "pass");
            builder.addNode("b", () -> "pass");
            builder.addNode("collect", () -> "pass");
            builder.addNode("end", () -> "pass");
            
            // Fan-out from start
            builder.addEdge("start", List.of("a", "b"));
            // Barrier synchronization a,b -> collect
            builder.addEdge(Arrays.asList("a", "b"), "collect");
            builder.addEdge("collect", List.of("end"));
            
            assertNotNull(builder.getNodes());
            assertNotNull(builder.getChannels());
        }

        @Test
        @DisplayName("test builder with conditional routing")
        void testBuilderWithConditionalRouting() {
            PregelBuilder builder = new PregelBuilder();
            
            Supplier<String> pickTarget = () -> "D";
            
            builder.addNode("A", () -> 42);
            builder.addNode("D", () -> "received");
            builder.addNode("E", () -> "received");
            
            builder.addBranch("A", pickTarget);
            
            assertNotNull(builder.getNodes());
            assertTrue(builder.getNodes().containsKey("A"));
            assertTrue(builder.getNodes().containsKey("D"));
            assertTrue(builder.getNodes().containsKey("E"));
        }
    }

    @Nested
    @DisplayName("PregelNode tests")
    class PregelNodeTests {

        @Test
        @DisplayName("test node with static router")
        void testNodeWithStaticRouter() {
            Callable<String> fn = () -> "result";
            StaticRouter router = new StaticRouter(Arrays.asList("a", "b"));
            
            PregelNode node = new PregelNode("test", fn, Arrays.asList(router));
            
            assertEquals("test", node.getName());
            assertNotNull(node.getRouters());
            assertEquals(1, node.getRouters().size());
        }

        @Test
        @DisplayName("test node with multiple routers")
        void testNodeWithMultipleRouters() {
            Callable<String> fn = () -> "result";
            StaticRouter staticRouter = new StaticRouter(Arrays.asList("a"));
            Supplier<String> selector = () -> "b";
            ConditionalRouter conditionalRouter = new ConditionalRouter(selector);
            
            PregelNode node = new PregelNode("test", fn, Arrays.asList(staticRouter, conditionalRouter));
            
            assertEquals("test", node.getName());
            assertEquals(2, node.getRouters().size());
        }
    }

    @Nested
    @DisplayName("BarrierChannel tests")
    class BarrierChannelTests {

        @Test
        @DisplayName("test barrier channel creation")
        void testBarrierChannelCreation() {
            BarrierChannel barrier = new BarrierChannel("collect", 
                    new HashSet<>(Arrays.asList("A", "B", "C")));
            
            assertEquals("collect", barrier.getTargetNode());
            assertNotNull(barrier.getExpectedSenders());
            assertEquals(3, barrier.getExpectedSenders().size());
        }

        @Test
        @DisplayName("test barrier is ready when all senders arrived")
        void testBarrierReadyWhenAllArrived() {
            BarrierChannel barrier = new BarrierChannel("collect", 
                    new HashSet<>(Arrays.asList("A", "B")));
            
            barrier.receive("A");
            assertFalse(barrier.isReady());
            
            barrier.receive("B");
            assertTrue(barrier.isReady());
        }

        @Test
        @DisplayName("test barrier reset after consume")
        void testBarrierResetAfterConsume() {
            BarrierChannel barrier = new BarrierChannel("collect", 
                    new HashSet<>(Arrays.asList("A", "B")));
            
            barrier.receive("A");
            barrier.receive("B");
            assertTrue(barrier.isReady());
            
            barrier.consume();
            
            assertFalse(barrier.isReady());
            assertTrue(barrier.getReceived().isEmpty());
        }
    }

    @Nested
    @DisplayName("TriggerChannel tests")
    class TriggerChannelTests {

        @Test
        @DisplayName("test trigger channel creation")
        void testTriggerChannelCreation() {
            TriggerChannel channel = new TriggerChannel("node_a");
            
            assertEquals("node_a", channel.getTargetNode());
            assertFalse(channel.isReady());
        }

        @Test
        @DisplayName("test trigger channel ready after receive")
        void testTriggerChannelReadyAfterReceive() {
            TriggerChannel channel = new TriggerChannel("node_a");
            
            channel.trigger();
            assertTrue(channel.isReady());
        }

        @Test
        @DisplayName("test trigger channel reset after consume")
        void testTriggerChannelResetAfterConsume() {
            TriggerChannel channel = new TriggerChannel("node_a");
            
            channel.trigger();
            assertTrue(channel.isReady());
            
            channel.consume();
            assertFalse(channel.isReady());
        }
    }

    @Nested
    @DisplayName("PregelConfig tests")
    class PregelConfigTests {

        @Test
        @DisplayName("test default config")
        void testDefaultConfig() {
            PregelConfig config = new PregelConfig();
            
            assertNotNull(config);
        }

        @Test
        @DisplayName("test config with custom values")
        void testConfigWithCustomValues() {
            PregelConfig config = PregelConfig.builder()
                    .maxConcurrency(4)
                    .timeoutMs(30000)
                    .build();
            
            assertNotNull(config);
        }
    }
}
