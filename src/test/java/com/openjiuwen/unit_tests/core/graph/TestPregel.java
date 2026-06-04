/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.graph;

import com.openjiuwen.core.graph.pregel.BarrierChannel;
import com.openjiuwen.core.graph.pregel.BarrierRouter;
import com.openjiuwen.core.graph.pregel.Channel;
import com.openjiuwen.core.graph.pregel.ConditionalRouter;
import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelBuilder;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.pregel.PregelLoop;
import com.openjiuwen.core.graph.pregel.PregelNode;
import com.openjiuwen.core.graph.pregel.StaticRouter;
import com.openjiuwen.core.graph.pregel.TriggerChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/core/graph/test_pregel.py}.
 *
 * <p>This compatibility suite replaces the old placeholder translation with
 * executable smoke tests for the key Python scenarios: barrier fan-in,
 * conditional routing, and mixed multi-routing.</p>
 */
class TestPregel {

    @Test
    @DisplayName("barrier wait for all")
    void testBarrierWaitForAll() throws Exception {
        List<List<String>> trace = new ArrayList<>();
        Pregel app = basicGraphDirect(trace);

        app.run(null);

        assertEquals(5, trace.size());
        assertEquals(List.of("start"), trace.get(0));
        assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(trace.get(1)));
        assertEquals(List.of("a1"), trace.get(2));
        assertEquals(List.of("collect"), trace.get(3));
        assertEquals(List.of("end"), trace.get(4));
    }

    @Test
    @DisplayName("conditional routing")
    void testConditionalRouting() throws Exception {
        Runnable fn = () -> { };
        List<Channel> channels = List.of(
                new TriggerChannel("A"),
                new TriggerChannel("D"),
                new TriggerChannel("E"));
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("A", new PregelNode("A", fn, List.of(new ConditionalRouter(ignored -> "D"))));
        nodes.put("D", new PregelNode("D", fn, List.of(new StaticRouter(List.of()))));
        nodes.put("E", new PregelNode("E", fn, List.of(new StaticRouter(List.of()))));

        List<List<String>> trace = new ArrayList<>();
        new Pregel(nodes, channels, "A", null, traceCallback(trace)).run(null);

        assertEquals(2, trace.size());
        assertEquals(List.of("A"), trace.get(0));
        assertEquals(List.of("D"), trace.get(1));
        assertFalse(trace.stream().flatMap(List::stream).toList().contains("E"));
    }

    @Test
    @DisplayName("multi routing")
    void testMultiRouting() throws Exception {
        List<List<String>> trace = new ArrayList<>();

        multiRoutingDirect(trace).run(null);

        assertEquals(4, trace.size());
        assertEquals(List.of("START"), trace.get(0));
        assertEquals(Set.of("A", "B", "C", "X"), Set.copyOf(trace.get(1)));
        assertEquals(Set.of("Y", "E", "G", "D"), Set.copyOf(trace.get(2)));
        assertEquals(Set.of("END", "D"), Set.copyOf(trace.get(3)));
    }

    @Test
    @DisplayName("builder barrier graph mirrors direct execution")
    void testBarrierWaitForAllBuilder() throws Exception {
        PregelBuilder builder = new PregelBuilder();
        Runnable fnPass = () -> { };
        builder.addNode("a", fnPass);
        builder.addNode("b", fnPass);
        builder.addNode("c", fnPass);
        builder.addNode("d", fnPass);
        builder.addNode("a1", fnPass);
        builder.addNode("collect", fnPass);
        builder.addNode("finish", fnPass);

        builder.addEdge(PregelConstants.START, List.of("a", "b", "c", "d"));
        builder.addEdge("a", "a1");
        builder.addEdge(List.of("a1", "b", "c", "d"), "collect");
        builder.addEdge("collect", "finish");

        List<List<String>> trace = new ArrayList<>();
        builder.build(null, traceCallback(trace)).run(null);

        assertEquals(5, trace.size());
        assertEquals(List.of(PregelConstants.START), trace.get(0));
        assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(trace.get(1)));
        assertEquals(List.of("a1"), trace.get(2));
        assertEquals(List.of("collect"), trace.get(3));
        assertEquals(List.of("finish"), trace.get(4));
    }

    @Test
    @DisplayName("builder multi routing keeps end barrier behavior")
    void testMultiRoutingBuilder() throws Exception {
        Runnable fn = () -> { };
        PregelBuilder builder = new PregelBuilder();
        builder.addNode("A", fn);
        builder.addNode("B", fn);
        builder.addNode("C", fn);
        builder.addNode("X", fn);
        builder.addNode("Y", fn);
        builder.addNode("D", fn);
        builder.addNode("E", fn);
        builder.addNode("F", fn);
        builder.addNode("G", fn);
        builder.addNode("END", fn);

        builder.addEdge(PregelConstants.START, List.of("A", "B", "C", "X"));
        builder.addEdge("A", "G");
        builder.addBranch("A", ignored -> "E");
        builder.addEdge(List.of("A", "B", "C"), "D");
        builder.addEdge("X", "Y");
        builder.addEdge(List.of("A", "Y"), "D");
        builder.addEdge("Y", "D");
        builder.addEdge(List.of("D", "E", "G"), "END");
        builder.addEdge("F", "END");

        List<List<String>> trace = new ArrayList<>();
        builder.build(null, traceCallback(trace)).run(null);

        assertTrue(trace.size() >= 4);
        assertEquals(List.of(PregelConstants.START), trace.get(0));
        assertEquals(Set.of("A", "B", "C", "X"), Set.copyOf(trace.get(1)));
        assertEquals(Set.of("Y", "E", "G", "D"), Set.copyOf(trace.get(2)));
        assertEquals(Set.of("END", "D"), Set.copyOf(trace.get(3)));
    }

    private Pregel basicGraphDirect(List<List<String>> trace) {
        Runnable fn = () -> { };
        BarrierChannel a1bcdToCollect = new BarrierChannel("collect", Set.of("a1", "b", "c", "d"));
        List<Channel> channels = List.of(
                new TriggerChannel("start"),
                new TriggerChannel("a"),
                new TriggerChannel("b"),
                new TriggerChannel("c"),
                new TriggerChannel("d"),
                new TriggerChannel("a1"),
                new TriggerChannel("collect"),
                new TriggerChannel("end"),
                a1bcdToCollect);
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("start", new PregelNode("start", fn, List.of(new StaticRouter(List.of("a", "b", "c", "d")))));
        nodes.put("a", new PregelNode("a", fn, List.of(new StaticRouter(List.of("a1")))));
        nodes.put("b", new PregelNode("b", fn, List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey())))));
        nodes.put("c", new PregelNode("c", fn, List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey())))));
        nodes.put("d", new PregelNode("d", fn, List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey())))));
        nodes.put("a1", new PregelNode("a1", fn, List.of(new BarrierRouter(List.of(a1bcdToCollect.getKey())))));
        nodes.put("collect", new PregelNode("collect", fn, List.of(new StaticRouter(List.of("end")))));
        nodes.put("end", new PregelNode("end", fn, List.of(new StaticRouter(List.of()))));
        return new Pregel(nodes, channels, "start", null, traceCallback(trace));
    }

    private Pregel multiRoutingDirect(List<List<String>> trace) {
        Runnable fn = () -> { };
        BarrierChannel abcToD = new BarrierChannel("D", Set.of("A", "B", "C"));
        BarrierChannel ayToD = new BarrierChannel("D", Set.of("A", "Y"));
        BarrierChannel degToEnd = new BarrierChannel("END", Set.of("D", "E", "G"));
        List<Channel> channels = List.of(
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
                new TriggerChannel("END"),
                abcToD,
                ayToD,
                degToEnd);
        Map<String, PregelNode> nodes = new LinkedHashMap<>();
        nodes.put("START", new PregelNode("START", fn, List.of(new StaticRouter(List.of("A", "B", "C", "X")))));
        nodes.put("A", new PregelNode("A", fn, List.of(
                new StaticRouter(List.of("G")),
                new ConditionalRouter(ignored -> "E"),
                new BarrierRouter(List.of(abcToD.getKey(), ayToD.getKey())))));
        nodes.put("B", new PregelNode("B", fn, List.of(new BarrierRouter(List.of(abcToD.getKey())))));
        nodes.put("C", new PregelNode("C", fn, List.of(new BarrierRouter(List.of(abcToD.getKey())))));
        nodes.put("X", new PregelNode("X", fn, List.of(new StaticRouter(List.of("Y")))));
        nodes.put("Y", new PregelNode("Y", fn, List.of(
                new StaticRouter(List.of("D")),
                new BarrierRouter(List.of(ayToD.getKey())))));
        nodes.put("D", new PregelNode("D", fn, List.of(new BarrierRouter(List.of(degToEnd.getKey())))));
        nodes.put("E", new PregelNode("E", fn, List.of(new BarrierRouter(List.of(degToEnd.getKey())))));
        nodes.put("F", new PregelNode("F", fn, List.of(new StaticRouter(List.of("END")))));
        nodes.put("G", new PregelNode("G", fn, List.of(new BarrierRouter(List.of(degToEnd.getKey())))));
        nodes.put("END", new PregelNode("END", fn, List.of(new StaticRouter(List.of()))));
        return new Pregel(nodes, channels, "START", null, traceCallback(trace));
    }

    private static Consumer<PregelLoop> traceCallback(List<List<String>> trace) {
        return loop -> trace.add(new ArrayList<>(loop.getActiveNodes()));
    }
}
