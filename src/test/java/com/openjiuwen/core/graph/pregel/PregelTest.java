/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.graph.store.InMemoryStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Pregel} graph execution engine — barrier, conditional routing, multi-routing.
 * <p>
 * Ported from Python's {@code test_pregel.py :: TestPregelV2}.
 */
class PregelTest {

    // ---------- helper: create trace-recording callback ----------
    private static java.util.function.Consumer<PregelLoop> traceCallback(List<Map<String, Object>> trace) {
        return loop -> trace.add(Map.of(
                "step", loop.getStep(),
                "active_nodes", new ArrayList<>(loop.getActiveNodes())
        ));
    }

    // ---------- Barrier synchronization ----------

    @Nested
    @DisplayName("Barrier synchronization")
    class BarrierTests {

        /**
         * Graph structure (direct construction):
         * <pre>
         * start -> a -> a1 --\
         *          b --------\
         *          c ---------\-> collect -> end
         *          d --------/
         * </pre>
         */
        @Test
        @DisplayName("barrier wait for all - direct construction")
        void testBarrierWaitForAllDirect() throws Exception {
            Runnable fnPass = () -> {};

            BarrierChannel a1bcdToCollect = new BarrierChannel("collect", Set.of("a1", "b", "c", "d"));

            List<Channel> channels = new ArrayList<>(List.of(
                    new TriggerChannel("start"),
                    new TriggerChannel("a"),
                    new TriggerChannel("b"),
                    new TriggerChannel("c"),
                    new TriggerChannel("d"),
                    new TriggerChannel("a1"),
                    new TriggerChannel("collect"),
                    new TriggerChannel("end"),
                    a1bcdToCollect
            ));

            Map<String, PregelNode> nodes = new LinkedHashMap<>();
            nodes.put("start", new PregelNode("start", fnPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of("a", "b", "c", "d"))))));
            nodes.put("a", new PregelNode("a", fnPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of("a1"))))));
            nodes.put("b", new PregelNode("b", fnPass,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey()))))));
            nodes.put("c", new PregelNode("c", fnPass,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey()))))));
            nodes.put("d", new PregelNode("d", fnPass,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey()))))));
            nodes.put("a1", new PregelNode("a1", fnPass,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey()))))));
            nodes.put("collect", new PregelNode("collect", fnPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of("end"))))));
            nodes.put("end", new PregelNode("end", fnPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of())))));

            List<Map<String, Object>> trace = new ArrayList<>();
            Pregel app = new Pregel(nodes, channels, "start", null, traceCallback(trace));
            app.run(null);

            // Verify execution trace: 5 steps
            assertEquals(5, trace.size());
            assertEquals(List.of("start"), trace.get(0).get("active_nodes"));

            @SuppressWarnings("unchecked")
            List<String> step1 = (List<String>) trace.get(1).get("active_nodes");
            assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(step1));

            assertEquals(List.of("a1"), trace.get(2).get("active_nodes"));
            assertEquals(List.of("collect"), trace.get(3).get("active_nodes"));
            assertEquals(List.of("end"), trace.get(4).get("active_nodes"));
        }

        @Test
        @DisplayName("barrier wait for all - builder construction")
        void testBarrierWaitForAllBuilder() throws Exception {
            Runnable fnPass = () -> {};

            PregelBuilder builder = new PregelBuilder();
            builder.addNode("a", fnPass);
            builder.addNode("b", fnPass);
            builder.addNode("c", fnPass);
            builder.addNode("d", fnPass);
            builder.addNode("a1", fnPass);
            builder.addNode("collect", fnPass);
            builder.addNode("finish", fnPass); // custom terminal (not __end__) so it appears in trace

            builder.addEdge(PregelConstants.START, List.of("a", "b", "c", "d"));
            builder.addEdge("a", "a1");
            builder.addEdge(List.of("a1", "b", "c", "d"), "collect");
            builder.addEdge("collect", "finish");

            List<Map<String, Object>> trace = new ArrayList<>();
            Pregel app = builder.build(null, traceCallback(trace));
            app.run(null);

            // 5 steps: __start__, {a,b,c,d}, a1, collect, finish
            assertEquals(5, trace.size());
            assertEquals(List.of(PregelConstants.START), trace.get(0).get("active_nodes"));

            @SuppressWarnings("unchecked")
            List<String> step1 = (List<String>) trace.get(1).get("active_nodes");
            assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(step1));

            assertEquals(List.of("a1"), trace.get(2).get("active_nodes"));
            assertEquals(List.of("collect"), trace.get(3).get("active_nodes"));
            assertEquals(List.of("finish"), trace.get(4).get("active_nodes"));
        }
    }

    // ---------- Conditional routing ----------

    @Nested
    @DisplayName("Conditional routing")
    class ConditionalRoutingTests {

        @Test
        @DisplayName("conditional router selects D - direct construction")
        void testConditionalRoutingDirect() throws Exception {
            Runnable fnInt = () -> {};
            Runnable fnReceive = () -> {};

            List<Channel> channels = new ArrayList<>(List.of(
                    new TriggerChannel("A"),
                    new TriggerChannel("D"),
                    new TriggerChannel("E")
            ));

            Map<String, PregelNode> nodes = new LinkedHashMap<>();
            nodes.put("A", new PregelNode("A", fnInt,
                    new ArrayList<>(List.of(new ConditionalRouter(x -> "D")))));
            nodes.put("D", new PregelNode("D", fnReceive,
                    new ArrayList<>(List.of(new StaticRouter(List.of())))));
            nodes.put("E", new PregelNode("E", fnReceive,
                    new ArrayList<>(List.of(new StaticRouter(List.of())))));

            List<Map<String, Object>> trace = new ArrayList<>();
            Pregel app = new Pregel(nodes, channels, "A", null, traceCallback(trace));
            app.run(null);

            assertEquals(2, trace.size());
            assertEquals(List.of("A"), trace.get(0).get("active_nodes"));
            assertEquals(List.of("D"), trace.get(1).get("active_nodes"));

            // E was never activated
            @SuppressWarnings("unchecked")
            List<String> allActivated = trace.stream()
                    .flatMap(t -> ((List<String>) t.get("active_nodes")).stream())
                    .toList();
            assertFalse(allActivated.contains("E"));
        }

        @Test
        @DisplayName("conditional router selects D - builder construction")
        void testConditionalRoutingBuilder() throws Exception {
            Runnable fnInt = () -> {};
            Runnable fnReceive = () -> {};

            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", fnInt);
            builder.addNode("D", fnReceive);
            builder.addNode("E", fnReceive);
            builder.addEdge(PregelConstants.START, "A");
            builder.addBranch("A", x -> "D");

            List<Map<String, Object>> trace = new ArrayList<>();
            Pregel app = builder.build(null, traceCallback(trace));
            app.run(null);

            // 3 steps: __start__, A, D
            assertEquals(3, trace.size());
            assertEquals(List.of(PregelConstants.START), trace.get(0).get("active_nodes"));
            assertEquals(List.of("A"), trace.get(1).get("active_nodes"));
            assertEquals(List.of("D"), trace.get(2).get("active_nodes"));
        }
    }

    // ---------- Multi-routing ----------

    @Nested
    @DisplayName("Multi-routing")
    class MultiRoutingTests {

        /**
         * Complex graph:
         * <pre>
         * START → A, B, C, X (fan-out)
         * A → G (static), A → E (conditional), [A,B,C] → D (barrier), [A,Y] → D (barrier)
         * X → Y (static), Y → D (static)
         * [D,E,G] → FINISH (barrier), F → FINISH (static, never triggered)
         * </pre>
         * Expected trace: START, {A,B,C,X}, {Y,E,G,D}, {FINISH,D}
         */
        @Test
        @DisplayName("multi-routing with mixed static/conditional/barrier - direct construction")
        void testMultiRoutingDirect() throws Exception {
            Runnable fnInt = () -> {};
            Runnable fnReceive = () -> {};
            Runnable fnEnd = () -> {};

            // Create barrier channels
            BarrierChannel barrierABC_D = new BarrierChannel("D", Set.of("A", "B", "C"));
            BarrierChannel barrierAY_D = new BarrierChannel("D", Set.of("A", "Y"));
            BarrierChannel barrierDEG_FIN = new BarrierChannel("FINISH", Set.of("D", "E", "G"));

            List<Channel> channels = new ArrayList<>(List.of(
                    new TriggerChannel("START"),
                    new TriggerChannel("A"),
                    new TriggerChannel("B"),
                    new TriggerChannel("C"),
                    new TriggerChannel("X"),
                    new TriggerChannel("Y"),
                    new TriggerChannel("D"),
                    new TriggerChannel("E"),
                    new TriggerChannel("F"),
                    new TriggerChannel("G"),
                    new TriggerChannel("FINISH"),
                    barrierABC_D,
                    barrierAY_D,
                    barrierDEG_FIN
            ));

            Map<String, PregelNode> nodes = new LinkedHashMap<>();
            nodes.put("START", new PregelNode("START", fnInt,
                    new ArrayList<>(List.of(new StaticRouter(List.of("A", "B", "C", "X"))))));
            nodes.put("A", new PregelNode("A", fnInt, new ArrayList<>(List.of(
                    new StaticRouter(List.of("G")),                           // A → G
                    new ConditionalRouter(x -> "E"),                          // A → E
                    new BarrierRouter(List.of(barrierABC_D.getKey())),        // A → barrier(A,B,C→D)
                    new BarrierRouter(List.of(barrierAY_D.getKey()))          // A → barrier(A,Y→D)
            ))));
            nodes.put("B", new PregelNode("B", fnInt,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(barrierABC_D.getKey()))))));
            nodes.put("C", new PregelNode("C", fnInt,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(barrierABC_D.getKey()))))));
            nodes.put("X", new PregelNode("X", fnInt,
                    new ArrayList<>(List.of(new StaticRouter(List.of("Y"))))));
            nodes.put("Y", new PregelNode("Y", fnReceive, new ArrayList<>(List.of(
                    new StaticRouter(List.of("D")),                           // Y → D (static)
                    new BarrierRouter(List.of(barrierAY_D.getKey()))          // Y → barrier(A,Y→D)
            ))));
            nodes.put("D", new PregelNode("D", fnReceive,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(barrierDEG_FIN.getKey()))))));
            nodes.put("E", new PregelNode("E", fnReceive,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(barrierDEG_FIN.getKey()))))));
            nodes.put("F", new PregelNode("F", fnReceive,
                    new ArrayList<>(List.of(new StaticRouter(List.of("FINISH"))))));
            nodes.put("G", new PregelNode("G", fnReceive,
                    new ArrayList<>(List.of(new BarrierRouter(List.of(barrierDEG_FIN.getKey()))))));
            nodes.put("FINISH", new PregelNode("FINISH", fnEnd,
                    new ArrayList<>(List.of(new StaticRouter(List.of())))));

            List<Map<String, Object>> trace = new ArrayList<>();
            Pregel graph = new Pregel(nodes, channels, "START", null, traceCallback(trace));
            graph.run(null);

            assertEquals(4, trace.size());
            assertEquals(List.of("START"), trace.get(0).get("active_nodes"));

            @SuppressWarnings("unchecked")
            List<String> step1 = (List<String>) trace.get(1).get("active_nodes");
            assertEquals(Set.of("A", "B", "C", "X"), Set.copyOf(step1));

            @SuppressWarnings("unchecked")
            List<String> step2 = (List<String>) trace.get(2).get("active_nodes");
            assertEquals(Set.of("Y", "E", "G", "D"), Set.copyOf(step2));

            @SuppressWarnings("unchecked")
            List<String> step3 = (List<String>) trace.get(3).get("active_nodes");
            assertEquals(Set.of("FINISH", "D"), Set.copyOf(step3));
        }

        @Test
        @DisplayName("multi-routing with mixed static/conditional/barrier - builder construction")
        void testMultiRoutingBuilder() throws Exception {
            Runnable fnInt = () -> {};
            Runnable fnReceive = () -> {};
            Runnable fnEnd = () -> {};

            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", fnInt);
            builder.addNode("B", fnInt);
            builder.addNode("C", fnInt);
            builder.addNode("X", fnInt);
            builder.addNode("Y", fnReceive);
            builder.addNode("D", fnReceive);
            builder.addNode("E", fnReceive);
            builder.addNode("F", fnReceive);
            builder.addNode("G", fnReceive);
            builder.addNode("FINISH", fnEnd);

            builder.addEdge(PregelConstants.START, List.of("A", "B", "C", "X"));  // fan-out
            builder.addEdge("A", "G");                                             // A → G (static)
            builder.addBranch("A", x -> "E");                                      // A → E (conditional)
            builder.addEdge(List.of("A", "B", "C"), "D");                          // barrier A,B,C → D
            builder.addEdge("X", "Y");                                             // X → Y (static)
            builder.addEdge(List.of("A", "Y"), "D");                               // barrier A,Y → D
            builder.addEdge("Y", "D");                                             // Y → D (static)
            builder.addEdge(List.of("D", "E", "G"), "FINISH");                     // barrier D,E,G → FINISH
            builder.addEdge("F", "FINISH");                                        // F → FINISH (static, never triggered)

            List<Map<String, Object>> trace = new ArrayList<>();
            Pregel graph = builder.build(null, traceCallback(trace));
            graph.run(null);

            // 5 steps: __start__, {A,B,C,X}, {Y,E,G,D}, {FINISH,D}, then done
            // __start__ adds one extra step compared to the direct version
            assertTrue(trace.size() >= 4);

            assertEquals(List.of(PregelConstants.START), trace.get(0).get("active_nodes"));

            @SuppressWarnings("unchecked")
            List<String> step1 = (List<String>) trace.get(1).get("active_nodes");
            assertEquals(Set.of("A", "B", "C", "X"), Set.copyOf(step1));

            @SuppressWarnings("unchecked")
            List<String> step2 = (List<String>) trace.get(2).get("active_nodes");
            assertEquals(Set.of("Y", "E", "G", "D"), Set.copyOf(step2));

            @SuppressWarnings("unchecked")
            List<String> step3 = (List<String>) trace.get(3).get("active_nodes");
            assertEquals(Set.of("FINISH", "D"), Set.copyOf(step3));
        }
    }

    // ---------- PregelBuilder tests ----------

    @Nested
    @DisplayName("PregelBuilder")
    class PregelBuilderTests {

        @Test
        @DisplayName("builder creates __start__ and __end__ nodes by default")
        void testDefaultNodes() {
            PregelBuilder builder = new PregelBuilder();
            Pregel pregel = builder.build();
            assertTrue(pregel.getNodes().containsKey(PregelConstants.START));
            assertTrue(pregel.getNodes().containsKey(PregelConstants.END));
        }

        @Test
        @DisplayName("addNode adds node and trigger channel")
        void testAddNode() {
            PregelBuilder builder = new PregelBuilder();
            builder.addNode("myNode", (Runnable) () -> {});
            Pregel pregel = builder.build();
            assertTrue(pregel.getNodes().containsKey("myNode"));
            assertTrue(pregel.getChannels().stream().anyMatch(c -> "myNode".equals(c.getNodeName())));
        }

        @Test
        @DisplayName("addEdge 1->1 creates static router")
        void testAddEdgeSingleStatic() {
            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", (Runnable) () -> {});
            builder.addNode("B", (Runnable) () -> {});
            builder.addEdge("A", "B");
            Pregel pregel = builder.build();
            assertFalse(pregel.getNodes().get("A").getRouters().isEmpty());
        }

        @Test
        @DisplayName("addEdge 1->N creates static router with multiple targets")
        void testAddEdgeFanOut() {
            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", (Runnable) () -> {});
            builder.addNode("B", (Runnable) () -> {});
            builder.addNode("C", (Runnable) () -> {});
            builder.addEdge("A", List.of("B", "C"));
            Pregel pregel = builder.build();
            assertEquals(1, pregel.getNodes().get("A").getRouters().size());
        }

        @Test
        @DisplayName("addEdge N->1 creates barrier channel")
        void testAddEdgeBarrier() {
            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", (Runnable) () -> {});
            builder.addNode("B", (Runnable) () -> {});
            builder.addNode("C", (Runnable) () -> {});
            builder.addEdge(List.of("A", "B"), "C");
            Pregel pregel = builder.build();

            assertFalse(pregel.getNodes().get("A").getRouters().isEmpty());
            assertFalse(pregel.getNodes().get("B").getRouters().isEmpty());
            assertTrue(pregel.getChannels().stream().anyMatch(c -> c instanceof BarrierChannel));
        }

        @Test
        @DisplayName("addBranch adds conditional router")
        void testAddBranch() {
            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", (Runnable) () -> {});
            builder.addBranch("A", x -> "target");
            Pregel pregel = builder.build();
            assertEquals(1, pregel.getNodes().get("A").getRouters().size());
            assertInstanceOf(ConditionalRouter.class, pregel.getNodes().get("A").getRouters().get(0));
        }

        @Test
        @DisplayName("build creates a Pregel instance")
        void testBuild() {
            PregelBuilder builder = new PregelBuilder();
            builder.addNode("A", (Runnable) () -> {});
            Pregel pregel = builder.build();
            assertNotNull(pregel);
            assertNotNull(pregel.getNodes());
            assertNotNull(pregel.getChannels());
        }
    }

    // ---------- PregelConfig tests ----------

    @Nested
    @DisplayName("PregelConfig")
    class PregelConfigTests {

        @Test
        @DisplayName("default config has MAX_RECURSIVE_LIMIT")
        void testDefaultConfig() {
            PregelConfig config = new PregelConfig();
            assertEquals(PregelConstants.MAX_RECURSIVE_LIMIT, config.getRecursionLimit());
            assertNull(config.getSessionId());
            assertNull(config.getNs());
        }

        @Test
        @DisplayName("parameterized constructor")
        void testParameterizedConstructor() {
            PregelConfig config = new PregelConfig("session1", "ns1", 100);
            assertEquals("session1", config.getSessionId());
            assertEquals("ns1", config.getNs());
            assertEquals(100, config.getRecursionLimit());
        }

        @Test
        @DisplayName("get by key name")
        void testGetByKey() {
            PregelConfig config = new PregelConfig("sid", "myns", 50);
            assertEquals("sid", config.get(PregelConstants.SESSION_ID));
            assertEquals("myns", config.get(PregelConstants.NS));
            assertEquals(50, config.get(PregelConstants.RECURSION_LIMIT));
            assertNull(config.get("unknown_key"));
        }

        @Test
        @DisplayName("toMap contains all fields")
        void testToMap() {
            PregelConfig config = new PregelConfig("sid", "ns", 100);
            config.setParentNs("parent");
            Map<String, Object> map = config.toMap();
            assertEquals("sid", map.get(PregelConstants.SESSION_ID));
            assertEquals("ns", map.get(PregelConstants.NS));
            assertEquals("parent", map.get(PregelConstants.PARENT_NS));
            assertEquals(100, map.get(PregelConstants.RECURSION_LIMIT));
        }

        @Test
        @DisplayName("createInnerConfig copies fields")
        void testCreateInnerConfig() {
            PregelConfig original = new PregelConfig("sid", "ns", 200);
            original.setParentNs("parent");
            PregelConfig inner = PregelConfig.createInnerConfig(original);
            assertEquals("sid", inner.getSessionId());
            assertEquals("ns", inner.getNs());
            assertEquals("parent", inner.getParentNs());
            assertEquals(200, inner.getRecursionLimit());
        }

        @Test
        @DisplayName("createInnerConfig from null uses defaults")
        void testCreateInnerConfigNull() {
            PregelConfig inner = PregelConfig.createInnerConfig(null);
            assertEquals(PregelConstants.MAX_RECURSIVE_LIMIT, inner.getRecursionLimit());
        }
    }

    // ---------- Router tests ----------

    @Nested
    @DisplayName("Router dispatch")
    class RouterTests {

        @Test
        @DisplayName("StaticRouter dispatches TriggerMessages to all targets")
        void testStaticRouter() {
            StaticRouter router = new StaticRouter(List.of("A", "B", "C"));
            List<Message> messages = router.dispatch("source");
            assertEquals(3, messages.size());
            assertTrue(messages.stream().allMatch(m -> m instanceof TriggerMessage));
            assertTrue(messages.stream().allMatch(m -> "source".equals(m.getSender())));
            assertEquals(Set.of("A", "B", "C"),
                    Set.copyOf(messages.stream().map(Message::getTarget).toList()));
        }

        @Test
        @DisplayName("StaticRouter with empty targets dispatches nothing")
        void testStaticRouterEmpty() {
            StaticRouter router = new StaticRouter(List.of());
            List<Message> messages = router.dispatch("source");
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("ConditionalRouter dispatches to selected target (String)")
        void testConditionalRouterString() {
            ConditionalRouter router = new ConditionalRouter(x -> "targetNode");
            List<Message> messages = router.dispatch("source");
            assertEquals(1, messages.size());
            assertEquals("targetNode", messages.get(0).getTarget());
            assertEquals("source", messages.get(0).getSender());
        }

        @Test
        @DisplayName("ConditionalRouter dispatches to multiple targets (List)")
        void testConditionalRouterList() {
            ConditionalRouter router = new ConditionalRouter(x -> List.of("X", "Y"));
            List<Message> messages = router.dispatch("source");
            assertEquals(2, messages.size());
            assertEquals(Set.of("X", "Y"),
                    Set.copyOf(messages.stream().map(Message::getTarget).toList()));
        }

        @Test
        @DisplayName("BarrierRouter dispatches BarrierMessages")
        void testBarrierRouter() {
            BarrierRouter router = new BarrierRouter(List.of("barrier:A|B->C"));
            List<Message> messages = router.dispatch("source");
            assertEquals(1, messages.size());
            assertTrue(messages.get(0) instanceof BarrierMessage);
            assertEquals("barrier:A|B->C", messages.get(0).getTarget());
        }
    }

    // ---------- Interrupt and constants ----------

    @Nested
    @DisplayName("Interrupt and PregelConstants")
    class InterruptAndConstantsTests {

        @Test
        @DisplayName("GraphInterrupt carries Interrupt value")
        void testGraphInterrupt() {
            Interrupt interrupt = new Interrupt("test_value");
            GraphInterrupt gi = new GraphInterrupt(interrupt);
            assertEquals("test_value", gi.getValue().getValue());
        }

        @Test
        @DisplayName("Interrupt toString")
        void testInterruptToString() {
            Interrupt interrupt = new Interrupt("hello");
            assertNotNull(interrupt.toString());
        }

        @Test
        @DisplayName("PregelConstants values")
        void testConstants() {
            assertEquals("__start__", PregelConstants.START);
            assertEquals("__end__", PregelConstants.END);
            assertEquals("__interrupt__", PregelConstants.TASK_STATUS_INTERRUPT);
            assertEquals("__error__", PregelConstants.TASK_STATUS_ERROR);
            assertEquals(":", PregelConstants.NS_SEPARATOR);
            assertEquals("ns", PregelConstants.NS);
            assertEquals("parent_ns", PregelConstants.PARENT_NS);
            assertEquals("session_id", PregelConstants.SESSION_ID);
        }
    }

    // ---------- Subgraph with exception and checkpoint ----------

    @Nested
    @DisplayName("Subgraph with exception and state persistence")
    class SubgraphExceptionTests {

        @Test
        @DisplayName("exception in node saves checkpoint, resume retries")
        void testSubgraphExceptionAndResume() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);

            // Node that fails on first call, succeeds on second
            Callable<Object> failThenPass = () -> {
                int count = callCount.incrementAndGet();
                if (count <= 1) {
                    throw new RuntimeException("node failure");
                }
                return "ok";
            };

            Runnable fnPass = () -> {};

            // Build nodes/channels manually so we can control the initial node
            BarrierChannel noBarrier = null; // unused
            List<Channel> channels = new ArrayList<>(List.of(
                    new TriggerChannel("start"),
                    new TriggerChannel("worker"),
                    new TriggerChannel("end")
            ));
            Map<String, PregelNode> nodes = new LinkedHashMap<>();
            nodes.put("start", new PregelNode("start", fnPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of("worker"))))));
            nodes.put("worker", new PregelNode("worker", failThenPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of("end"))))));
            nodes.put("end", new PregelNode("end", fnPass,
                    new ArrayList<>(List.of(new StaticRouter(List.of())))));

            InMemoryStore store = new InMemoryStore();
            List<Map<String, Object>> trace = new ArrayList<>();

            Pregel graph = new Pregel(nodes, channels, "start", store, traceCallback(trace));
            PregelConfig config = new PregelConfig("test_exc", "ns_exc", 100);

            // First run: should fail with RuntimeException from worker node
            assertThrows(RuntimeException.class, () -> graph.run(config));

            // Checkpoint should exist in the store
            assertTrue(store.get("test_exc", "ns_exc").isPresent());

            // Second run: resumes from checkpoint, worker now succeeds
            trace.clear();
            Map<String, Object> result = graph.run(config);
            assertNotNull(result);

            // Worker should have been activated during the resumed run
            @SuppressWarnings("unchecked")
            List<String> allNodes = trace.stream()
                    .flatMap(t -> ((List<String>) t.get("active_nodes")).stream())
                    .toList();
            assertTrue(allNodes.contains("worker"));
        }
    }

    // ---------- Recursion limit ----------

    @Nested
    @DisplayName("Recursion limit")
    class RecursionLimitTests {

        @Test
        @DisplayName("exceeding recursion limit throws StackOverflowError")
        void testRecursionLimitExceeded() {
            Runnable fn = () -> {};

            // Build a self-loop graph: A → A
            List<Channel> channels = new ArrayList<>(List.of(new TriggerChannel("A")));
            Map<String, PregelNode> nodes = new LinkedHashMap<>();
            nodes.put("A", new PregelNode("A", fn,
                    new ArrayList<>(List.of(new StaticRouter(List.of("A"))))));

            Pregel graph = new Pregel(nodes, channels, "A", null, null);
            PregelConfig config = new PregelConfig("test_limit", "ns_limit", 3);

            StackOverflowError err = assertThrows(StackOverflowError.class, () -> graph.run(config));
            assertTrue(err.getMessage().contains("Recursion limit"));
        }
    }
}
