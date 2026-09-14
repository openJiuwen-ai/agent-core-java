/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.cli.integration;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.cli.agent.CliAgentFactory;
import com.openjiuwen.harness.cli.agent.LocalBackend;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>Mirrors Python's {@code tests/cli/integration/test_local_backend.py}.</p>
 */
class LocalBackendMissingTest {

    @Test
    void testStartInitializesAgent() {
        DeepAgent agent = mock(DeepAgent.class);
        TokenTrackingRail tracker = new TokenTrackingRail();
        CliAgentFactory.AgentBundle bundle = new CliAgentFactory.AgentBundle(agent, tracker);

        try (MockedStatic<CliAgentFactory> factory = Mockito.mockStatic(CliAgentFactory.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            factory.when(() -> CliAgentFactory.createAgent(Mockito.anyMap())).thenReturn(bundle);
            runner.when(Runner::start).thenReturn(CompletableFuture.completedFuture(true));

            LocalBackend backend = new LocalBackend(Map.of("api_key", "test"));
            backend.start().toCompletableFuture().join();

            assertThat(backend.getAgent()).isSameAs(agent);
            assertThat(backend.getTracker()).isSameAs(tracker);
            runner.verify(Runner::start);
        }
    }

    @Test
    void testRunStreamingYieldsChunks() throws ReflectiveOperationException {
        List<Object> chunks = List.of(
                new FakeChunk("llm_output", 0, Map.of("content", "hi")),
                new FakeChunk("answer", 1, Map.of("output", "done"))
        );
        DeepAgent agent = mock(DeepAgent.class);
        LocalBackend backend = new LocalBackend(Map.of("api_key", "test"));
        setAgent(backend, agent);

        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.runAgentStreaming(
                    Mockito.same(agent),
                    Mockito.eq(Map.of("query", "test")),
                    Mockito.any(),
                    Mockito.isNull(),
                    Mockito.isNull(),
                    Mockito.isNull()
            )).thenReturn(CompletableFuture.completedFuture(chunks.iterator()));

            Iterator<Object> resultIterator = backend.runStreaming("test", null).toCompletableFuture().join();
            List<Object> results = iteratorToList(resultIterator);

            assertThat(results).hasSize(2);
            assertThat(((FakeChunk) results.get(0)).type()).isEqualTo("llm_output");
            assertThat(((FakeChunk) results.get(1)).type()).isEqualTo("answer");
        }
    }

    @Test
    void testAbortCallsAgentAbort() throws ReflectiveOperationException {
        DeepAgent agent = mock(DeepAgent.class);
        when(agent.abort(Mockito.isNull())).thenReturn(CompletableFuture.completedFuture(true));
        LocalBackend backend = new LocalBackend(Map.of("api_key", "test"));
        setAgent(backend, agent);

        backend.abort().toCompletableFuture().join();

        verify(agent).abort(Mockito.isNull());
    }

    @Test
    void testAbortWithoutAgent() {
        LocalBackend backend = new LocalBackend(Map.of("api_key", "test"));

        backend.abort().toCompletableFuture().join();

        assertThat(backend.getAgent()).isNull();
    }

    private static List<Object> iteratorToList(Iterator<Object> iterator) {
        java.util.ArrayList<Object> results = new java.util.ArrayList<>();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }
        return results;
    }

    private static void setAgent(LocalBackend backend, DeepAgent agent) throws ReflectiveOperationException {
        Field field = LocalBackend.class.getDeclaredField("agent");
        field.setAccessible(true);
        field.set(backend, agent);
    }

    private record FakeChunk(String type, int index, Map<String, Object> payload) {
    }
}
