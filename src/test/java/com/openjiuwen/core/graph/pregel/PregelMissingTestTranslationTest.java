/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.InMemoryStore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Supplemental Pregel parity tests.
 *
 * <p>Mirrors Python's {@code TestPregelV2} cases in
 * {@code tests/unit_tests/core/graph/test_pregel.py}.</p>
 */
class PregelMissingTestTranslationTest {

    @Test
    void barrierWaitForAllDirectConstruction() throws Exception {
        Trace trace = new Trace();
        GraphParts graphParts = basicBarrierDirect();

        new Pregel(graphParts.nodes(), graphParts.channels(), "start", null, trace::record)
                .run();

        assertEquals(5, trace.steps().size());
        assertStep(trace, 0, "start");
        assertStep(trace, 1, "a", "b", "c", "d");
        assertStep(trace, 2, "a1");
        assertStep(trace, 3, "collect");
        assertStep(trace, 4, "end");
    }

    @Test
    void barrierWaitForAllBuilderConstruction() throws Exception {
        Trace trace = new Trace();
        GraphParts graphParts = basicBarrierBuilder();

        new Pregel(graphParts.nodes(), graphParts.channels(), "start", null, trace::record)
                .run();

        assertEquals(5, trace.steps().size());
        assertStep(trace, 0, "start");
        assertStep(trace, 1, "a", "b", "c", "d");
        assertStep(trace, 2, "a1");
        assertStep(trace, 3, "collect");
        assertStep(trace, 4, "end");
    }

    @Test
    void conditionalRoutingDirectConstruction() throws Exception {
        Trace trace = new Trace();
        GraphParts graphParts = conditionalRoutingDirect();

        new Pregel(graphParts.nodes(), graphParts.channels(), "A", null, trace::record)
                .run();

        assertEquals(2, trace.steps().size());
        assertStep(trace, 0, "A");
        assertStep(trace, 1, "D");
        assertFalse(trace.flat().contains("E"));
    }

    @Test
    void conditionalRoutingBuilderConstruction() throws Exception {
        Trace trace = new Trace();
        GraphParts graphParts = conditionalRoutingBuilder();

        new Pregel(graphParts.nodes(), graphParts.channels(), "A", null, trace::record)
                .run();

        assertEquals(2, trace.steps().size());
        assertStep(trace, 0, "A");
        assertStep(trace, 1, "D");
        assertFalse(trace.flat().contains("E"));
    }

    @Test
    void multiRoutingDirectConstruction() throws Exception {
        Trace trace = new Trace();
        GraphParts graphParts = multiRoutingDirect();

        new Pregel(graphParts.nodes(), graphParts.channels(), "START", null, trace::record)
                .run();

        assertEquals(4, trace.steps().size());
        assertStep(trace, 0, "START");
        assertStep(trace, 1, "A", "B", "C", "X");
        assertStep(trace, 2, "Y", "E", "G", "D");
        assertStep(trace, 3, "END", "D");
    }

    @Test
    void multiRoutingBuilderConstruction() throws Exception {
        Trace trace = new Trace();
        GraphParts graphParts = multiRoutingBuilder();

        new Pregel(graphParts.nodes(), graphParts.channels(), "START", null, trace::record)
                .run();

        assertEquals(4, trace.steps().size());
        assertStep(trace, 0, "START");
        assertStep(trace, 1, "A", "B", "C", "X");
        assertStep(trace, 2, "Y", "E", "G", "D");
        assertStep(trace, 3, "END", "D");
    }

    @Test
    void subgraphExceptionPersistsAndStatelessRunsRestart() {
        InMemoryStore store = new InMemoryStore();
        Trace trace = new Trace();
        Pregel inner = exceptionSubgraph(store, trace::record);
        Pregel outer = new Pregel(
                subgraphOuterNodes(runSubgraph(inner)),
                subgraphOuterChannels(),
                "start",
                store,
                trace::record
        );
        PregelConfig config = new PregelConfig("test_parallel_fail_java", "start-a-end", 100);

        RuntimeException first = assertThrows(RuntimeException.class, () -> outer.run(config));
        assertEquals("a1 exception", rootCause(first).getMessage());
        GraphStoreState outerState = state(store, config.getSessionId(), config.getNs());
        assertTrue(outerState.getPendingNode().containsKey("a"));
        GraphStoreState innerState = state(store, config.getSessionId(), config.getNs() + ":a:1");
        assertTrue(innerState.getPendingNode().containsKey("a1"));

        trace.clear();
        RuntimeException second = assertThrows(RuntimeException.class, () -> outer.run(config));
        assertEquals("a1 exception", rootCause(second).getMessage());
        assertTrue(trace.steps().isEmpty());

        trace.clear();
        RuntimeException stateless = assertThrows(RuntimeException.class, () -> outer.run(new PregelConfig()));
        assertEquals("a1 exception", rootCause(stateless).getMessage());
        assertStep(trace, 0, "start");
    }

