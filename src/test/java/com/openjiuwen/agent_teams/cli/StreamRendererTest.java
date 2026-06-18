/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.harness.cli.ui.CliRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code stream_renderer} module in
 * {@code openjiuwen/agent_teams/cli/stream_renderer.py}.
 */
class StreamRendererTest {

    @Test
    void wrapStreamInterceptsRuntimeReadyAndSuppressesVisibleChunk() {
        StreamHandle handle = new StreamHandle("team-a", "session-a", new CompletableFuture<>(),
                CompletableFuture.completedFuture(null));
        AtomicInteger callbackCount = new AtomicInteger();
        List<String> callbackArgs = new ArrayList<>();
        Map<String, Object> readyPayload = Map.of(
                "event_type", "team.runtime_ready",
                "team_name", "team-a",
                "session_id", "session-a"
        );

        Iterator<Object> wrapped = StreamRenderer.wrapStream(
                chunks(
                        chunk("message", readyPayload),
                        chunk("message", Map.of("content", "visible"))
                ),
                handle,
                (teamName, sessionId, payload) -> {
                    callbackCount.incrementAndGet();
                    callbackArgs.add(teamName + ":" + sessionId + ":" + payload.get("event_type"));
                    return CompletableFuture.completedFuture(null);
                },
                printStream(new ByteArrayOutputStream()),
                true
        );

        assertTrue(wrapped.hasNext());
        Object visible = wrapped.next();

        assertEquals(readyPayload, handle.getRuntimeReady().join());
        assertEquals(1, callbackCount.get());
        assertEquals(List.of("team-a:session-a:team.runtime_ready"), callbackArgs);
        assertEquals("message", ((Map<?, ?>) visible).get("type"));
        assertEquals(Map.of("content", "visible"), ((Map<?, ?>) visible).get("payload"));
        assertFalse(wrapped.hasNext());
    }

    @Test
    void wrapStreamRendersReasoningWithBoundaryAndReset() {
        StreamHandle handle = new StreamHandle("team-a", "session-a", new CompletableFuture<>(),
                CompletableFuture.completedFuture(null));
        ByteArrayOutputStream terminal = new ByteArrayOutputStream();
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        CliRenderer renderer = new CliRenderer();

        Iterator<Object> wrapped = StreamRenderer.wrapStream(
                chunks(
                        chunk("llm_output", Map.of("content", "Hello")),
                        chunk("llm_reasoning", Map.of("content", "think")),
                        chunk("llm_reasoning", Map.of("content", " more")),
                        chunk("llm_output", Map.of("content", "done"))
                ),
                handle,
                null,
                printStream(terminal),
                true
        );

        renderer.renderStream(wrapped, printStream(terminal), printStream(console), null, false);

        String output = terminal.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Hello" + System.lineSeparator() + StreamRenderer.REASONING_PREFIX + "think more"
                + StreamRenderer.REASONING_RESET));
        assertTrue(output.contains("done"));
    }

    @Test
    void wrapStreamHidesReasoningWhenDisabled() {
        StreamHandle handle = new StreamHandle("team-a", "session-a", new CompletableFuture<>(),
                CompletableFuture.completedFuture(null));
        ByteArrayOutputStream terminal = new ByteArrayOutputStream();
        CliRenderer renderer = new CliRenderer();

        Iterator<Object> wrapped = StreamRenderer.wrapStream(
                chunks(
                        chunk("llm_reasoning", Map.of("content", "hidden")),
                        chunk("llm_output", Map.of("content", "visible"))
                ),
                handle,
                null,
                printStream(terminal),
                false
        );

        renderer.renderStream(wrapped, printStream(terminal), printStream(new ByteArrayOutputStream()), null, false);

        String output = terminal.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("hidden"));
        assertTrue(output.contains("visible"));
    }

    @Test
    void spawnStreamRunsSourceThroughRendererAndCompletesReadyFuture() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("team-a");
        ByteArrayOutputStream terminal = new ByteArrayOutputStream();
        ByteArrayOutputStream console = new ByteArrayOutputStream();

        StreamRenderer.StreamSource source = (teamSpec, inputs, sessionId) ->
                CompletableFuture.completedFuture(chunks(
                        chunk("message", Map.of(
                                "event_type", "team.runtime_ready",
                                "team_name", teamSpec.getTeamName(),
                                "session_id", sessionId
                        )),
                        chunk("llm_output", Map.of("content", inputs.get("query")))
                ));

        StreamHandle handle = StreamRenderer.spawnStream(
                spec,
                "session-a",
                Map.of("query", "hello"),
                printStream(terminal),
                printStream(console),
                null,
                true,
                source,
                new CliRenderer(),
                Runnable::run
        );

        handle.getTask().join();

        assertEquals("team-a", handle.getRuntimeReady().join().get("team_name"));
        assertTrue(console.toString(StandardCharsets.UTF_8).contains("[team-a] stream started"));
        assertTrue(terminal.toString(StandardCharsets.UTF_8).contains("hello"));
    }

    @Test
    void stopStreamCancelsUnfinishedTask() {
        CompletableFuture<Void> task = new CompletableFuture<>();
        StreamHandle handle = new StreamHandle("team-a", "session-a", new CompletableFuture<>(), task);

        StreamRenderer.stopStream(handle).toCompletableFuture().join();

        assertTrue(handle.isCancelled());
        assertTrue(task.isCancelled());
    }

    @SafeVarargs
    private static Iterator<Object> chunks(Map<String, Object>... chunks) {
        List<Object> result = new ArrayList<>();
        for (Map<String, Object> chunk : chunks) {
            result.add(chunk);
        }
        return result.iterator();
    }

    private static Map<String, Object> chunk(String type, Map<String, Object> payload) {
        return Map.of("type", type, "payload", payload);
    }

    private static PrintStream printStream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }
}
