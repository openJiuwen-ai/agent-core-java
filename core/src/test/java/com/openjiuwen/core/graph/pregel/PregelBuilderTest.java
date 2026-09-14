/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link PregelBuilder}.
 *
 * <p>Mirrors Python's {@code PregelBuilder} in
 * {@code openjiuwen/core/graph/pregel/builder.py}.</p>
 */
class PregelBuilderTest {

    @Test
    void builderAddsStaticEdgesAndBuildsPregel() throws Exception {
        List<String> calls = new ArrayList<>();
        Pregel graph = new PregelBuilder()
                .addNode("worker", ignored -> {
                    calls.add("worker");
                    return Boolean.TRUE;
                })
                .addEdge(PregelConstants.START, "worker")
                .build();

        graph.run(new PregelConfig("session-1", "graph-1", 5));

        assertEquals(List.of("worker"), calls);
    }

    @Test
    void builderSupportsOneToManyStaticEdges() throws Exception {
        AtomicInteger count = new AtomicInteger();
        Pregel graph = new PregelBuilder()
                .addNode("left", ignored -> {
                    count.incrementAndGet();
                    return Boolean.TRUE;
                })
                .addNode("right", ignored -> {
                    count.incrementAndGet();
                    return Boolean.TRUE;
                })
                .addEdge(PregelConstants.START, List.of("left", "right"))
                .build();

        graph.run(new PregelConfig("session-1", "graph-1", 5));

        assertEquals(2, count.get());
    }

    @Test
    void builderSupportsBarrierEdges() throws Exception {
        AtomicInteger collected = new AtomicInteger();
        Pregel graph = new PregelBuilder()
                .addNode("a", ignored -> Boolean.TRUE)
                .addNode("b", ignored -> Boolean.TRUE)
                .addNode("collect", ignored -> {
                    collected.incrementAndGet();
                    return Boolean.TRUE;
                })
                .addEdge(PregelConstants.START, List.of("a", "b"))
                .addEdge(List.of("a", "b"), "collect")
                .build();

        graph.run(new PregelConfig("session-1", "graph-1", 5));

        assertEquals(1, collected.get());
    }

    @Test
    void builderSupportsCnfBarrierGroups() throws Exception {
        AtomicInteger collected = new AtomicInteger();
        Pregel graph = new PregelBuilder()
                .addNode("a", ignored -> Boolean.TRUE)
                .addNode("b", ignored -> Boolean.TRUE)
                .addNode("c", ignored -> Boolean.TRUE)
                .addNode("collect", ignored -> {
                    collected.incrementAndGet();
                    return Boolean.TRUE;
                })
                .addEdge(PregelConstants.START, List.of("a", "c"))
                .addEdge(List.of(Set.of("a", "b"), "c"), "collect")
                .build();

        graph.run(new PregelConfig("session-1", "graph-1", 5));

        assertEquals(1, collected.get());
    }

    @Test
    void builderSupportsConditionalBranches() throws Exception {
        List<String> calls = new ArrayList<>();
        Pregel graph = new PregelBuilder()
                .addNode("branch", ignored -> Boolean.TRUE)
                .addNode("done", ignored -> {
                    calls.add("done");
                    return Boolean.TRUE;
                })
                .addEdge(PregelConstants.START, "branch")
                .addBranch("branch", () -> "done")
                .build();

        graph.run(new PregelConfig("session-1", "graph-1", 5));

        assertEquals(List.of("done"), calls);
    }

    @Test
    void builderSeedsStartAndEndNodes() {
        PregelBuilder builder = new PregelBuilder();

        assertTrue(builder.getNodes().containsKey(PregelConstants.START));
        assertTrue(builder.getNodes().containsKey(PregelConstants.END));
    }
}