    @Test
    void recursionLimitRecoveryResumesSavedTrigger() throws Exception {
        InMemoryStore store = new InMemoryStore();
        Trace trace = new Trace();
        Pregel graph = linearGraph(store, trace::record);
        PregelConfig config = new PregelConfig("test_recursion_limit_nested_java", "outer-linear-test", 1);

        IllegalStateException first = assertThrows(IllegalStateException.class, () -> graph.run(config));
        assertTrue(first.getMessage().contains("Recursion limit of 1 reached at step 2"));
        assertStep(trace, 0, "start");
        assertStep(trace, 1, "a");
        assertEquals(2, state(store, config.getSessionId(), config.getNs()).getStep());

        trace.clear();
        IllegalStateException second = assertThrows(IllegalStateException.class, () -> graph.run(config));
        assertTrue(second.getMessage().contains("Recursion limit of 3 reached at step 4"));
        assertStep(trace, 0, "b");
        assertStep(trace, 1, "c");
        assertEquals(4, state(store, config.getSessionId(), config.getNs()).getStep());

        trace.clear();
        graph.run(config);
        assertStep(trace, 0, "end");
    }

    @Test
    void subgraphInterruptPersistsPendingNodesAndResumes() throws Exception {
        InMemoryStore store = new InMemoryStore();
        Trace trace = new Trace();
        Pregel inner = interruptingParallelSubgraph(store, trace::record);
        Pregel graph = new Pregel(
                parallelOuterNodes(runSubgraph(inner), interruptTwice("b_Interrupt", 80)),
                parallelOuterChannels(),
                "start",
                store,
                trace::record
        );
        PregelConfig config = new PregelConfig("test_parallel_interrupt_java", "start-a-end", 100);

        Map<String, Object> first = graph.run(config);
        assertEquals("a1_Interrupt", first.get(PregelConstants.TASK_STATUS_INTERRUPT));
        GraphStoreState outerState = state(store, config.getSessionId(), config.getNs());
        assertTrue(outerState.getPendingNode().containsKey("a"));
        assertTrue(outerState.getPendingNode().containsKey("b"));
        GraphStoreState innerState = state(store, config.getSessionId(), config.getNs() + ":a:1");
        assertTrue(innerState.getPendingNode().containsKey("a1"));
        assertFalse(innerState.getPendingNode().containsKey("a2"));
        assertFalse(innerState.getPendingNode().containsKey("a3"));

        trace.clear();
        Map<String, Object> second = graph.run(config);
        assertEquals("a1_Interrupt", second.get(PregelConstants.TASK_STATUS_INTERRUPT));
        assertTrue(trace.steps().isEmpty());
        outerState = state(store, config.getSessionId(), config.getNs());
        assertTrue(outerState.getPendingNode().containsKey("a"));
        assertTrue(outerState.getPendingNode().containsKey("b"));

        trace.clear();
        Map<String, Object> third = graph.run(config);
        assertFalse(third.containsKey(PregelConstants.TASK_STATUS_INTERRUPT));
        List<String> flat = trace.flat();
        assertTrue(flat.contains("a"));
        assertTrue(flat.contains("b"));
        assertTrue(flat.contains("end1"));
        assertTrue(flat.contains("end"));
    }

