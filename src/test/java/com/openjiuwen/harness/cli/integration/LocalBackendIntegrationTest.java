/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.integration;

import com.openjiuwen.harness.cli.agent.CliAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-02: LocalBackend integration tests.
 * <p>
 * Mirrors Python's {@code test_local_backend} in
 * {@code tests.cli.integration.test_local_backend}.
 */
class LocalBackendIntegrationTest {

    @Test
    void startInitializesAgent() {
        Object agent = new Object();
        Object tracker = new Object();
        AtomicInteger startCalls = new AtomicInteger();
        CliAgentFactory.LocalBackend backend = backend(
                new CliAgentFactory.AgentAndTracker(agent, tracker),
                startCalls::incrementAndGet,
                () -> {
                },
                (a, inputs, session) -> List.of().iterator());

        backend.start();

        assertSame(agent, backend.getAgent());
        assertSame(tracker, backend.getTracker());
        assertEquals(1, startCalls.get());
    }

    @Test
    void runStreamingYieldsChunks() {
        List<Object> chunks = List.of(
                new FakeChunk("llm_output", 0, Map.of("content", "hi")),
                new FakeChunk("answer", 1, Map.of("output", "done")));
        AtomicReference<Object> inputsSeen = new AtomicReference<>();
        CliAgentFactory.LocalBackend backend = backend(
                new CliAgentFactory.AgentAndTracker(new Object(), new Object()),
                () -> {
                },
                () -> {
                },
                (agent, inputs, session) -> {
                    inputsSeen.set(inputs);
                    return chunks.iterator();
                });
        backend.start();

        List<Object> results = new ArrayList<>();
        backend.runStreaming("test").forEachRemaining(results::add);

        assertEquals(2, results.size());
        assertEquals("llm_output", ((FakeChunk) results.get(0)).type());
        assertEquals("answer", ((FakeChunk) results.get(1)).type());
        assertEquals("test", ((Map<?, ?>) inputsSeen.get()).get("query"));
    }

    @Test
    void abortCallsAgentAbort() {
        FakeAbortableAgent agent = new FakeAbortableAgent();
        CliAgentFactory.LocalBackend backend = backend(
                new CliAgentFactory.AgentAndTracker(agent, new Object()),
                () -> {
                },
                () -> {
                },
                (a, inputs, session) -> List.of().iterator());
        backend.start();

        backend.abort();

        assertEquals(1, agent.abortCalls);
    }

    @Test
    void abortWithoutAgent() {
        CliAgentFactory.LocalBackend backend = backend(
                new CliAgentFactory.AgentAndTracker(new Object(), new Object()),
                () -> {
                },
                () -> {
                },
                (a, inputs, session) -> List.of().iterator());

        assertDoesNotThrow(backend::abort);
    }

    private static CliAgentFactory.LocalBackend backend(
            CliAgentFactory.AgentAndTracker agentAndTracker,
            Runnable runnerStart,
            Runnable runnerStop,
            CliAgentFactory.LocalBackend.StreamingRunner streamingRunner) {
        return new CliAgentFactory.LocalBackend(
                new LinkedHashMap<>(),
                ignored -> agentAndTracker,
                runnerStart,
                runnerStop,
                streamingRunner);
    }

    record FakeChunk(String type, int index, Map<String, Object> payload) {
    }

    public static class FakeAbortableAgent {
        int abortCalls;

        public CompletableFuture<Void> abort() {
            abortCalls += 1;
            return CompletableFuture.completedFuture(null);
        }
    }
}
