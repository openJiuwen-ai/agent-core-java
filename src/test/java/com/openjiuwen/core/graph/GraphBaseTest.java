/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's base graph behavior in
 * {@code openjiuwen/core/graph/base.py}.
 */
class GraphBaseTest {

    @Test
    @DisplayName("executable graph invoke extracts inputs and config")
    void testExecutableGraphInvokeExtractsInputsAndConfig() {
        RecordingGraph graph = new RecordingGraph();
        BaseSession session = new TestSession();
        Map<String, Object> envelope = Map.of(
                Constant.INPUTS_KEY, "payload",
                Constant.CONFIG_KEY, Map.of("recursion_limit", 10)
        );

        String result = graph.invoke(envelope, session);

        assertEquals("payload", graph.lastInputs);
        assertSame(session, graph.lastSession);
        assertEquals(Map.of("recursion_limit", 10), graph.lastConfig);
        assertEquals("payload::10", result);
    }

    @Test
    @DisplayName("executable graph invoke passes null config when missing")
    void testExecutableGraphInvokePassesNullConfigWhenMissing() {
        RecordingGraph graph = new RecordingGraph();

        String result = graph.invoke(Map.of(Constant.INPUTS_KEY, "payload"), new TestSession());

        assertEquals("payload", graph.lastInputs);
        assertNull(graph.lastConfig);
        assertEquals("payload::null", result);
    }

    @Test
    @DisplayName("executable graph pass bodies return null")
    void testExecutableGraphPassBodiesReturnNull() {
        RecordingGraph graph = new RecordingGraph();

        assertNull(graph.stream(Map.of(), new TestSession()));
        assertNull(graph.collect(Collections.emptyIterator(), new TestSession()));
        assertNull(graph.transform(Collections.emptyIterator(), new TestSession()));
        graph.interrupt(Map.of("message", "stop"));
    }

    @Test
    @DisplayName("graph base methods return null like Python pass")
    void testGraphBaseMethodsReturnNullLikePythonPass() {
        Graph graph = new Graph();
        Executable<String, String> executable = new TestExecutable();

        assertNull(graph.startNode("start"));
        assertNull(graph.endNode("end"));
        assertNull(graph.addNode("node", executable));
        assertNull(graph.addNode("node", executable, true));
        assertNull(graph.addEdge(List.of("a", "b"), "c"));
        assertNull(graph.addConditionalEdges("router", (Router) args -> "next"));
        assertNull(graph.compile(new TestSession()));
        assertNull(graph.compile(new TestSession(), Map.of("context", "ctx")));
        assertNull(graph.getNodes());
    }

    @Test
    @DisplayName("router supports scalar and collection route values")
    void testRouterSupportsScalarAndCollectionRouteValues() {
        Router scalarRouter = args -> args[0];
        Router listRouter = args -> List.of(args);

        assertEquals("target", scalarRouter.route("target"));
        assertEquals(List.of("a", "b"), listRouter.route("a", "b"));
    }

    @Test
    @DisplayName("executable default methods preserve Python error text")
    void testExecutableDefaultMethodsPreservePythonErrorText() {
        TestExecutable executable = new TestExecutable();

        UnsupportedOperationException error = assertThrows(
                UnsupportedOperationException.class,
                () -> executable.onInvoke("input", null)
        );
        assertTrue(error.getMessage().contains("does not implement the on_invoke method"));
        assertTrue(error.getMessage().contains("Required implementation: async def on_invoke"));
    }

    private static final class RecordingGraph extends ExecutableGraph<String, String> {
        private String lastInputs;
        private BaseSession lastSession;
        private Object lastConfig;

        @Override
        protected String invokeInternal(String inputs, BaseSession session, Object config) {
            this.lastInputs = inputs;
            this.lastSession = session;
            this.lastConfig = config;
            Object limit = config instanceof Map<?, ?> map ? map.get("recursion_limit") : null;
            return inputs + "::" + limit;
        }
    }

    private static final class TestExecutable extends Executable<String, String> {
    }

    private static final class TestSession extends BaseSession {
    }
}