    @Test
    void nestedLoopWithInnerParallelInterruptsAndCleansNestedState() throws Exception {
        InMemoryStore store = new InMemoryStore();
        Trace trace = new Trace();
        Pregel body = bodySubgraph(store, trace::record);
        AtomicInteger conditionCount = new AtomicInteger();
        Pregel loop = loopSubgraph(body, conditionCount, store, trace::record);
        Pregel graph = outerLoopGraph(loop, store, trace::record);
        PregelConfig config = new PregelConfig("test_loop_interrupt_java", "start-loop-end", 100);

        Map<String, Object> first = graph.run(config);
        assertTrue(first.containsKey(PregelConstants.TASK_STATUS_INTERRUPT));
        GraphStoreState outerState = state(store, config.getSessionId(), config.getNs());
        assertTrue(outerState.getPendingNode().containsKey("loop"));
        GraphStoreState loopState = state(store, config.getSessionId(), config.getNs() + ":loop:1");
        assertTrue(loopState.getPendingNode().containsKey("body"));
        GraphStoreState bodyState = state(store, config.getSessionId(), config.getNs() + ":loop:1:body:1");
        assertTrue(bodyState.getPendingNode().containsKey("a"));
        assertTrue(bodyState.getPendingNode().containsKey("b"));
        assertEquals("c", bodyState.getPendingBuffer().get(0).getSender());

        trace.clear();
        Map<String, Object> second = graph.run(config);
        assertTrue(second.containsKey(PregelConstants.TASK_STATUS_INTERRUPT));
        assertFalse(trace.flat().contains("c"));

        trace.clear();
        Map<String, Object> third = graph.run(config);
        assertTrue(third.containsKey(PregelConstants.TASK_STATUS_INTERRUPT));
        List<String> thirdFlat = trace.flat();
        assertTrue(thirdFlat.contains("a"));
        assertTrue(thirdFlat.contains("b"));
        assertTrue(thirdFlat.contains("start3"));
        GraphStoreState bodySecondIteration = state(
                store,
                config.getSessionId(),
                config.getNs() + ":loop:1:body:2"
        );
        assertTrue(bodySecondIteration.getPendingNode().containsKey("a"));
        assertTrue(bodySecondIteration.getPendingNode().containsKey("b"));
        assertEquals("c", bodySecondIteration.getPendingBuffer().get(0).getSender());

        trace.clear();
        Map<String, Object> fourth = graph.run(config);
        assertFalse(fourth.containsKey(PregelConstants.TASK_STATUS_INTERRUPT));
        List<String> fourthFlat = trace.flat();
        assertTrue(fourthFlat.contains("end3"));
        assertTrue(fourthFlat.contains("end1"));
        assertTrue(fourthFlat.contains("end"));

        store.delete(config.getSessionId(), config.getNs()).toCompletableFuture().join();
        assertEmptyState(store, config.getSessionId(), config.getNs() + ":loop:1:body:2");
        assertEmptyState(store, config.getSessionId(), config.getNs() + ":loop:1:body:1");
        assertEmptyState(store, config.getSessionId(), config.getNs() + ":loop:1");
        assertEmptyState(store, config.getSessionId(), config.getNs());
    }

    private static GraphParts basicBarrierDirect() {
        BarrierChannel barrier = new BarrierChannel(
                "collect",
                List.of(Set.of("a1"), Set.of("b"), Set.of("c"), Set.of("d"))
        );
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", pass(), new StaticRouter(List.of("a", "b", "c", "d"))));
        nodes.put("a", node("a", pass(), new StaticRouter(List.of("a1"))));
        nodes.put("b", node("b", pass(), new BarrierRouter(List.of(barrier.getKey()))));
        nodes.put("c", node("c", pass(), new BarrierRouter(List.of(barrier.getKey()))));
        nodes.put("d", node("d", pass(), new BarrierRouter(List.of(barrier.getKey()))));
        nodes.put("a1", node("a1", ignored -> "slow_data", new BarrierRouter(List.of(barrier.getKey()))));
        nodes.put("collect", node("collect", pass(), new StaticRouter(List.of("end"))));
        nodes.put("end", node("end", pass()));
        return new GraphParts(
                nodes,
                List.of(
                        new TriggerChannel("start"),
                        new TriggerChannel("a"),
                        new TriggerChannel("b"),
                        new TriggerChannel("c"),
                        new TriggerChannel("d"),
                        new TriggerChannel("a1"),
                        new TriggerChannel("collect"),
                        new TriggerChannel("end"),
                        barrier
                )
        );
    }

    private static GraphParts basicBarrierBuilder() {
        PregelBuilder builder = new PregelBuilder()
                .addNode("start", pass())
                .addNode("a", pass())
                .addNode("b", pass())
                .addNode("c", pass())
                .addNode("d", pass())
                .addNode("a1", ignored -> "slow_data")
                .addNode("collect", pass())
                .addNode("end", pass())
                .addEdge("start", List.of("a", "b", "c", "d"))
                .addEdge("a", "a1")
                .addEdge(List.of("a1", "b", "c", "d"), "collect")
                .addEdge("collect", "end");
        return new GraphParts(builder.getNodes(), builder.getChannels());
    }

    private static GraphParts conditionalRoutingDirect() {
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("A", node("A", ignored -> 42, new ConditionalRouter(() -> "D")));
        nodes.put("D", node("D", ignored -> "received"));
        nodes.put("E", node("E", ignored -> "received"));
        return new GraphParts(nodes, List.of(new TriggerChannel("A"), new TriggerChannel("D"), new TriggerChannel("E")));
    }

    private static GraphParts conditionalRoutingBuilder() {
        PregelBuilder builder = new PregelBuilder()
                .addNode("A", ignored -> 42)
                .addNode("D", ignored -> "received")
                .addNode("E", ignored -> "received")
                .addBranch("A", () -> "D");
        return new GraphParts(builder.getNodes(), builder.getChannels());
    }

    private static GraphParts multiRoutingDirect() {
        BarrierChannel abcToD = new BarrierChannel("D", List.of(Set.of("A"), Set.of("B"), Set.of("C")));
        BarrierChannel ayToD = new BarrierChannel("D", List.of(Set.of("A"), Set.of("Y")));
        BarrierChannel degToEnd = new BarrierChannel("END", List.of(Set.of("D"), Set.of("E"), Set.of("G")));
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("START", node("START", ignored -> 1, new StaticRouter(List.of("A", "B", "C", "X"))));
        nodes.put("A", node("A", ignored -> 1,
                new StaticRouter(List.of("G")),
                new ConditionalRouter(() -> "E"),
                new BarrierRouter(List.of(abcToD.getKey(), ayToD.getKey()))));
        nodes.put("B", node("B", ignored -> 1, new BarrierRouter(List.of(abcToD.getKey()))));
        nodes.put("C", node("C", ignored -> 1, new BarrierRouter(List.of(abcToD.getKey()))));
        nodes.put("X", node("X", ignored -> 1,
                new StaticRouter(List.of("Y")),
                new BarrierRouter(List.of(ayToD.getKey()))));
        nodes.put("Y", node("Y", ignored -> 2, new StaticRouter(List.of("D"))));
        nodes.put("D", node("D", ignored -> 2, new BarrierRouter(List.of(degToEnd.getKey()))));
        nodes.put("E", node("E", ignored -> 2, new BarrierRouter(List.of(degToEnd.getKey()))));
        nodes.put("F", node("F", ignored -> 2, new StaticRouter(List.of("END"))));
        nodes.put("G", node("G", ignored -> 2, new BarrierRouter(List.of(degToEnd.getKey()))));
        nodes.put("END", node("END", pass()));
        return new GraphParts(
                nodes,
                List.of(
                        new TriggerChannel("START"),
                        new TriggerChannel("A"),
                        new TriggerChannel("B"),
                        new TriggerChannel("C"),
                        new TriggerChannel("X"),
                        new TriggerChannel("Y"),
                        new TriggerChannel("E"),
                        new TriggerChannel("F"),
                        new TriggerChannel("G"),
                        new TriggerChannel("D"),
                        new TriggerChannel("END"),
                        abcToD,
                        ayToD,
                        degToEnd
                )
        );
    }

    private static GraphParts multiRoutingBuilder() {
        PregelBuilder builder = new PregelBuilder()
                .addNode("START", ignored -> 1)
                .addNode("A", ignored -> 1)
                .addNode("B", ignored -> 1)
                .addNode("C", ignored -> 1)
                .addNode("X", ignored -> 1)
                .addNode("Y", ignored -> 2)
                .addNode("D", ignored -> 2)
                .addNode("E", ignored -> 2)
                .addNode("F", ignored -> 2)
                .addNode("G", ignored -> 2)
                .addNode("END", pass())
                .addEdge("START", List.of("A", "B", "C", "X"))
                .addEdge("A", "G")
                .addBranch("A", () -> "E")
                .addEdge(List.of("A", "B", "C"), "D")
                .addEdge(List.of("A", "Y"), "D")
                .addEdge("X", "Y")
                .addEdge("Y", "D")
                .addEdge(List.of("D", "E", "G"), "END")
                .addEdge("F", "END");
        return new GraphParts(builder.getNodes(), builder.getChannels());
    }

    private static Pregel exceptionSubgraph(InMemoryStore store, Consumer<PregelLoop> logger) {
        PregelBuilder builder = new PregelBuilder()
                .addNode("start1", pass())
                .addNode("a1", ignored -> {
                    throw new IllegalStateException("a1 exception");
                })
                .addNode("end1", pass())
                .addEdge("start1", "a1")
                .addEdge("a1", "end1");
        return new Pregel(builder.getNodes(), builder.getChannels(), "start1", store, logger);
    }

    private static Pregel linearGraph(InMemoryStore store, Consumer<PregelLoop> logger) {
        PregelBuilder builder = new PregelBuilder()
                .addNode("start", pass())
                .addNode("a", pass())
                .addNode("b", pass())
                .addNode("c", pass())
                .addNode("end", pass())
                .addEdge("start", "a")
                .addEdge("a", "b")
                .addEdge("b", "c")
                .addEdge("c", "end");
        return new Pregel(builder.getNodes(), builder.getChannels(), "start", store, logger);
    }

    private static Pregel interruptingParallelSubgraph(InMemoryStore store, Consumer<PregelLoop> logger) {
        PregelBuilder builder = new PregelBuilder()
                .addNode("start1", pass())
                .addNode("a1", interruptTwice("a1_Interrupt", 20))
                .addNode("a2", pass())
                .addNode("a3", pass())
                .addNode("end1", pass())
                .addEdge("start1", List.of("a1", "a2", "a3"))
                .addEdge(List.of("a1", "a2", "a3"), "end1");
        return new Pregel(builder.getNodes(), builder.getChannels(), "start1", store, logger);
    }

    private static Pregel bodySubgraph(InMemoryStore store, Consumer<PregelLoop> logger) {
        CyclicBarrier parallelBarrier = new CyclicBarrier(2);
        Function<Object, Object> a = interruptFirstTwoThenEvenCalls("a_interrupt", parallelBarrier);
        Function<Object, Object> b = interruptFirstTwoThenEvenCalls("b_interrupt", parallelBarrier);
        PregelBuilder builder = new PregelBuilder()
                .addNode("start3", pass())
                .addNode("a", a)
                .addNode("b", b)
                .addNode("c", ignored -> "c_done")
                .addNode("end3", pass())
                .addEdge("start3", List.of("a", "b", "c"))
                .addEdge(List.of("a", "b", "c"), "end3");
        return new Pregel(builder.getNodes(), builder.getChannels(), "start3", store, logger);
    }

    private static Pregel loopSubgraph(
            Pregel body,
            AtomicInteger conditionCount,
            InMemoryStore store,
            Consumer<PregelLoop> logger
    ) {
        PregelBuilder builder = new PregelBuilder()
                .addNode("start1", pass())
                .addNode("body", runSubgraph(body))
                .addNode("condition", pass())
                .addNode("end1", pass())
                .addEdge("start1", "body")
                .addEdge("body", "condition")
                .addBranch("condition", () -> conditionCount.incrementAndGet() < 2 ? "body" : "end1");
        return new Pregel(builder.getNodes(), builder.getChannels(), "start1", store, logger);
    }

    private static Pregel outerLoopGraph(Pregel loop, InMemoryStore store, Consumer<PregelLoop> logger) {
        PregelBuilder builder = new PregelBuilder()
                .addNode("start", pass())
                .addNode("loop", runSubgraph(loop))
                .addNode("end", pass())
                .addEdge("start", "loop")
                .addEdge("loop", "end");
        return new Pregel(builder.getNodes(), builder.getChannels(), "start", store, logger);
    }

    private static Map<String, PregelNode> subgraphOuterNodes(Function<Object, Object> subgraphRunner) {
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", pass(), new StaticRouter(List.of("a"))));
        nodes.put("a", node("a", subgraphRunner, new StaticRouter(List.of("end"))));
        nodes.put("end", node("end", pass()));
        return nodes;
    }

    private static List<Channel> subgraphOuterChannels() {
        return List.of(new TriggerChannel("start"), new TriggerChannel("a"), new TriggerChannel("end"));
    }

    private static Map<String, PregelNode> parallelOuterNodes(
            Function<Object, Object> subgraphRunner,
            Function<Object, Object> interruptingPeer
    ) {
        BarrierChannel barrier = new BarrierChannel("end", List.of(Set.of("a"), Set.of("b")));
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", pass(), new StaticRouter(List.of("a", "b"))));
        nodes.put("a", node("a", subgraphRunner, new BarrierRouter(List.of(barrier.getKey()))));
        nodes.put("b", node("b", interruptingPeer, new BarrierRouter(List.of(barrier.getKey()))));
        nodes.put("end", node("end", pass()));
        return nodes;
    }

    private static List<Channel> parallelOuterChannels() {
        BarrierChannel barrier = new BarrierChannel("end", List.of(Set.of("a"), Set.of("b")));
        return List.of(new TriggerChannel("start"), new TriggerChannel("a"), new TriggerChannel("b"),
                new TriggerChannel("end"), barrier);
    }

    @SafeVarargs
    private static PregelNode node(String name, Function<Object, Object> function, IRouter... routers) {
        return new PregelNode(name, function, List.of(routers));
    }

    private static Function<Object, Object> pass() {
        return ignored -> "pass";
    }

    private static Function<Object, Object> runSubgraph(Pregel graph) {
        return invocation -> {
            try {
                return graph.run(invocationConfig(invocation));
            } catch (GraphInterrupt interrupt) {
                throw new RuntimeException(interrupt);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static PregelConfig invocationConfig(Object invocation) {
        assertInstanceOf(Map.class, invocation);
        Object config = ((Map<String, Object>) invocation).get("config");
        assertInstanceOf(PregelConfig.class, config);
        return (PregelConfig) config;
    }

    private static Function<Object, Object> interruptTwice(String value, long delayMillis) {
        AtomicInteger count = new AtomicInteger();
        return ignored -> {
            int attempt = count.incrementAndGet();
            sleep(delayMillis);
            return attempt <= 2 ? new GraphInterrupt(value) : value + "_done";
        };
    }

    private static Function<Object, Object> interruptFirstTwoThenEvenCalls(String value, CyclicBarrier barrier) {
        AtomicInteger count = new AtomicInteger();
        return ignored -> {
            int attempt = count.incrementAndGet();
            awaitBarrier(barrier);
            sleep(20);
            if (attempt <= 2 || attempt % 2 == 0) {
                return new GraphInterrupt(value);
            }
            return value + "_done";
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (BrokenBarrierException broken) {
            throw new IllegalStateException(broken);
        }
    }

    private static GraphStoreState state(InMemoryStore store, String sessionId, String ns) {
        Optional<GraphStoreState> state = store.get(sessionId, ns).toCompletableFuture().join();
        assertTrue(state.isPresent(), () -> "Expected graph state for namespace " + ns);
        return state.orElseThrow();
    }

    private static void assertEmptyState(InMemoryStore store, String sessionId, String ns) {
        assertTrue(store.get(sessionId, ns).toCompletableFuture().join().isEmpty(),
                () -> "Expected deleted graph state for namespace " + ns);
    }

    private static void assertStep(Trace trace, int index, String... expected) {
        assertTrue(trace.steps().size() > index, () -> "Missing trace step " + index);
        assertEquals(Set.of(expected), Set.copyOf(trace.steps().get(index)));
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        assertNotNull(current);
        return current;
    }

    /**
     * Immutable graph parts for translated Pregel fixtures.
     *
     * <p>Mirrors Python's graph fixture tuples in
     * {@code tests/unit_tests/core/graph/test_pregel.py}.</p>
     *
     * @param nodes Java Pregel node map
     * @param channels Java Pregel channel list
     */
    private record GraphParts(Map<String, PregelNode> nodes, List<Channel> channels) {
    }

    /**
     * Super-step trace used by translated Pregel tests.
     *
     * <p>Mirrors Python's {@code execution_trace} lists in
     * {@code tests/unit_tests/core/graph/test_pregel.py}.</p>
     */
    private static final class Trace {
        private final List<List<String>> steps = new ArrayList<>();

        private void record(PregelLoop loop) {
            steps.add(loop.getActiveNodes());
        }

        private List<List<String>> steps() {
            return steps;
        }

        private List<String> flat() {
            return steps.stream().flatMap(List::stream).toList();
        }

        private void clear() {
            steps.clear();
        }
    }
}
